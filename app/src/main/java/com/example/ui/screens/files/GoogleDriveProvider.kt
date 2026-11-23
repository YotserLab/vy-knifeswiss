package com.example.ui.screens.files

import android.content.Context
import com.example.ui.screens.filemanager.FileSystemProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.example.R
import com.example.Res

class GoogleDriveProvider(private val context: Context) : FileSystemProvider {

    override val connectionName: String = Res.get(R.string.google_drive)

    private var driveService: Drive? = null
    var account: GoogleSignInAccount? = null

    init {
        account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            setupDriveService(account!!)
        }
    }

    private fun setupDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_READONLY))
        credential.selectedAccount = account.account

        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName(Res.get(R.string.vyknifeswiss)).build()
    }

    val signInClient: GoogleSignInClient
        get() {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_READONLY))
                .build()
            return GoogleSignIn.getClient(context, gso)
        }

    fun onSignInSuccess(account: GoogleSignInAccount) {
        this.account = account
        setupDriveService(account)
    }

    override suspend fun listFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        if (driveService == null) throw Exception(Res.get(R.string.nao_autenticado_no_google_drive))
        
        // On Drive, 'path' is the folder ID. We use 'root' as the root.
        val folderId = if (path == "/" || path.isEmpty()) "root" else path
        
        val result = mutableListOf<FileItem>()
        try {
            val fileList = driveService!!.files().list() // ARQ-R07
                .setQ("'$folderId' in parents and trashed = false")
                .setFields("files(id, name, mimeType, size, modifiedTime)")
                .execute()

            for (f in fileList.files) {
                val isDir = f.mimeType == "application/vnd.google-apps.folder"
                result.add(
                    FileItem(
                        name = f.name,
                        path = f.id, // In Drive the path is the file ID
                        size = if (isDir) "" else formatSize(f.getSize() ?: 0L),
                        type = getFileTypeFromName(f.name, isDir),
                        isLocal = false,
                        lastModified = f.modifiedTime?.value ?: 0L,
                        rawSize = f.getSize() ?: 0L
                    )
                )
            }
            // Sort: folders first
            result.sortedWith(compareBy({ it.type != FileType.DIRECTORY }, { it.name.lowercase() }))
        } catch (e: Exception) {
            throw Exception(Res.get(R.string.erro_ao_listar_drive_x, (e.message)))
        }
    }

    override suspend fun delete(file: FileItem): Boolean = withContext(Dispatchers.IO) {
        if (driveService == null) return@withContext false
        return@withContext try {
            driveService!!.files().delete(file.path).execute()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Size-formatting utility (duplicated from FileManagerComponents for isolation)
    private fun formatSize(size: Long): String {
        if (size <= 0) return Res.get(R.string.n_0_b)
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(java.util.Locale.US, Res.get(R.string.n_1f_s), size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
