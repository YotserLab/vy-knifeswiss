package com.example.transport
import com.example.ui.screens.files.shareFile

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.R
import com.example.Res

object ObdExportUtils {

    fun exportSessionToCsv(context: Context, session: ObdTripSession, logs: List<ObdLogEntryEntity>) {
        runCatching {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val dateStr = sdf.format(Date(session.startTimeMs))
            val filename = "OBD_Log_${session.deviceName.replace(" ", "_")}_$dateStr.csv"
            
            val file = File(context.cacheDir, filename)
            FileWriter(file).use { writer ->
                // Session Header
                writer.appendLine(Res.get(R.string.sessao_veiculo_x, (session.deviceName)))
                writer.appendLine(Res.get(R.string.sessao_inicio_x, (Date(session.startTimeMs))))
                writer.appendLine(Res.get(R.string.sessao_duracaoms_x_session_starttimems, (session.endTimeMs - session.startTimeMs)))
                writer.appendLine(Res.get(R.string.sessao_maxrpm_x, (session.maxRpm)))
                writer.appendLine(Res.get(R.string.sessao_maxvelocidade_x, (session.maxSpeedKmh)))
                writer.appendLine(Res.get(R.string.sessao_distanciakm_x, (session.distanceKm)))
                writer.appendLine(Res.get(R.string.sessao_combustivell_x, (session.fuelConsumedL)))
                writer.appendLine("")
                
                // Logs Header
                writer.appendLine(Res.get(R.string.timestampms_rpm_speed_kmh_engineload_pct))
                
                // Logs
                for (log in logs) {
                    writer.appendLine("${log.timestampMs},${log.rpm},${log.speedKmh},${log.engineLoadPct},${log.coolantTempC},${log.voltage}")
                }
            }
            
            shareFile(context, file, "text/csv", Res.get(R.string.exportar_csv_da_viagem))
        }
    }

    fun exportDiagnosticsToPdf(
        context: Context,
        dtcs: List<ObdDtcCode>,
        freezeFrames: List<FreezeFrame>,
        readiness: ReadinessMonitors
    ) {
        runCatching {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size at 72 PPI
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 14f
            }
            val titlePaint = Paint().apply {
                color = Color.BLACK
                textSize = 20f
                isFakeBoldText = true
            }

            var y = 50f
            val x = 40f

            canvas.drawText(Res.get(R.string.relatorio_de_diagnostico_obd_ii), x, y, titlePaint)
            y += 40f
            
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            canvas.drawText(Res.get(R.string.data_x, (sdf.format(Date()))), x, y, paint)
            y += 40f

            // Readiness
            canvas.drawText(Res.get(R.string.monitores_de_prontidao_i_m_readiness), x, y, titlePaint)
            y += 25f
            canvas.drawText(Res.get(R.string.prontos_x_x, (readiness.readyCount), (readiness.totalMonitors)), x, y, paint)
            y += 40f

            // DTCs
            canvas.drawText(Res.get(R.string.codigos_de_falha_dtcs_x_encontrados, (dtcs.size)), x, y, titlePaint)
            y += 25f
            if (dtcs.isEmpty()) {
                canvas.drawText(Res.get(R.string.nenhuma_falha_detectada), x, y, paint)
                y += 30f
            } else {
                for (dtc in dtcs) {
                    canvas.drawText(Res.get(R.string.x_x_7, (dtc.code), (dtc.description)), x, y, paint)
                    y += 20f
                    if (dtc.isPending) {
                        canvas.drawText(Res.get(R.string.status_pendente), x, y, paint)
                        y += 20f
                    }
                    if (y > 780f) break // Simple pagination prevention for demo
                }
            }
            y += 20f

            // Freeze Frames
            canvas.drawText(Res.get(R.string.freeze_frames_retratos_da_falha), x, y, titlePaint)
            y += 25f
            if (freezeFrames.isEmpty()) {
                canvas.drawText(Res.get(R.string.nenhum_freeze_frame_registrado), x, y, paint)
            } else {
                for (ff in freezeFrames) {
                    canvas.drawText(Res.get(R.string.dtc_gatilho_x, (ff.triggerDtc)), x, y, paint)
                    y += 20f
                    canvas.drawText("  RPM: ${ff.rpm ?: "N/D"} | Vel: ${ff.speedKmh ?: Res.get(R.string.n_d)} km/h | Temp: ${ff.coolantTempC ?: Res.get(R.string.n_d)} C", x, y, paint)
                    y += 20f
                    if (y > 780f) break
                }
            }

            document.finishPage(page)

            val filename = "OBD_Diagnostico_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.pdf"
            val file = File(context.cacheDir, filename)
            file.outputStream().use { document.writeTo(it) }
            document.close()

            shareFile(context, file, "application/pdf", Res.get(R.string.exportar_relatorio_pdf))
        }
    }

    private fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
