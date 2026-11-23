package com.example.transport
import com.example.R
import com.example.Res

/**
 * Complete registry of standard SAE J1979 OBD-II PIDs.
 * Cobre Mode 01, Mode 02, Mode 06, Mode 09.
 * Extensible with user-defined Custom PIDs.
 */
// OBD-R03
object ObdPidRegistry {

    data class PidDefinition(
        val mode: String,
        val pid: String,
        val name: String,
        val description: String,
        val unit: String,
        val minValue: Float,
        val maxValue: Float,
        val category: PidCategory,
        val byteCount: Int = 1,
        val formula: (ByteArray) -> Float = { bytes -> bytes[0].toInt().and(0xFF).toFloat() }
    ) {
        val fullCommand get() = "$mode$pid"
        val responseHeader get() = "${(mode.toInt(16) + 0x40).toString(16).uppercase().padStart(2, '0')}$pid"
    }

    enum class PidCategory(val displayName: String) {
        ENGINE("Motor"),
        FUEL(Res.get(R.string.combustivel)),
        EXHAUST(Res.get(R.string.escape_emissoes)),
        INTAKE(Res.get(R.string.admissao)),
        ELECTRICAL(Res.get(R.string.eletrico)),
        TEMPERATURE("Temperaturas"),
        PRESSURE(Res.get(R.string.pressoes)),
        PERFORMANCE("Performance"),
        SYSTEM("Sistema")
    }

    val ALL: List<PidDefinition> = listOf(
        // ── System / Status ──────────────────────────────────────────────────
        PidDefinition("01", "01", Res.get(R.string.i_m_readiness), Res.get(R.string.status_dos_monitores_de_emissao), "", 0f, 255f, PidCategory.SYSTEM, 4),
        PidDefinition("01", "1C", Res.get(R.string.obd_standard), Res.get(R.string.tipo_de_padrao_obd_suportado_pelo_veicul), "", 0f, 255f, PidCategory.SYSTEM),
        PidDefinition("01", "1F", Res.get(R.string.tempo_de_funcionamento), Res.get(R.string.segundos_desde_a_partida_do_motor), "s", 0f, 65535f, PidCategory.SYSTEM, 2,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)).toFloat() }),
        PidDefinition("01", "21", Res.get(R.string.dist_com_mil), Res.get(R.string.distancia_percorrida_com_mil_ligada), "km", 0f, 65535f, PidCategory.SYSTEM, 2,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)).toFloat() }),
        PidDefinition("01", "30", Res.get(R.string.warm_ups_desde_dtc), Res.get(R.string.contagem_de_aquecimentos_desde_limpeza_d), "", 0f, 255f, PidCategory.SYSTEM),
        PidDefinition("01", "31", Res.get(R.string.dist_desde_dtc), Res.get(R.string.distancia_percorrida_desde_limpeza_de_dt), "km", 0f, 65535f, PidCategory.SYSTEM, 2,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)).toFloat() }),
        PidDefinition("01", "4E", Res.get(R.string.tempo_desde_dtc), Res.get(R.string.minutos_desde_limpeza_de_dtcs), "min", 0f, 65535f, PidCategory.SYSTEM, 2,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)).toFloat() }),

        // ── Engine ────────────────────────────────────────────────────────────
        PidDefinition("01", "04", Res.get(R.string.carga_do_motor), Res.get(R.string.carga_calculada_pelo_motor), "%", 0f, 100f, PidCategory.ENGINE,
            formula = { b -> b[0].toInt().and(0xFF) * 100f / 255f }),
        PidDefinition("01", "0C", "RPM", Res.get(R.string.rotacoes_por_minuto_do_motor), "RPM", 0f, 16383.75f, PidCategory.ENGINE, 2,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)) / 4f }),
        PidDefinition("01", "0D", "Velocidade", Res.get(R.string.velocidade_do_veiculo_2), Res.get(R.string.km_h), 0f, 255f, PidCategory.ENGINE),
        PidDefinition("01", "0E", Res.get(R.string.avanco_ignicao), Res.get(R.string.avanco_de_ignicao_do_cilindro_1), "°", -64f, 63.5f, PidCategory.ENGINE,
            formula = { b -> b[0].toInt().and(0xFF) / 2f - 64f }),
        PidDefinition("01", "11", Res.get(R.string.acelerador_tps), Res.get(R.string.posicao_absoluta_do_acelerador), "%", 0f, 100f, PidCategory.ENGINE,
            formula = { b -> b[0].toInt().and(0xFF) * 100f / 255f }),
        PidDefinition("01", "2C", Res.get(R.string.duty_cycle_egr), Res.get(R.string.duty_cycle_comandado_do_egr), "%", 0f, 100f, PidCategory.ENGINE,
            formula = { b -> b[0].toInt().and(0xFF) * 100f / 255f }),
        PidDefinition("01", "2D", Res.get(R.string.erro_egr), Res.get(R.string.erro_do_egr), "%", -100f, 99.2f, PidCategory.ENGINE,
            formula = { b -> b[0].toInt().and(0xFF) * 100f / 128f - 100f }),
        PidDefinition("01", "2F", Res.get(R.string.nivel_combustivel), Res.get(R.string.nivel_do_tanque_de_combustivel), "%", 0f, 100f, PidCategory.FUEL,
            formula = { b -> b[0].toInt().and(0xFF) * 100f / 255f }),
        PidDefinition("01", "41", Res.get(R.string.carga_motor_abs), Res.get(R.string.carga_absoluta_do_motor), "%", 0f, 25700f, PidCategory.ENGINE, 2,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)) * 100f / 255f }),
        PidDefinition("01", "43", Res.get(R.string.carga_absoluta), Res.get(R.string.valor_de_carga_absoluta_do_motor), "%", 0f, 100f, PidCategory.ENGINE),
        PidDefinition("01", "44", Res.get(R.string.razao_ar_comb), Res.get(R.string.razao_ar_combustivel_comandada_equivalen), "λ", 0f, 2f, PidCategory.ENGINE, 2,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)) / 32768f }),
        PidDefinition("01", "45", Res.get(R.string.acelerador_relativo), Res.get(R.string.posicao_relativa_do_acelerador), "%", 0f, 100f, PidCategory.ENGINE,
            formula = { b -> b[0].toInt().and(0xFF) * 100f / 255f }),
        PidDefinition("01", "4C", Res.get(R.string.acelerador_comandado), Res.get(R.string.posicao_comandada_do_acelerador), "%", 0f, 100f, PidCategory.ENGINE,
            formula = { b -> b[0].toInt().and(0xFF) * 100f / 255f }),
        PidDefinition("01", "5D", Res.get(R.string.posicao_pedal_inje), Res.get(R.string.posicao_do_pedal_do_injetor), "%", -100f, 99.2f, PidCategory.ENGINE,
            formula = { b -> b[0].toInt().and(0xFF) * 100f / 128f - 100f }),
        PidDefinition("01", "5E", Res.get(R.string.fluxo_combustivel), Res.get(R.string.taxa_de_fluxo_de_combustivel_do_motor), Res.get(R.string.l_h), 0f, 3212.75f, PidCategory.FUEL, 2,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)) / 20f }),

        // ── Fuel Trims ────────────────────────────────────────────────────────
        PidDefinition("01", "06", Res.get(R.string.stft_banco_1), Res.get(R.string.correcao_de_combustivel_curto_prazo_banc), "%", -100f, 99.2f, PidCategory.FUEL,
            formula = { b -> b[0].toInt().and(0xFF) * 100f / 128f - 100f }),
        PidDefinition("01", "07", Res.get(R.string.ltft_banco_1), Res.get(R.string.correcao_de_combustivel_longo_prazo_banc), "%", -100f, 99.2f, PidCategory.FUEL,
            formula = { b -> b[0].toInt().and(0xFF) * 100f / 128f - 100f }),
        PidDefinition("01", "08", Res.get(R.string.stft_banco_2), Res.get(R.string.correcao_de_combustivel_curto_prazo_banc_2), "%", -100f, 99.2f, PidCategory.FUEL,
            formula = { b -> b[0].toInt().and(0xFF) * 100f / 128f - 100f }),
        PidDefinition("01", "09", Res.get(R.string.ltft_banco_2), Res.get(R.string.correcao_de_combustivel_longo_prazo_banc_2), "%", -100f, 99.2f, PidCategory.FUEL,
            formula = { b -> b[0].toInt().and(0xFF) * 100f / 128f - 100f }),

        // ── Temperatures ──────────────────────────────────────────────────────
        PidDefinition("01", "05", Res.get(R.string.temp_arrefecimento), Res.get(R.string.temperatura_do_liquido_de_arrefecimento_2), Res.get(R.string.c), -40f, 215f, PidCategory.TEMPERATURE,
            formula = { b -> b[0].toInt().and(0xFF) - 40f }),
        PidDefinition("01", "0F", Res.get(R.string.temp_admissao), Res.get(R.string.temperatura_do_ar_de_admissao_2), Res.get(R.string.c), -40f, 215f, PidCategory.TEMPERATURE,
            formula = { b -> b[0].toInt().and(0xFF) - 40f }),
        PidDefinition("01", "46", Res.get(R.string.temp_ambiente), Res.get(R.string.temperatura_ambiente_do_ar_externo), Res.get(R.string.c), -40f, 215f, PidCategory.TEMPERATURE,
            formula = { b -> b[0].toInt().and(0xFF) - 40f }),
        PidDefinition("01", "5C", Res.get(R.string.temp_oleo), Res.get(R.string.temperatura_do_oleo_do_motor), Res.get(R.string.c), -40f, 210f, PidCategory.TEMPERATURE,
            formula = { b -> b[0].toInt().and(0xFF) - 40f }),
        PidDefinition("01", "3C", Res.get(R.string.temp_catalisador_b1s1), Res.get(R.string.temperatura_do_catalisador_banco_1_senso), Res.get(R.string.c), -40f, 6513.5f, PidCategory.EXHAUST, 2,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)) / 10f - 40f }),
        PidDefinition("01", "3D", Res.get(R.string.temp_catalisador_b2s1), Res.get(R.string.temperatura_do_catalisador_banco_2_senso), Res.get(R.string.c), -40f, 6513.5f, PidCategory.EXHAUST, 2,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)) / 10f - 40f }),

        // ── Pressures ─────────────────────────────────────────────────────────
        PidDefinition("01", "0A", Res.get(R.string.pressao_combustivel), Res.get(R.string.pressao_do_combustivel_no_rail), "kPa", 0f, 765f, PidCategory.PRESSURE,
            formula = { b -> b[0].toInt().and(0xFF) * 3f }),
        PidDefinition("01", "0B", Res.get(R.string.pressao_map), Res.get(R.string.pressao_absoluta_do_coletor_de_admissao), "kPa", 0f, 255f, PidCategory.PRESSURE),
        PidDefinition("01", "0D", "Velocidade", Res.get(R.string.velocidade_do_veiculo_2), Res.get(R.string.km_h), 0f, 255f, PidCategory.ENGINE),
        PidDefinition("01", "33", Res.get(R.string.pressao_barometrica), Res.get(R.string.pressao_barometrica_absoluta), "kPa", 0f, 255f, PidCategory.PRESSURE),
        PidDefinition("01", "32", Res.get(R.string.pressao_vapor_evap), Res.get(R.string.pressao_de_vapor_do_sistema_evap), "Pa", -8192f, 8191.75f, PidCategory.EXHAUST, 2,
            { b ->
                val raw = (b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)
                val signed = if (raw >= 32768) raw - 65536 else raw
                signed / 4f
            }),
        PidDefinition("01", "53", Res.get(R.string.pressao_abs_evap), Res.get(R.string.pressao_absoluta_do_sistema_evap), "kPa", 0f, 327.675f, PidCategory.EXHAUST, 2,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)) / 200f }),

        // ── Intake / Air ──────────────────────────────────────────────────────
        PidDefinition("01", "10", Res.get(R.string.maf_fluxo_ar), Res.get(R.string.fluxo_de_ar_medido_pelo_sensor_maf), Res.get(R.string.g_s), 0f, 655.35f, PidCategory.INTAKE, 2,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)) / 100f }),

        // ── O2 Sensors ────────────────────────────────────────────────────────
        PidDefinition("01", "13", Res.get(R.string.sensores_o2_presentes), Res.get(R.string.sensores_de_oxigenio_presentes_bitmask), "", 0f, 255f, PidCategory.EXHAUST),
        PidDefinition("01", "14", Res.get(R.string.o2_b1s1_tensao), Res.get(R.string.tensao_do_sensor_o2_banco_1_sensor_1), "V", 0f, 1.275f, PidCategory.EXHAUST, 2,
            { b -> b[0].toInt().and(0xFF) / 200f }),
        PidDefinition("01", "15", Res.get(R.string.o2_b1s2_tensao), Res.get(R.string.tensao_do_sensor_o2_banco_1_sensor_2), "V", 0f, 1.275f, PidCategory.EXHAUST, 2,
            { b -> b[0].toInt().and(0xFF) / 200f }),
        PidDefinition("01", "16", Res.get(R.string.o2_b1s3_tensao), Res.get(R.string.tensao_do_sensor_o2_banco_1_sensor_3), "V", 0f, 1.275f, PidCategory.EXHAUST, 2,
            { b -> b[0].toInt().and(0xFF) / 200f }),
        PidDefinition("01", "17", Res.get(R.string.o2_b1s4_tensao), Res.get(R.string.tensao_do_sensor_o2_banco_1_sensor_4), "V", 0f, 1.275f, PidCategory.EXHAUST, 2,
            { b -> b[0].toInt().and(0xFF) / 200f }),
        PidDefinition("01", "24", Res.get(R.string.o2_b1s1_wideband), Res.get(R.string.o2_wideband_equivalencia_banco_1_sensor), "λ", 0f, 2f, PidCategory.EXHAUST, 4,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)) / 32768f }),
        PidDefinition("01", "25", Res.get(R.string.o2_b1s2_wideband), Res.get(R.string.o2_wideband_equivalencia_banco_1_sensor_2), "λ", 0f, 2f, PidCategory.EXHAUST, 4,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)) / 32768f }),

        // ── Electrical ───────────────────────────────────────────────────────
        PidDefinition("01", "42", Res.get(R.string.tensao_modulo), Res.get(R.string.tensao_de_controle_do_modulo_bateria), "V", 0f, 65.535f, PidCategory.ELECTRICAL, 2,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)) / 1000f }),

        // ── Performance / Turbo ───────────────────────────────────────────────
        PidDefinition("01", "4D", Res.get(R.string.tempo_com_mil), Res.get(R.string.tempo_de_funcionamento_com_mil_ligada), "min", 0f, 65535f, PidCategory.SYSTEM, 2,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)).toFloat() }),
        PidDefinition("01", "52", Res.get(R.string.alcool_combustivel), Res.get(R.string.porcentagem_de_etanol_no_combustivel), "%", 0f, 100f, PidCategory.FUEL,
            formula = { b -> b[0].toInt().and(0xFF) * 100f / 255f }),
        PidDefinition("01", "67", Res.get(R.string.temp_arref_turbo), Res.get(R.string.temperatura_do_liquido_de_arref_do_turbo), Res.get(R.string.c), -40f, 215f, PidCategory.TEMPERATURE,
            formula = { b -> b[0].toInt().and(0xFF) - 40f }),
        PidDefinition("01", "70", Res.get(R.string.pressao_turbo_abs), Res.get(R.string.pressao_de_boost_do_turbo_absoluta), "kPa", 0f, 2000f, PidCategory.PRESSURE, 4,
            { b -> ((b[0].toInt().and(0xFF) * 256) + b[1].toInt().and(0xFF)) * 0.03125f })
    )

    val BY_CATEGORY: Map<PidCategory, List<PidDefinition>> = ALL.groupBy { it.category }

    val FAST_POLL: List<PidDefinition> = ALL.filter { it.pid in setOf("0C", "0D", "05", "04", "11") }

    fun findByPid(mode: String, pid: String): PidDefinition? =
        ALL.find { it.mode == mode && it.pid.equals(pid, ignoreCase = true) }
}
