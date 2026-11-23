package com.example.transport
import com.example.R
import com.example.Res

/**
 * Represents a manufacturer-specific (OEM) custom PID.
 * Many cars require the OBD-II header to be changed before sending the command.
 */
data class CustomPid(
    val id: String,
    val name: String,
    val command: String, // Ex: "220105" (Modo 22, PID 0105)
    val header: String?, // E.g., "7E0" (Engine) or "7E1" (Transmission)
    val formula: String, // Text representation of the formula (e.g., "A-40") for reference
    val unit: String,
    val parse: (IntArray) -> Float? // Function that converts the response bytes (A, B, C) into the final value
)

data class OemProfile(
    val manufacturer: String,
    val models: List<String>,
    val pids: List<CustomPid>
)

object ObdOemProfiles {

    // Dedicated profile for the Renault/Dacia B0 platform (Sandero, Duster, Logan)
    val RENAULT_SANDERO = OemProfile(
        manufacturer = Res.get(R.string.renault_dacia),
        models = listOf(Res.get(R.string.sandero_stepway), "Sandero", "Duster", "Logan", "Oroch"),
        pids = listOf(
            CustomPid(
                id = "REN_ODOMETER",
                name = Res.get(R.string.odometro_real_painel),
                command = "222201",
                header = "7A0", // ECU do painel
                formula = "((A*65536)+(B*256)+C)/10",
                unit = "km",
                parse = { bytes ->
                    if (bytes.size >= 3) ((bytes[0] * 65536) + (bytes[1] * 256) + bytes[2]) / 10f else null
                }
            ),
            CustomPid(
                id = "REN_CVT_TEMP",
                name = Res.get(R.string.temperatura_cambio_automatico_cvt),
                command = "222002", // Example of a proprietary TCU PID
                header = "7E1", // Transmission ECU
                formula = Res.get(R.string.a_40),
                unit = Res.get(R.string.c),
                parse = { bytes ->
                    if (bytes.isNotEmpty()) (bytes[0] - 40).toFloat() else null
                }
            ),
            CustomPid(
                id = "REN_MISFIRE_CYL1",
                name = Res.get(R.string.contador_de_falhas_de_ignicao_cilindro_1),
                command = "221020",
                header = "7E0", // Engine ECU
                formula = "A*256+B",
                unit = "falhas",
                parse = { bytes ->
                    if (bytes.size >= 2) (bytes[0] * 256 + bytes[1]).toFloat() else null
                }
            ),
            CustomPid(
                id = "REN_FUEL_LEVEL_LITERS",
                name = Res.get(R.string.combustivel_real_no_tanque),
                command = "222202",
                header = "7A0",
                formula = "A/2",
                unit = "Litros",
                parse = { bytes ->
                    if (bytes.isNotEmpty()) (bytes[0] / 2f) else null
                }
            )
        )
    )

    // List of profiles built into the app
    val PRELOADED_PROFILES = listOf(RENAULT_SANDERO)
}
