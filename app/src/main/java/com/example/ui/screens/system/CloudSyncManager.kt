package com.example.ui.screens.system

import android.content.Context
import android.content.Intent
import com.example.BuildConfig
import android.net.Uri
import android.util.Log
import com.example.utils.crypto.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import com.example.R
import com.example.Res

// ─────────────────────────────────────────────────────────────────────────────
// CloudProvider enum
// ─────────────────────────────────────────────────────────────────────────────
enum class CloudProvider(
    val label: String,
    val iconEmoji: String,
    val prefsKey: String
) {
    GDRIVE(Res.get(R.string.google_drive), "🔵", "gdrive"),
    ONEDRIVE(Res.get(R.string.onedrive), "🔷", "onedrive"),
    DROPBOX("Dropbox", "📦", "dropbox"),
    PCLOUD("pCloud", "☁️", "pcloud")
}

// ─────────────────────────────────────────────────────────────────────────────
// OAuth2 configuration per provider
// IMPORTANT: Replace CLIENT_ID values with your own from each developer console
// ─────────────────────────────────────────────────────────────────────────────
private object OAuthConfig {
    const val REDIRECT_URI = "vyknifeswiss://oauth"

    // Google Drive — https://console.cloud.google.com
    val GDRIVE_CLIENT_ID  = BuildConfig.GDRIVE_CLIENT_ID ?: "YOUR_GDRIVE_CLIENT_ID.apps.googleusercontent.com"
    const val GDRIVE_AUTH_URL   = "https://accounts.google.com/o/oauth2/v2/auth"
    const val GDRIVE_TOKEN_URL  = "https://oauth2.googleapis.com/token"
    const val GDRIVE_SCOPE      = "https://www.googleapis.com/auth/drive.appdata"

    // OneDrive — https://portal.azure.com
    val ONEDRIVE_CLIENT_ID  = BuildConfig.ONEDRIVE_CLIENT_ID ?: "YOUR_ONEDRIVE_CLIENT_ID"
    const val ONEDRIVE_AUTH_URL   = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
    const val ONEDRIVE_TOKEN_URL  = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
    const val ONEDRIVE_SCOPE      = "Files.ReadWrite.AppFolder offline_access"

    // Dropbox — https://www.dropbox.com/developers/apps
    val DROPBOX_CLIENT_ID  = BuildConfig.DROPBOX_CLIENT_ID ?: "YOUR_DROPBOX_APP_KEY"
    const val DROPBOX_AUTH_URL   = "https://www.dropbox.com/oauth2/authorize"
    const val DROPBOX_TOKEN_URL  = "https://api.dropboxapi.com/oauth2/token"

    // pCloud — https://docs.pcloud.com
    val PCLOUD_CLIENT_ID  = BuildConfig.PCLOUD_CLIENT_ID ?: "YOUR_PCLOUD_CLIENT_ID"
    const val PCLOUD_AUTH_URL   = "https://my.pcloud.com/oauth2/authorize"
    const val PCLOUD_TOKEN_URL  = "https://api.pcloud.com/oauth2_token"
}

// ─────────────────────────────────────────────────────────────────────────────
// Token storage (EncryptedSharedPreferences — protected by Android Keystore)
// ─────────────────────────────────────────────────────────────────────────────
class CloudTokenStore(context: Context) {
    private val appContext = context.applicationContext

    fun saveAccessToken(provider: CloudProvider, token: String) =
        SecurePrefs.putString(appContext, "${provider.prefsKey}_access", token)

    fun getAccessToken(provider: CloudProvider): String? =
        SecurePrefs.getString(appContext, "${provider.prefsKey}_access", null)

    fun saveRefreshToken(provider: CloudProvider, token: String) =
        SecurePrefs.putString(appContext, "${provider.prefsKey}_refresh", token)

    fun getRefreshToken(provider: CloudProvider): String? =
        SecurePrefs.getString(appContext, "${provider.prefsKey}_refresh", null)

    fun saveEmail(provider: CloudProvider, email: String) =
        SecurePrefs.putString(appContext, "${provider.prefsKey}_email", email)

    fun getEmail(provider: CloudProvider): String? =
        SecurePrefs.getString(appContext, "${provider.prefsKey}_email", null)

    fun saveLastSync(provider: CloudProvider, ts: Long) =
        SecurePrefs.putLong(appContext, "${provider.prefsKey}_last_sync", ts)

    fun getLastSync(provider: CloudProvider): Long =
        SecurePrefs.getLong(appContext, "${provider.prefsKey}_last_sync", 0L)

    fun savePkceVerifier(provider: CloudProvider, verifier: String) =
        SecurePrefs.putString(appContext, "${provider.prefsKey}_pkce_verifier", verifier)

    fun getPkceVerifier(provider: CloudProvider): String? =
        SecurePrefs.getString(appContext, "${provider.prefsKey}_pkce_verifier", null)

    fun isConnected(provider: CloudProvider): Boolean =
        getAccessToken(provider) != null

    fun disconnect(provider: CloudProvider) {
        SecurePrefs.remove(appContext, "${provider.prefsKey}_access")
        SecurePrefs.remove(appContext, "${provider.prefsKey}_refresh")
        SecurePrefs.remove(appContext, "${provider.prefsKey}_email")
        SecurePrefs.remove(appContext, "${provider.prefsKey}_last_sync")
        SecurePrefs.remove(appContext, "${provider.prefsKey}_pkce_verifier")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PKCE helper
// ─────────────────────────────────────────────────────────────────────────────
object PkceHelper {
    fun generateVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun generateChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance(Res.get(R.string.sha_256)).digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CloudSyncManager — OAuth launch + token exchange + upload/download
// ─────────────────────────────────────────────────────────────────────────────
object CloudSyncManager {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val BACKUP_FILENAME = "vyknifeswiss_backup.json"
    private const val JSON_MIME = "application/json"

    // ── Launch OAuth2 in browser ──────────────────────────────────────────────
    fun launchOAuth(context: Context, provider: CloudProvider, tokenStore: CloudTokenStore) {
        val verifier = PkceHelper.generateVerifier()
        val challenge = PkceHelper.generateChallenge(verifier)
        tokenStore.savePkceVerifier(provider, verifier)

        val url = when (provider) {
            CloudProvider.GDRIVE -> Uri.parse(OAuthConfig.GDRIVE_AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", OAuthConfig.GDRIVE_CLIENT_ID)
                .appendQueryParameter("redirect_uri", OAuthConfig.REDIRECT_URI)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", OAuthConfig.GDRIVE_SCOPE)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("access_type", "offline")
                .appendQueryParameter("prompt", "consent")
                .build().toString()

            CloudProvider.ONEDRIVE -> Uri.parse(OAuthConfig.ONEDRIVE_AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", OAuthConfig.ONEDRIVE_CLIENT_ID)
                .appendQueryParameter("redirect_uri", OAuthConfig.REDIRECT_URI)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", OAuthConfig.ONEDRIVE_SCOPE)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .build().toString()

            CloudProvider.DROPBOX -> Uri.parse(OAuthConfig.DROPBOX_AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", OAuthConfig.DROPBOX_CLIENT_ID)
                .appendQueryParameter("redirect_uri", OAuthConfig.REDIRECT_URI)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("token_access_type", "offline")
                .build().toString()

            CloudProvider.PCLOUD -> Uri.parse(OAuthConfig.PCLOUD_AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", OAuthConfig.PCLOUD_CLIENT_ID)
                .appendQueryParameter("redirect_uri", OAuthConfig.REDIRECT_URI)
                .appendQueryParameter("response_type", "code")
                .build().toString()
        }

        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // ── Exchange code for token ───────────────────────────────────────────────
    suspend fun handleOAuthCallback(
        provider: CloudProvider,
        code: String,
        tokenStore: CloudTokenStore,
        onLog: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val verifier = tokenStore.getPkceVerifier(provider) ?: run {
                onLog(Res.get(R.string.erro_pkce_verifier_nao_encontrado_para_x, (provider.label)))
                return@withContext false
            }

            val bodyBuilder = FormBody.Builder()
                .add("code", code)
                .add("redirect_uri", OAuthConfig.REDIRECT_URI)
                .add("code_verifier", verifier)
                .add("grant_type", "authorization_code")

            val tokenUrl: String
            when (provider) {
                CloudProvider.GDRIVE -> {
                    tokenUrl = OAuthConfig.GDRIVE_TOKEN_URL
                    bodyBuilder.add("client_id", OAuthConfig.GDRIVE_CLIENT_ID)
                }
                CloudProvider.ONEDRIVE -> {
                    tokenUrl = OAuthConfig.ONEDRIVE_TOKEN_URL
                    bodyBuilder.add("client_id", OAuthConfig.ONEDRIVE_CLIENT_ID)
                }
                CloudProvider.DROPBOX -> {
                    tokenUrl = OAuthConfig.DROPBOX_TOKEN_URL
                    bodyBuilder.add("client_id", OAuthConfig.DROPBOX_CLIENT_ID)
                }
                CloudProvider.PCLOUD -> {
                    tokenUrl = OAuthConfig.PCLOUD_TOKEN_URL
                    bodyBuilder.add("client_id", OAuthConfig.PCLOUD_CLIENT_ID)
                }
            }

            val request = Request.Builder()
                .url(tokenUrl)
                .post(bodyBuilder.build())
                .build()

            val response = http.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                onLog(Res.get(R.string.erro_token_exchange_falhou_x_x, (response.code), body))
                return@withContext false
            }

            val json = JSONObject(body)
            val accessToken = json.optString("access_token", "")
            val refreshToken = json.optString("refresh_token", "")
            if (accessToken.isEmpty()) {
                onLog(Res.get(R.string.erro_access_token_vazio_na_resposta))
                return@withContext false
            }

            tokenStore.saveAccessToken(provider, accessToken)
            if (refreshToken.isNotEmpty()) tokenStore.saveRefreshToken(provider, refreshToken)
            onLog(Res.get(R.string.auth_x_conectado_com_sucesso, (provider.label)))
            true
        } catch (e: Exception) {
            onLog(Res.get(R.string.erro_x_3, (e.message)))
            false
        }
    }

    // ── Upload backup JSON ────────────────────────────────────────────────────
    suspend fun uploadBackup(
        provider: CloudProvider,
        jsonContent: String,
        tokenStore: CloudTokenStore,
        onLog: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val token = tokenStore.getAccessToken(provider) ?: run {
            onLog(Res.get(R.string.erro_nao_conectado_ao_x, (provider.label)))
            return@withContext false
        }

        try {
            onLog(Res.get(R.string.upload_enviando_backup_para_x, (provider.label)))
            val bytes = jsonContent.toByteArray()
            val result = when (provider) {
                CloudProvider.GDRIVE -> uploadGdrive(token, bytes, onLog)
                CloudProvider.ONEDRIVE -> uploadOneDrive(token, bytes, onLog)
                CloudProvider.DROPBOX -> uploadDropbox(token, bytes, onLog)
                CloudProvider.PCLOUD -> uploadPCloud(token, bytes, onLog)
            }
            if (result) {
                tokenStore.saveLastSync(provider, System.currentTimeMillis())
                onLog(Res.get(R.string.ok_backup_enviado_para_x_x_bytes, (provider.label), (bytes.size)))
            }
            result
        } catch (e: Exception) {
            onLog(Res.get(R.string.erro_upload_falhou_x, (e.message)))
            false
        }
    }

    // ── Download backup JSON ──────────────────────────────────────────────────
    suspend fun downloadBackup(
        provider: CloudProvider,
        tokenStore: CloudTokenStore,
        onLog: (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val token = tokenStore.getAccessToken(provider) ?: run {
            onLog(Res.get(R.string.erro_nao_conectado_ao_x, (provider.label)))
            return@withContext null
        }
        try {
            onLog(Res.get(R.string.download_baixando_backup_do_x, (provider.label)))
            val content = when (provider) {
                CloudProvider.GDRIVE -> downloadGdrive(token, onLog)
                CloudProvider.ONEDRIVE -> downloadOneDrive(token, onLog)
                CloudProvider.DROPBOX -> downloadDropbox(token, onLog)
                CloudProvider.PCLOUD -> downloadPCloud(token, onLog)
            }
            if (content != null) onLog(Res.get(R.string.ok_arquivo_baixado_x_bytes, (content.toByteArray().size)))
            content
        } catch (e: Exception) {
            onLog(Res.get(R.string.erro_download_falhou_x, (e.message)))
            null
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Google Drive (App Data folder)
    // ────────────────────────────────────────────────────────────────────────
    private fun uploadGdrive(token: String, bytes: ByteArray, onLog: (String) -> Unit): Boolean {
        // 1. Check if file exists — list appDataFolder
        val listReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='$BACKUP_FILENAME'&fields=files(id)")
            .header("Authorization", "Bearer $token")
            .get().build()
        val listResp = http.newCall(listReq).execute()
        val listBody = listResp.body?.string() ?: "{}"
        val files = JSONObject(listBody).optJSONArray("files")
        val existingId = if (files != null && files.length() > 0) files.getJSONObject(0).optString("id") else null

        return if (existingId != null) {
            // PATCH — update existing
            val patchReq = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$existingId?uploadType=media")
                .header("Authorization", "Bearer $token")
                .patch(bytes.toRequestBody(JSON_MIME.toMediaType()))
                .build()
            val resp = http.newCall(patchReq).execute()
            onLog(Res.get(R.string.gdrive_arquivo_atualizado_id_x_status_x, existingId, (resp.code)))
            resp.isSuccessful
        } else {
            // POST multipart — create in appDataFolder
            val metaJson = """{"name":"$BACKUP_FILENAME","parents":["appDataFolder"]}"""
            val boundary = "----WB${System.currentTimeMillis()}"
            val multipart = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metaJson\r\n--$boundary\r\nContent-Type: $JSON_MIME\r\n\r\n${String(bytes)}\r\n--$boundary--".toByteArray()
            val postReq = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $token")
                .post(multipart.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                .build()
            val resp = http.newCall(postReq).execute()
            onLog(Res.get(R.string.gdrive_arquivo_criado_status_x, (resp.code)))
            resp.isSuccessful
        }
    }

    private fun downloadGdrive(token: String, onLog: (String) -> Unit): String? {
        val listReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='$BACKUP_FILENAME'&fields=files(id)")
            .header("Authorization", "Bearer $token")
            .get().build()
        val listBody = http.newCall(listReq).execute().body?.string() ?: return null
        val files = JSONObject(listBody).optJSONArray("files")
        if (files == null || files.length() == 0) { onLog(Res.get(R.string.gdrive_nenhum_backup_encontrado)); return null }
        val fileId = files.getJSONObject(0).optString("id")
        val dlReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .get().build()
        return http.newCall(dlReq).execute().body?.string()
    }

    // ────────────────────────────────────────────────────────────────────────
    // OneDrive (Apps special folder)
    // ────────────────────────────────────────────────────────────────────────
    private fun uploadOneDrive(token: String, bytes: ByteArray, onLog: (String) -> Unit): Boolean {
        val req = Request.Builder()
            .url("https://graph.microsoft.com/v1.0/me/drive/special/approot:/$BACKUP_FILENAME:/content")
            .header("Authorization", "Bearer $token")
            .put(bytes.toRequestBody(JSON_MIME.toMediaType()))
            .build()
        val resp = http.newCall(req).execute()
        onLog(Res.get(R.string.onedrive_upload_status_x, (resp.code)))
        return resp.isSuccessful
    }

    private fun downloadOneDrive(token: String, onLog: (String) -> Unit): String? {
        val metaReq = Request.Builder()
            .url("https://graph.microsoft.com/v1.0/me/drive/special/approot:/$BACKUP_FILENAME")
            .header("Authorization", "Bearer $token")
            .get().build()
        val metaResp = http.newCall(metaReq).execute()
        if (!metaResp.isSuccessful) { onLog(Res.get(R.string.onedrive_backup_nao_encontrado_x, (metaResp.code))); return null }
        val downloadUrl = JSONObject(metaResp.body?.string() ?: "").optString("@microsoft.graph.downloadUrl", "")
        if (downloadUrl.isEmpty()) return null
        return http.newCall(Request.Builder().url(downloadUrl).get().build()).execute().body?.string()
    }

    // ────────────────────────────────────────────────────────────────────────
    // Dropbox
    // ────────────────────────────────────────────────────────────────────────
    private fun uploadDropbox(token: String, bytes: ByteArray, onLog: (String) -> Unit): Boolean {
        val args = """{"path":"/$BACKUP_FILENAME","mode":"overwrite","autorename":false,"mute":false}"""
        val req = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/upload")
            .header("Authorization", "Bearer $token")
            .header("Dropbox-API-Arg", args)
            .post(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        val resp = http.newCall(req).execute()
        onLog(Res.get(R.string.dropbox_upload_status_x, (resp.code)))
        return resp.isSuccessful
    }

    private fun downloadDropbox(token: String, onLog: (String) -> Unit): String? {
        val args = """{"path":"/$BACKUP_FILENAME"}"""
        val req = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/download")
            .header("Authorization", "Bearer $token")
            .header("Dropbox-API-Arg", args)
            .post("".toRequestBody(null))
            .build()
        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) { onLog(Res.get(R.string.dropbox_backup_nao_encontrado_x, (resp.code))); return null }
        return resp.body?.string()
    }

    // ────────────────────────────────────────────────────────────────────────
    // pCloud
    // ────────────────────────────────────────────────────────────────────────
    private fun uploadPCloud(token: String, bytes: ByteArray, onLog: (String) -> Unit): Boolean {
        val req = Request.Builder()
            .url("https://api.pcloud.com/uploadfile?filename=$BACKUP_FILENAME&folderid=0&nopartial=1")
            .header("Authorization", "Bearer $token")
            .post(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        val resp = http.newCall(req).execute()
        onLog(Res.get(R.string.pcloud_upload_status_x, (resp.code)))
        return resp.isSuccessful
    }

    private fun downloadPCloud(token: String, onLog: (String) -> Unit): String? {
        val listReq = Request.Builder()
            .url("https://api.pcloud.com/listfolder?folderid=0&recursive=0")
            .header("Authorization", "Bearer $token")
            .get().build()
        val listBody = http.newCall(listReq).execute().body?.string() ?: return null
        val contents = JSONObject(listBody).optJSONObject("metadata")?.optJSONArray("contents")
        var fileId: Long? = null
        if (contents != null) {
            for (i in 0 until contents.length()) {
                val item = contents.getJSONObject(i)
                if (item.optString("name") == BACKUP_FILENAME) {
                    fileId = item.optLong("fileid")
                    break
                }
            }
        }
        if (fileId == null) { onLog(Res.get(R.string.pcloud_backup_nao_encontrado)); return null }
        val linkReq = Request.Builder()
            .url("https://api.pcloud.com/getfilelink?fileid=$fileId")
            .header("Authorization", "Bearer $token")
            .get().build()
        val linkBody = JSONObject(http.newCall(linkReq).execute().body?.string() ?: return null)
        val host = linkBody.optJSONArray("hosts")?.optString(0) ?: return null
        val path = linkBody.optString("path")
        return http.newCall(Request.Builder().url("https://$host$path").get().build()).execute().body?.string()
    }
}
