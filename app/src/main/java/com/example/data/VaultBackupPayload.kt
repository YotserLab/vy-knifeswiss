package com.example.data
import com.example.utils.crypto.TotpSecretCrypto

import com.example.utils.TotpGenerator
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.example.R
import com.example.Res

data class VaultBackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val vaultEntries: List<VaultEntry> = emptyList(),
    val totpAccounts: List<TotpAccount> = emptyList()
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): VaultBackupPayload? {
            val trimmed = json.trim()
            if (trimmed.contains("\"items\"")) {
                parseBitwardenJson(trimmed)?.let { return it }
            }
            return try {
                val payload = Gson().fromJson(trimmed, VaultBackupPayload::class.java)
                if (payload != null && (payload.vaultEntries.isNotEmpty() || payload.totpAccounts.isNotEmpty())) {
                    payload
                } else {
                    parseBitwardenJson(trimmed)
                }
            } catch (e: Exception) {
                parseBitwardenJson(trimmed)
            }
        }

        private fun parseBitwardenJson(json: String): VaultBackupPayload? {
            return try {
                val bw = Gson().fromJson(json, BitwardenExport::class.java)
                if (bw != null && bw.items.isNotEmpty()) {
                    val entries = mutableListOf<VaultEntry>()
                    val totpList = mutableListOf<TotpAccount>()

                    bw.items.forEach { item ->
                        val category = when (item.type) {
                            2 -> "note"
                            3 -> "card"
                            4 -> "identity"
                            else -> "login"
                        }
                        val username = item.login?.username ?: ""
                        val password = item.login?.password ?: ""
                        val url = item.login?.uris?.firstOrNull()?.uri ?: ""
                        val notes = item.notes ?: ""

                        entries.add(
                            VaultEntry(
                                title = item.name.ifBlank { Res.get(R.string.sem_titulo) },
                                category = category,
                                username = username,
                                encryptedPassword = password,
                                url = url,
                                notes = notes,
                                isFavorite = item.favorite
                            )
                        )

                        val totpSecret = item.login?.totp
                        if (!totpSecret.isNullOrBlank()) {
                            if (totpSecret.startsWith("otpauth://")) {
                                val parsed = TotpGenerator.parseOtpAuthUri(totpSecret)
                                if (parsed != null) {
                                    totpList.add(
                                        TotpAccount(
                                            issuer = parsed.issuer.ifBlank { item.name },
                                            accountName = parsed.account.ifBlank { username },
                                            encryptedSecret = com.example.utils.crypto.TotpSecretCrypto.encryptSecret(parsed.secret),
                                            algorithm = parsed.algorithm,
                                            digits = parsed.digits,
                                            period = parsed.period
                                        )
                                    )
                                }
                            } else if (totpSecret.length >= 8) {
                                totpList.add(
                                    TotpAccount(
                                        issuer = item.name,
                                        accountName = username,
                                        encryptedSecret = com.example.utils.crypto.TotpSecretCrypto.encryptSecret(totpSecret.uppercase().replace(" ", "").replace("-", ""))
                                    )
                                )
                            }
                        }
                    }

                    VaultBackupPayload(
                        version = 1,
                        exportedAt = System.currentTimeMillis(),
                        vaultEntries = entries,
                        totpAccounts = totpList
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}

private data class BitwardenExport(
    val encrypted: Boolean = false,
    val items: List<BitwardenItem> = emptyList()
)

private data class BitwardenItem(
    val type: Int = 1,
    val name: String = "",
    val notes: String? = null,
    val favorite: Boolean = false,
    val login: BitwardenLogin? = null
)

private data class BitwardenLogin(
    val username: String? = null,
    val password: String? = null,
    val totp: String? = null,
    val uris: List<BitwardenUri>? = null
)

private data class BitwardenUri(
    val uri: String? = null
)
