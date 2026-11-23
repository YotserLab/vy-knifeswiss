package com.example.ui.screens.files

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.Vector
import com.example.utils.net.SshSessionHelper
import com.example.R
import com.example.Res


sealed class RemoteResult<out T> {
    data class Success<T>(val data: T) : RemoteResult<T>()
    data class Error(val message: String) : RemoteResult<Nothing>()
}

class RemoteFileClient(private val conn: NetworkConnection) {

    private var sftpSession: Session? = null
    private var sftpChannel: ChannelSftp? = null

    suspend fun testConnection(): RemoteResult<String> = withContext(Dispatchers.IO) {
        try {
            when (conn.protocol) {
                "SFTP" -> testSftp()
                "FTP", "FTPS" -> testFtp()
                else -> RemoteResult.Error(Res.get(R.string.protocolo_x_nao_suportado_ainda, (conn.protocol)))
            }
        } catch (e: Exception) {
            RemoteResult.Error(e.message ?: Res.get(R.string.erro_desconhecido))
        }
    }

    private fun testSftp(): RemoteResult<String> {
        val jsch = JSch()
        val session = jsch.getSession(conn.user, conn.host, conn.port)
        session.setPassword(conn.password)
        SshSessionHelper.applyConfig(session)
        session.connect(10000)
        val channel = session.openChannel("sftp") as ChannelSftp // ARQ-R15
        channel.connect(5000)
        channel.disconnect()
        session.disconnect()
        return RemoteResult.Success(Res.get(R.string.sftp_conectado_a_x_x, (conn.host), (conn.port)))
    }

    private fun testFtp(): RemoteResult<String> {
        val sock = Socket(conn.host, conn.port)
        sock.soTimeout = 5000
        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
        val response = reader.readLine()
        sock.close()
        return if (response != null && response.startsWith("220")) {
            RemoteResult.Success(Res.get(R.string.ftp_conectado_a_x_x, (conn.host), (conn.port)))
        } else {
            RemoteResult.Error(Res.get(R.string.resposta_ftp_inesperada_x, response))
        }
    }

    suspend fun listDirectory(path: String): RemoteResult<List<FileItem>> = withContext(Dispatchers.IO) {
        try {
            when (conn.protocol) {
                "SFTP" -> listSftp(path)
                "FTP", "FTPS" -> listFtp(path)
                else -> RemoteResult.Error(Res.get(R.string.protocolo_x_nao_suportado_ainda, (conn.protocol)))
            }
        } catch (e: Exception) {
            RemoteResult.Error(e.message ?: Res.get(R.string.erro_ao_listar))
        }
    }

    private fun listSftp(path: String): RemoteResult<List<FileItem>> {
        val jsch = JSch()
        val session = jsch.getSession(conn.user, conn.host, conn.port)
        session.setPassword(conn.password)
        SshSessionHelper.applyConfig(session)
        session.connect(10000)
        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect(5000)
        val entries = mutableListOf<FileItem>()
        val files = channel.ls(path)
        @Suppress("UNCHECKED_CAST")
        val entriesList = files as Vector<ChannelSftp.LsEntry>
        for (entry in entriesList) {
            val name = entry.filename
            if (name == "." || name == "..") continue
            val attrs = entry.attrs
            entries.add(
                FileItem(
                    name = name,
                    path = if (path.endsWith("/")) "$path$name" else "$path/$name",
                    size = formatSize(attrs.size),
                    type = getFileTypeFromName(name, attrs.isDir),
                    isLocal = false,
                    lastModified = attrs.mTime * 1000L,
                    rawSize = attrs.size
                )
            )
        }
        channel.disconnect()
        session.disconnect()
        return RemoteResult.Success(entries.sortedWith(compareByDescending<FileItem> { it.type == FileType.DIRECTORY }.thenBy { it.name }))
    }

    private fun listFtp(path: String): RemoteResult<List<FileItem>> {
        val sock = Socket(conn.host, conn.port)
        sock.soTimeout = 10000
        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
        val writer = sock.getOutputStream()

        fun readResponse(): String {
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.appendLine(line)
                if (line!!.length >= 4 && line!![3] == ' ') break
            }
            return sb.toString()
        }

        readResponse()
        writer.write("USER ${conn.user}\r\n".toByteArray()); writer.flush(); readResponse()
        writer.write("PASS ${conn.password}\r\n".toByteArray()); writer.flush(); readResponse()
        writer.write("TYPE I\r\n".toByteArray()); writer.flush(); readResponse()

        val pasvResp = run {
            writer.write("PASV\r\n".toByteArray()); writer.flush()
            readResponse()
        }
        val pasvMatch = Regex("""(\d+),(\d+),(\d+),(\d+),(\d+),(\d+)""").find(pasvResp)
        val dataHost: String
        val dataPort: Int
        if (pasvMatch != null) {
            val groups = pasvMatch.groupValues.drop(1).map { it.toInt() }
            dataHost = groups.take(4).joinToString(".")
            dataPort = groups[4] * 256 + groups[5]
        } else {
            writer.write("QUIT\r\n".toByteArray())
            sock.close()
            return RemoteResult.Error(Res.get(R.string.falha_no_modo_passivo))
        }

        writer.write("LIST $path\r\n".toByteArray()); writer.flush()

        val dataSock = Socket(dataHost, dataPort)
        dataSock.soTimeout = 10000
        val dataReader = BufferedReader(InputStreamReader(dataSock.getInputStream()))
        val listing = dataReader.readText()
        dataSock.close()

        readResponse()
        writer.write("QUIT\r\n".toByteArray())
        sock.close()

        val entries = mutableListOf<FileItem>()
        val lines = listing.lines().filter { it.isNotBlank() }
        for (line in lines) {
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 9) continue
            val name = parts.drop(8).joinToString(" ")
            if (name == "." || name == "..") continue
            val isDir = parts[0].startsWith("d")
            val sizeStr = parts[4]
            val rawSize = sizeStr.toLongOrNull() ?: 0L
            entries.add(
                FileItem(
                    name = name,
                    path = "${path}/${name}",
                    size = formatSize(rawSize),
                    type = getFileTypeFromName(name, isDir),
                    isLocal = false,
                    rawSize = rawSize
                )
            )
        }
        return RemoteResult.Success(entries.sortedWith(compareByDescending<FileItem> { it.type == FileType.DIRECTORY }.thenBy { it.name }))
    }


    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> Res.get(R.string.x_b, bytes)
            bytes < 1024 * 1024 -> Res.get(R.string.x_1024_kb_2, (bytes / 1024))
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, Res.get(R.string.n_1f_mb), bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
