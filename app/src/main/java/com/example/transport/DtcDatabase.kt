package com.example.transport
import com.example.R
import com.example.Res

/**
 * Database of OBD-II DTC descriptions.
 * Covers the most common P0xxx, P1xxx, C, B and U codes.
 */
object DtcDatabase {

    fun getDescription(code: String): String {
        return dtcMap[code.uppercase()] ?: when {
            code.startsWith("P0") -> Res.get(R.string.codigo_powertrain_padrao_obd_ii_x, code)
            code.startsWith("P1") -> Res.get(R.string.codigo_powertrain_especifico_do_fabrican, code)
            code.startsWith("P2") -> Res.get(R.string.codigo_powertrain_padrao_obd_ii_expandid, code)
            code.startsWith("P3") -> Res.get(R.string.codigo_powertrain_reservado_x, code)
            code.startsWith("C0") -> Res.get(R.string.codigo_de_chassi_padrao_x, code)
            code.startsWith("C1") -> Res.get(R.string.codigo_de_chassi_especifico_do_fabricant, code)
            code.startsWith("B0") -> Res.get(R.string.codigo_de_carroceria_padrao_x, code)
            code.startsWith("B1") -> Res.get(R.string.codigo_de_carroceria_especifico_do_fabri, code)
            code.startsWith("U0") -> Res.get(R.string.codigo_de_rede_comunicacao_padrao_x, code)
            code.startsWith("U1") -> Res.get(R.string.codigo_de_rede_especifico_do_fabricante, code)
            else -> Res.get(R.string.codigo_de_diagnostico_x, code)
        }
    }

    private val dtcMap: Map<String, String> = mapOf(
        // ── P01xx — Air/fuel measurement ────────────────────────────────
        "P0100" to Res.get(R.string.sensor_maf_falha_no_circuito),
        "P0101" to Res.get(R.string.sensor_maf_faixa_desempenho_fora_do_espe),
        "P0102" to Res.get(R.string.sensor_maf_sinal_baixo),
        "P0103" to Res.get(R.string.sensor_maf_sinal_alto),
        "P0104" to Res.get(R.string.sensor_maf_circuito_intermitente),
        "P0105" to Res.get(R.string.sensor_map_falha_no_circuito),
        "P0106" to Res.get(R.string.sensor_map_faixa_desempenho_fora_do_espe),
        "P0107" to Res.get(R.string.sensor_map_sinal_baixo),
        "P0108" to Res.get(R.string.sensor_map_sinal_alto),
        "P0109" to Res.get(R.string.sensor_map_circuito_intermitente),
        "P0110" to Res.get(R.string.sensor_iat_temp_admissao_falha_no_circui),
        "P0111" to Res.get(R.string.sensor_iat_faixa_desempenho),
        "P0112" to Res.get(R.string.sensor_iat_sinal_baixo),
        "P0113" to Res.get(R.string.sensor_iat_sinal_alto),
        "P0114" to Res.get(R.string.sensor_iat_circuito_intermitente),
        "P0115" to Res.get(R.string.sensor_ect_temp_motor_falha_no_circuito),
        "P0116" to Res.get(R.string.sensor_ect_faixa_desempenho),
        "P0117" to Res.get(R.string.sensor_ect_sinal_baixo),
        "P0118" to Res.get(R.string.sensor_ect_sinal_alto),
        "P0119" to Res.get(R.string.sensor_ect_circuito_intermitente),
        "P0120" to Res.get(R.string.sensor_tps_app_a_falha_no_circuito),
        "P0121" to Res.get(R.string.sensor_tps_app_a_faixa_desempenho),
        "P0122" to Res.get(R.string.sensor_tps_app_a_sinal_baixo),
        "P0123" to Res.get(R.string.sensor_tps_app_a_sinal_alto),
        "P0124" to Res.get(R.string.sensor_tps_app_a_circuito_intermitente),
        "P0125" to Res.get(R.string.temperatura_insuficiente_para_controle_d),
        "P0126" to Res.get(R.string.temperatura_insuficiente_para_operacao_e),
        "P0128" to Res.get(R.string.temp_do_liquido_de_arrefecimento_abaixo),
        "P0130" to Res.get(R.string.sensor_o2_banco_1_sensor_1_falha_no_circ),
        "P0131" to Res.get(R.string.sensor_o2_b1s1_sinal_baixo),
        "P0132" to Res.get(R.string.sensor_o2_b1s1_sinal_alto),
        "P0133" to Res.get(R.string.sensor_o2_b1s1_resposta_lenta),
        "P0134" to Res.get(R.string.sensor_o2_b1s1_sem_atividade),
        "P0135" to Res.get(R.string.aquecedor_do_sensor_o2_b1s1_falha),
        "P0136" to Res.get(R.string.sensor_o2_banco_1_sensor_2_falha_no_circ),
        "P0137" to Res.get(R.string.sensor_o2_b1s2_sinal_baixo),
        "P0138" to Res.get(R.string.sensor_o2_b1s2_sinal_alto),
        "P0140" to Res.get(R.string.sensor_o2_b1s2_sem_atividade),
        "P0141" to Res.get(R.string.aquecedor_do_sensor_o2_b1s2_falha),
        "P0150" to Res.get(R.string.sensor_o2_banco_2_sensor_1_falha_no_circ),
        "P0151" to Res.get(R.string.sensor_o2_b2s1_sinal_baixo),
        "P0152" to Res.get(R.string.sensor_o2_b2s1_sinal_alto),
        "P0153" to Res.get(R.string.sensor_o2_b2s1_resposta_lenta),
        "P0155" to Res.get(R.string.aquecedor_do_sensor_o2_b2s1_falha),
        "P0156" to Res.get(R.string.sensor_o2_banco_2_sensor_2_falha_no_circ),
        "P0161" to Res.get(R.string.aquecedor_do_sensor_o2_b2s2_falha),

        // ── P016x–P017x — Fuel trim ────────────────────────────
        "P0170" to Res.get(R.string.correcao_de_combustivel_banco_1_mistura),
        "P0171" to Res.get(R.string.sistema_de_combustivel_mistura_muito_pob),
        "P0172" to Res.get(R.string.sistema_de_combustivel_mistura_muito_ric),
        "P0173" to Res.get(R.string.correcao_de_combustivel_banco_2_mistura),
        "P0174" to Res.get(R.string.sistema_de_combustivel_mistura_muito_pob_2),
        "P0175" to Res.get(R.string.sistema_de_combustivel_mistura_muito_ric_2),

        // ── P02xx — Fuel injection ───────────────────────────────────
        "P0200" to Res.get(R.string.injetor_de_combustivel_circuito_aberto),
        "P0201" to Res.get(R.string.injetor_cilindro_1_falha_no_circuito),
        "P0202" to Res.get(R.string.injetor_cilindro_2_falha_no_circuito),
        "P0203" to Res.get(R.string.injetor_cilindro_3_falha_no_circuito),
        "P0204" to Res.get(R.string.injetor_cilindro_4_falha_no_circuito),
        "P0205" to Res.get(R.string.injetor_cilindro_5_falha_no_circuito),
        "P0206" to Res.get(R.string.injetor_cilindro_6_falha_no_circuito),
        "P0207" to Res.get(R.string.injetor_cilindro_7_falha_no_circuito),
        "P0208" to Res.get(R.string.injetor_cilindro_8_falha_no_circuito),

        // ── P03xx — Ignition misfires ─────────────────────────────────────────
        "P0300" to Res.get(R.string.falha_de_ignicao_aleatoria_multipla_dete),
        "P0301" to Res.get(R.string.falha_de_ignicao_cilindro_1),
        "P0302" to Res.get(R.string.falha_de_ignicao_cilindro_2),
        "P0303" to Res.get(R.string.falha_de_ignicao_cilindro_3),
        "P0304" to Res.get(R.string.falha_de_ignicao_cilindro_4),
        "P0305" to Res.get(R.string.falha_de_ignicao_cilindro_5),
        "P0306" to Res.get(R.string.falha_de_ignicao_cilindro_6),
        "P0307" to Res.get(R.string.falha_de_ignicao_cilindro_7),
        "P0308" to Res.get(R.string.falha_de_ignicao_cilindro_8),
        "P0316" to Res.get(R.string.falha_de_ignicao_detectada_na_partida_do),
        "P0320" to Res.get(R.string.sensor_de_posicao_do_virabrequim_ckp_cir),
        "P0325" to Res.get(R.string.sensor_knock_detonacao_banco_1_falha_no),
        "P0326" to Res.get(R.string.sensor_knock_banco_1_faixa_desempenho),
        "P0327" to Res.get(R.string.sensor_knock_banco_1_sinal_baixo),
        "P0328" to Res.get(R.string.sensor_knock_banco_1_sinal_alto),
        "P0335" to Res.get(R.string.sensor_ckp_posicao_virabrequim_falha_no),
        "P0336" to Res.get(R.string.sensor_ckp_faixa_desempenho),
        "P0340" to Res.get(R.string.sensor_cmp_posicao_comando_de_valvulas_b),
        "P0341" to Res.get(R.string.sensor_cmp_banco_1_faixa_desempenho),
        "P0345" to Res.get(R.string.sensor_cmp_banco_2_falha_no_circuito),
        "P0351" to Res.get(R.string.bobina_de_ignicao_cilindro_1_falha_no_ci),
        "P0352" to Res.get(R.string.bobina_de_ignicao_cilindro_2_falha_no_ci),
        "P0353" to Res.get(R.string.bobina_de_ignicao_cilindro_3_falha_no_ci),
        "P0354" to Res.get(R.string.bobina_de_ignicao_cilindro_4_falha_no_ci),

        // ── P04xx — Emissions ──────────────────────────────────────────────────
        "P0400" to Res.get(R.string.sistema_egr_fluxo_insuficiente),
        "P0401" to Res.get(R.string.sistema_egr_fluxo_abaixo_do_esperado),
        "P0402" to Res.get(R.string.sistema_egr_fluxo_excessivo),
        "P0403" to Res.get(R.string.sistema_egr_circuito_da_valvula),
        "P0404" to Res.get(R.string.sistema_egr_faixa_desempenho_do_circuito),
        "P0405" to Res.get(R.string.sensor_posicao_valvula_egr_a_sinal_baixo),
        "P0406" to Res.get(R.string.sensor_posicao_valvula_egr_a_sinal_alto),
        "P0410" to Res.get(R.string.sistema_de_ar_secundario_falha),
        "P0411" to Res.get(R.string.sistema_de_ar_secundario_fluxo_incorreto),
        "P0412" to Res.get(R.string.valvula_de_controle_de_ar_secundario_fal),
        "P0420" to Res.get(R.string.eficiencia_do_catalisador_abaixo_do_limi),
        "P0421" to Res.get(R.string.eficiencia_do_catalisador_abaixo_do_limi_2),
        "P0422" to Res.get(R.string.eficiencia_do_catalisador_principal_abai),
        "P0430" to Res.get(R.string.eficiencia_do_catalisador_abaixo_do_limi_3),
        "P0431" to Res.get(R.string.eficiencia_do_catalisador_abaixo_do_limi_4),
        "P0440" to Res.get(R.string.sistema_evap_falha_geral),
        "P0441" to Res.get(R.string.sistema_evap_fluxo_incorreto_no_sistema),
        "P0442" to Res.get(R.string.sistema_evap_vazamento_pequeno_detectado),
        "P0443" to Res.get(R.string.sistema_evap_valvula_de_purga_falha_no_c),
        "P0444" to Res.get(R.string.sistema_evap_valvula_de_purga_circuito_a),
        "P0445" to Res.get(R.string.sistema_evap_valvula_de_purga_curto_para),
        "P0446" to Res.get(R.string.sistema_evap_circuito_de_ventilacao_falh),
        "P0447" to Res.get(R.string.sistema_evap_valvula_de_ventilacao_circu),
        "P0448" to Res.get(R.string.sistema_evap_valvula_de_ventilacao_curto),
        "P0449" to Res.get(R.string.sistema_evap_valvula_de_ventilacao_solen),
        "P0450" to Res.get(R.string.sensor_de_pressao_evap_falha_no_circuito),
        "P0451" to Res.get(R.string.sensor_de_pressao_evap_faixa_desempenho),
        "P0452" to Res.get(R.string.sensor_de_pressao_evap_sinal_baixo),
        "P0453" to Res.get(R.string.sensor_de_pressao_evap_sinal_alto),
        "P0455" to Res.get(R.string.sistema_evap_vazamento_muito_grande_dete),
        "P0456" to Res.get(R.string.sistema_evap_vazamento_muito_pequeno_det),
        "P0457" to Res.get(R.string.sistema_evap_vazamento_detectado_tampa_c),
        "P0460" to Res.get(R.string.sensor_de_nivel_de_combustivel_faixa_des),
        "P0461" to Res.get(R.string.sensor_de_nivel_de_combustivel_faixa_de),
        "P0462" to Res.get(R.string.sensor_de_nivel_de_combustivel_sinal_bai),
        "P0463" to Res.get(R.string.sensor_de_nivel_de_combustivel_sinal_alt),
        "P0480" to Res.get(R.string.rele_do_ventilador_de_resfriamento_falha),
        "P0481" to Res.get(R.string.rele_do_ventilador_de_resfriamento_veloc),

        // ── P05xx — Speed control ────────────────────────────────────
        "P0500" to Res.get(R.string.sensor_de_velocidade_do_veiculo_vss_falh),
        "P0501" to Res.get(R.string.sensor_de_velocidade_do_veiculo_faixa_de),
        "P0502" to Res.get(R.string.sensor_de_velocidade_do_veiculo_sinal_ba),
        "P0503" to Res.get(R.string.sensor_de_velocidade_do_veiculo_intermit),
        "P0505" to Res.get(R.string.sistema_de_marcha_lenta_iac_falha),
        "P0506" to Res.get(R.string.sistema_de_marcha_lenta_rpm_abaixo_do_es),
        "P0507" to Res.get(R.string.sistema_de_marcha_lenta_rpm_acima_do_esp),
        "P0508" to Res.get(R.string.circuito_de_controle_da_marcha_lenta_sin),
        "P0509" to Res.get(R.string.circuito_de_controle_da_marcha_lenta_sin_2),

        // ── P06xx — Communication / ECU ─────────────────────────────────────────
        "P0600" to Res.get(R.string.link_de_comunicacao_serial_falha),
        "P0601" to Res.get(R.string.memoria_interna_da_ecu_erro_de_checksum),
        "P0602" to Res.get(R.string.modulo_de_controle_erro_de_programacao),
        "P0603" to Res.get(R.string.modulo_de_controle_ram_interna_falha_kam),
        "P0604" to Res.get(R.string.modulo_de_controle_ram_interna_falha),
        "P0605" to Res.get(R.string.modulo_de_controle_rom_interna_falha),
        "P0606" to Res.get(R.string.modulo_de_controle_falha_geral_do_proces),
        "P0607" to Res.get(R.string.modulo_de_controle_desempenho),
        "P0620" to Res.get(R.string.circuito_de_controle_do_gerador_falha),
        "P0625" to Res.get(R.string.sinal_de_campo_do_gerador_sinal_baixo),
        "P0626" to Res.get(R.string.sinal_de_campo_do_gerador_sinal_alto),

        // ── P07xx — Transmission ───────────────────────────────────────────────
        "P0700" to Res.get(R.string.codigo_de_falha_solicitado_pela_tcm_tran),
        "P0705" to Res.get(R.string.sensor_de_posicao_do_inibidor_range_tr_c),
        "P0706" to Res.get(R.string.sensor_de_posicao_do_inibidor_range_faix),
        "P0715" to Res.get(R.string.sensor_de_velocidade_do_eixo_de_entrada),
        "P0720" to Res.get(R.string.sensor_de_velocidade_do_eixo_de_saida_fa),
        "P0730" to Res.get(R.string.relacao_de_cambio_incorreta),
        "P0731" to Res.get(R.string.n_1a_marcha_relacao_incorreta),
        "P0732" to Res.get(R.string.n_2a_marcha_relacao_incorreta),
        "P0733" to Res.get(R.string.n_3a_marcha_relacao_incorreta),
        "P0734" to Res.get(R.string.n_4a_marcha_relacao_incorreta),
        "P0735" to Res.get(R.string.n_5a_marcha_relacao_incorreta),
        "P0740" to Res.get(R.string.embreagem_do_conversor_de_torque_falha_n),
        "P0741" to Res.get(R.string.embreagem_do_conversor_de_torque_desempe),
        "P0745" to Res.get(R.string.solenoide_de_controle_de_pressao_falha_n),
        "P0748" to Res.get(R.string.solenoide_de_controle_de_pressao_falha_e),
        "P0750" to Res.get(R.string.solenoide_shift_a_falha_no_circuito),
        "P0755" to Res.get(R.string.solenoide_shift_b_falha_no_circuito),
        "P0760" to Res.get(R.string.solenoide_shift_c_falha_no_circuito),
        "P0780" to Res.get(R.string.falha_de_troca_de_marcha),
        "P0781" to Res.get(R.string.n_1a_para_2a_falha_de_troca),
        "P0782" to Res.get(R.string.n_2a_para_3a_falha_de_troca),
        "P0783" to Res.get(R.string.n_3a_para_4a_falha_de_troca),
        "P0784" to Res.get(R.string.n_4a_para_5a_falha_de_troca),

        // ── P08xx — Fuel pressure ────────────────────────────────────
        "P0800" to Res.get(R.string.transmissao_controle_de_modo_de_transfer),
        "P0850" to Res.get(R.string.sensor_de_posicao_do_neutro_falha_no_cir),

        // ── C — Chassis ───────────────────────────────────────────────────────
        "C0035" to Res.get(R.string.abs_sensor_de_velocidade_da_roda_diantei),
        "C0040" to Res.get(R.string.abs_sensor_de_velocidade_da_roda_diantei_2),
        "C0045" to Res.get(R.string.abs_sensor_de_velocidade_da_roda_traseir),
        "C0050" to Res.get(R.string.abs_sensor_de_velocidade_da_roda_traseir_2),
        "C0110" to Res.get(R.string.abs_motor_da_bomba_falha_no_circuito),
        "C0121" to Res.get(R.string.abs_valvula_solenoide_falha_no_circuito),
        "C0265" to Res.get(R.string.abs_rele_de_alimentacao_do_solenoide_ebc),

        // ── B — Body ──────────────────────────────────────────────────────────
        "B0001" to Res.get(R.string.airbag_sistema_de_ignicao_do_motorista_f),
        "B0002" to Res.get(R.string.airbag_sistema_de_ignicao_do_passageiro),
        "B0010" to Res.get(R.string.cinto_de_seguranca_pre_tensionador_motor),
        "B0020" to Res.get(R.string.cinto_de_seguranca_pre_tensionador_passa),
        "B1000" to Res.get(R.string.modulo_de_controle_da_carroceria_falha_i),
        "B1001" to Res.get(R.string.airbag_modulo_de_controle_falha),

        // ── U — Network ───────────────────────────────────────────────────────
        "U0001" to Res.get(R.string.bus_can_de_alta_velocidade_comunicacao_p),
        "U0100" to Res.get(R.string.comunicacao_perdida_com_o_modulo_ecm_pcm),
        "U0101" to Res.get(R.string.comunicacao_perdida_com_a_tcm),
        "U0121" to Res.get(R.string.comunicacao_perdida_com_o_modulo_abs),
        "U0140" to Res.get(R.string.comunicacao_perdida_com_o_modulo_bcm),
        "U0155" to Res.get(R.string.comunicacao_perdida_com_o_cluster_de_ins)
    )
}
