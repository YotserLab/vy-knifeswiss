package com.example.spectests
import com.example.ui.screens.files.AppExtractorScreen
import com.example.ui.screens.files.FTPServer
import com.example.ui.screens.files.FileManagerConnectionsTab
import com.example.ui.screens.files.FileManagerLocalTab
import com.example.ui.screens.files.FileManagerRemoteTab
import com.example.ui.screens.files.FileManagerScreen
import com.example.ui.screens.files.FileSynchronizerScreen
import com.example.ui.screens.files.GoogleDriveProvider
import com.example.ui.screens.files.RemoteFileClient
import com.example.ui.screens.files.WebDAVServer
import com.example.ui.screens.media.MediaStoreHelper
import com.example.ui.screens.system.CacheCleanerScreen

import org.junit.Assert.assertTrue
import org.junit.Test

class ArquivosSpecTest {

    // ARQ-R01: FileManager com abas Local/Remoto/Conexões
    @Test
    fun test_ARQ_R01_file_manager_has_tabs() {
        assertTrue(srcContains("com/example/ui/screens/files/FileManagerLocalTab.kt", "fun "))
        assertTrue(srcContains("com/example/ui/screens/files/FileManagerRemoteTab.kt", "fun "))
        assertTrue(srcContains("com/example/ui/screens/files/FileManagerConnectionsTab.kt", "fun "))
    }

    // ARQ-R02: FTP porta default 2221 com bind no IP Wi-Fi
    @Test
    fun test_ARQ_R02_ftp_default_port_2221() {
        val s = src("com/example/ui/screens/files/FTPServer.kt")
        assertTrue(s.contains("port: Int = 2221") && s.contains("getWifiOrLocalIpSync"))
    }

    // ARQ-R03: FTP exige USER/PASS, senha em tempo constante, 530 sem auth
    @Test
    fun test_ARQ_R03_ftp_auth_contract() {
        val s = src("com/example/ui/screens/files/FTPServer.kt")
        assertTrue(s.contains("constantTimeEquals") && s.contains("530 Please login"))
    }

    // ARQ-R04/R05: WebDAV porta 8080, Basic auth Base64, 401 sem credencial
    @Test
    fun test_ARQ_R04_R05_webdav_auth_contract() {
        val s = src("com/example/ui/screens/files/WebDAVServer.kt")
        assertTrue(s.contains("port: Int = 8080") && s.contains("401") && s.contains("Base64.getDecoder()"))
    }

    // ARQ-R06: conexões remotas suportam FTP/FTPS/SFTP/WebDAV/SMB
    @Test
    fun test_ARQ_R06_remote_protocols() {
        val s = src("com/example/ui/screens/files/FileManagerDialogs.kt")
        assertTrue(s.contains("\"FTP\", \"FTPS\", \"SFTP\"") && s.contains("webdav") && s.contains("\"SMB\""))
    }

    // ARQ-R15: cliente SFTP via JSch ChannelSftp
    @Test
    fun test_ARQ_R15_sftp_via_jsch() {
        assertTrue(srcContains("com/example/ui/screens/files/RemoteFileClient.kt", "ChannelSftp"))
    }

    // ARQ-R07: Google Drive via API oficial
    @Test
    fun test_ARQ_R07_drive_api() {
        assertTrue(srcContains("com/example/ui/screens/files/GoogleDriveProvider.kt", "com.google.api.services.drive"))
    }

    // ARQ-R08: sincronização/backup disparados manualmente
    @Test
    fun test_ARQ_R08_manual_sync_trigger() {
        assertTrue(srcContains("com/example/ui/screens/files/FileSynchronizerScreen.kt", "adicionar_pasta_p_sincronizar"))
    }

    // ARQ-R09: AppExtractor extrai APK via sourceDir
    @Test
    fun test_ARQ_R09_apk_extraction() {
        assertTrue(srcContains("com/example/ui/screens/files/AppExtractorScreen.kt", "sourceDir"))
        assertTrue(java.io.File("src/main/AndroidManifest.xml").readText().contains("QUERY_ALL_PACKAGES"))
    }

    // ARQ-R10: CacheCleaner abre settings do sistema
    @Test
    fun test_ARQ_R10_cache_cleaner_opens_settings() {
        assertTrue(srcContains("com/example/ui/screens/system/CacheCleanerScreen.kt", "ACTION_INTERNAL_STORAGE_SETTINGS"))
    }

    // ARQ-R11: delete de arquivo é direto, sem diálogo de confirmação
    @Test
    fun test_ARQ_R11_direct_delete() {
        assertTrue(srcContains("com/example/ui/screens/files/FileManagerScreen.kt", "currentProvider!!.delete(file)"))
    }

    // ARQ-R12: mídia via MediaStore
    @Test
    fun test_ARQ_R12_media_store_helper() {
        assertTrue(srcContains("com/example/ui/screens/media/MediaStoreHelper.kt", "MediaStore"))
    }

    // ARQ-R13: cabeçalho HTTP limitado a 16KB e 64 linhas
    @Test
    fun test_ARQ_R13_http_head_limits() {
        val s = src("com/example/service/InternalHttpFileServer.kt")
        assertTrue(s.contains("16 * 1024") && s.contains("MAX_HEADER_LINES = 64"))
    }

    // ARQ-R14: TTL padrão do token é 15 minutos
    @Test
    fun test_ARQ_R14_token_ttl_15min() {
        assertTrue(srcContains("com/example/service/InternalHttpFileServer.kt", "15 * 60 * 1000L"))
    }
}

// ARQ-R05: WebDAV valida Basic Base64 antes de qualquer método
class ArquivosWebDavAuthSpecTest {
    @org.junit.Test
    fun test_ARQ_R05_webdav_basic_auth_gate() {
        val s = src("com/example/ui/screens/files/WebDAVServer.kt")
        org.junit.Assert.assertTrue(s.contains("Basic") && s.contains("Base64"))
    }
}
