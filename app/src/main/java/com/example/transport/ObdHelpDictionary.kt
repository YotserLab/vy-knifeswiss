package com.example.transport
import com.example.R
import com.example.Res

data class SensorHelpInfo(
    val title: String,
    val description: String,
    val normalRange: String,
    val symptomOfFailure: String
)

object ObdHelpDictionary {
    val glossary = mapOf(
        "RPM" to SensorHelpInfo(
            title = Res.get(R.string.rotacoes_por_minuto_rpm),
            description = Res.get(R.string.mede_a_velocidade_em_que_o_eixo_do_motor),
            normalRange = Res.get(R.string.marcha_lenta_700_a_1000_rpm_conducao_150),
            symptomOfFailure = Res.get(R.string.marcha_lenta_oscilando_carro_morrendo_ou)
        ),
        "Velocidade" to SensorHelpInfo(
            title = Res.get(R.string.velocidade_do_veiculo),
            description = Res.get(R.string.a_velocidade_atual_relatada_pelo_modulo),
            normalRange = Res.get(R.string.depende_da_via),
            symptomOfFailure = Res.get(R.string.painel_marcando_errado_ou_cambio_automat)
        ),
        Res.get(R.string.carga_do_motor) to SensorHelpInfo(
            title = Res.get(R.string.carga_calculada_do_motor),
            description = Res.get(R.string.mostra_quanto_esforco_o_motor_esta_fazen),
            normalRange = Res.get(R.string.marcha_lenta_15_30_aceleracao_total_80_1),
            symptomOfFailure = Res.get(R.string.carga_alta_sem_estar_acelerando_pode_ind)
        ),
        "Arrefecimento" to SensorHelpInfo(
            title = Res.get(R.string.temperatura_do_liquido_de_arrefecimento),
            description = Res.get(R.string.a_temperatura_do_radiador_e_bloco_do_mot),
            normalRange = Res.get(R.string.ideal_85c_a_105c),
            symptomOfFailure = Res.get(R.string.temperatura_acima_de_110c_risco_de_super)
        ),
        Res.get(R.string.admissao_iat) to SensorHelpInfo(
            title = Res.get(R.string.temperatura_do_ar_de_admissao),
            description = Res.get(R.string.a_temperatura_do_ar_fresco_que_esta_entr),
            normalRange = Res.get(R.string.normalmente_de_10c_a_30c_acima_da_temper),
            symptomOfFailure = Res.get(R.string.temperatura_extremamente_alta_indica_que)
        ),
        Res.get(R.string.maf_fluxo_de_ar) to SensorHelpInfo(
            title = Res.get(R.string.sensor_maf_mass_air_flow),
            description = Res.get(R.string.mede_a_quantidade_de_ar_em_gramas_por_se),
            normalRange = Res.get(R.string.marcha_lenta_2_a_5_g_s_depende_do_motor),
            symptomOfFailure = Res.get(R.string.se_estiver_lendo_pouco_ar_a_mistura_fica)
        ),
        Res.get(R.string.stft_banco_1) to SensorHelpInfo(
            title = Res.get(R.string.correcao_de_combustivel_a_curto_prazo_st),
            description = Res.get(R.string.mostra_o_que_o_computador_esta_fazendo_a),
            normalRange = Res.get(R.string.n_5_a_5_e_perfeito),
            symptomOfFailure = Res.get(R.string.acima_de_10_constante_indica_entrada_de)
        ),
        Res.get(R.string.ltft_banco_1) to SensorHelpInfo(
            title = Res.get(R.string.correcao_de_combustivel_a_longo_prazo_lt),
            description = Res.get(R.string.e_a_memoria_historica_das_correcoes_se_o),
            normalRange = Res.get(R.string.n_8_a_8),
            symptomOfFailure = Res.get(R.string.se_estiver_em_25_a_luz_da_injecao_acende)
        ),
        Res.get(R.string.coletor_map) to SensorHelpInfo(
            title = Res.get(R.string.pressao_absoluta_do_coletor_map),
            description = Res.get(R.string.mede_a_pressao_ou_vacuo_dentro_do_coleto),
            normalRange = Res.get(R.string.marcha_lenta_25_a_40_kpa_bastante_vacuo),
            symptomOfFailure = Res.get(R.string.se_ler_100_kpa_com_o_motor_ligado_ha_um)
        ),
        Res.get(R.string.o2_b1s1) to SensorHelpInfo(
            title = Res.get(R.string.sonda_lambda_primaria_antes_do_catalisad),
            description = Res.get(R.string.mede_a_quantidade_de_oxigenio_no_escapam),
            normalRange = Res.get(R.string.oscila_rapidamente_entre_0_1v_pobre_e_0),
            symptomOfFailure = Res.get(R.string.se_a_voltagem_ficar_travada_em_0v_o_sens)
        ),
        Res.get(R.string.o2_b1s2) to SensorHelpInfo(
            title = Res.get(R.string.sonda_lambda_secundaria_depois_do_catali),
            description = Res.get(R.string.serve_exclusivamente_para_vigiar_o_catal),
            normalRange = Res.get(R.string.estavel_em_torno_de_0_4v_a_0_7v),
            symptomOfFailure = Res.get(R.string.se_o_grafico_dessa_sonda_estiver_oscilan)
        ),
        Res.get(R.string.tensao_obd_bateria) to SensorHelpInfo(
            title = Res.get(R.string.tensao_do_modulo_bateria),
            description = Res.get(R.string.mostra_a_voltagem_que_esta_alimentando_a),
            normalRange = Res.get(R.string.motor_desligado_12_2v_a_12_6v_motor_liga),
            symptomOfFailure = Res.get(R.string.abaixo_de_13v_com_o_motor_ligado_signifi)
        )
    )
}
