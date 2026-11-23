package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object WifiMonitor : Screen("wifi_monitor")
    object PortScanner : Screen("port_scanner")
    object NetworkTest : Screen("network_test")
    object SubnetScanner : Screen("subnet_scanner")
    object SpeedTest : Screen("speed_test")
    object BubbleLevel : Screen("bubble_level")
    object Compass : Screen("compass")
    object History : Screen("history")
    object Hp48gCalculator : Screen("hp48g_calculator")
    object CpuZ : Screen("device_info")
    object DeviceInfo : Screen("device_info")
    object FileManager : Screen("file_manager")
    object Terminal : Screen("terminal")
    object QuickNotesKanban : Screen("quick_notes_kanban")
    object CalendarAlarms : Screen("calendar_alarms")
    object BackupSync : Screen("backup_sync")
    object NetworkInfo : Screen("network_info")
    object QrCode : Screen("qr_code")
    object LocalChat : Screen("local_chat")
    object MeshInfo : Screen("mesh_info")
    object Notes : Screen("notes")
    object AudioRecorder : Screen("audio_recorder")
    object OfflineTranscription : Screen("offline_transcription")
    object SmsManager : Screen("sms_manager")
    object CacheCleaner : Screen("cache_cleaner")
    object FileSynchronizer : Screen("file_synchronizer")
    object ScreenRecorder : Screen("screen_recorder")
    object MediaManager : Screen("media_manager")
    object DevSettings : Screen("dev_settings")
    object Settings : Screen("settings")
    object PowerOffProtection : Screen("power_off_protection")
    object DeviceSecurity : Screen("device_security")
    object About : Screen("about")
    object ActivityLauncher : Screen("activity_launcher")
    object Whiteboard : Screen("whiteboard")
    object PriceCalculator : Screen("price_calculator")
    object ShoppingList : Screen("shopping_list")
    object CalorieCalculator : Screen("calorie_calculator")
    object Obd2Scanner : Screen("obd2_scanner")
    object PasswordVault : Screen("password_vault")
    object TotpAuthenticator : Screen("totp_authenticator")
    object MediaPlayer : Screen("media_player") {
        fun withPath(path: String) = "media_player?path=${java.net.URLEncoder.encode(path, "UTF-8")}"
    }
    object TextEditor : Screen("text_editor") {
        fun withPath(path: String) = "text_editor?path=${java.net.URLEncoder.encode(path, "UTF-8")}"
    }
    object PluginManager : Screen("plugin_manager")
    object CastToTV : Screen("cast_to_tv")
    object MiniAppStore : Screen("miniapp_store")
    object MiniAppRuntime : Screen("miniapp/{appId}") {
        fun createRoute(appId: String) = "miniapp/$appId"
    }

    // --- DROIDLINK REMOTE ---
    object DroidLinkRemote  : Screen("droidlink_remote")
    object DroidLinkServer  : Screen("droidlink_server")
    object DroidLinkConnect : Screen("droidlink_connect")
    object DroidLinkConsole : Screen("droidlink_console")
}
