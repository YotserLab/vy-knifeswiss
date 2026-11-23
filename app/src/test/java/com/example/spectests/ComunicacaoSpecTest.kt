package com.example.spectests
import com.example.ui.screens.system.QrCodeScreen
import com.example.ui.screens.system.SmsManagerScreen

import org.junit.Assert.assertTrue
import org.junit.Test

class ComunicacaoSpecTest {

    // COM-R01: geração de QR via ZXing QRCodeWriter
    @Test
    fun test_COM_R01_zxing_qr_writer() {
        assertTrue(srcContains("com/example/ui/screens/system/QrCodeScreen.kt", "QRCodeWriter"))
    }

    // COM-R02: leitura de QR/barras via ML Kit
    @Test
    fun test_COM_R02_mlkit_scanner() {
        assertTrue(srcContains("com/example/ui/screens/system/QrCodeScreen.kt", "BarcodeScanning.getClient"))
    }

    // COM-R03/R04: SMS completo só com ROLE_SMS; sem papel, solicita via RoleManager
    @Test
    fun test_COM_R03_R04_sms_role() {
        val s = src("com/example/ui/screens/system/SmsManagerScreen.kt")
        assertTrue(s.contains("ROLE_SMS") && s.contains("createRequestRoleIntent"))
    }

    // COM-R05: SMS recebidos via SmsReceiver
    @Test
    fun test_COM_R05_sms_receiver_declared() {
        assertTrue(srcContains("com/example/receiver/SmsReceiver.kt", "BroadcastReceiver"))
        assertTrue(java.io.File("src/main/AndroidManifest.xml").readText().contains("SmsReceiver"))
    }

    // COM-R06: envio via HeadlessSmsSendService
    @Test
    fun test_COM_R06_headless_send_service() {
        assertTrue(srcContains("com/example/service/HeadlessSmsSendService.kt", "Service"))
        assertTrue(java.io.File("src/main/AndroidManifest.xml").readText().contains("HeadlessSmsSendService"))
    }

    // COM-R07: e-mail via API Mailtrap
    @Test
    fun test_COM_R07_mailtrap_endpoint() {
        assertTrue(srcContains("com/example/service/MailtrapEmailService.kt", "send.api.mailtrap.io/api/send"))
    }

    // COM-R08: MMS via MmsReceiver
    @Test
    fun test_COM_R08_mms_receiver() {
        assertTrue(srcContains("com/example/receiver/MmsReceiver.kt", "BroadcastReceiver"))
    }

    // COM-R09: SmsComposeActivity para papel de app SMS padrão
    @Test
    fun test_COM_R09_sms_compose_activity() {
        assertTrue(srcContains("com/example/SmsComposeActivity.kt", "Activity"))
        assertTrue(java.io.File("src/main/AndroidManifest.xml").readText().contains("SmsComposeActivity"))
    }
}

// COM-R04: sem ROLE_SMS, tela oferece solicitar o papel via RoleManager
class ComunicacaoRoleRequestSpecTest {
    @org.junit.Test
    fun test_COM_R04_role_request_intent() {
        val s = src("com/example/ui/screens/system/SmsManagerScreen.kt")
        org.junit.Assert.assertTrue(s.contains("createRequestRoleIntent") && s.contains("isRoleAvailable"))
    }
}
