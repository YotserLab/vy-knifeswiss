package com.example.service

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Harness manual para fuzzing do InternalHttpFileServer no host (JVM).
 * Só executa quando existe /tmp/vy_fuzz_enable (senão travaria a suíte):
 *   touch /tmp/vy_fuzz_enable
 *   ./gradlew :app:testDebugUnitTest --tests '*FuzzHarnessTest*'
 * Sobe o server real, publica PORT/TOKEN em /tmp/vy_fuzz_server e segura
 * o processo — rode scripts/fuzz_http_server.py contra 127.0.0.1:PORT.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FuzzHarnessTest {

    @Test
    fun fuzz_harness_hold_server_alive() {
        if (!File("/tmp/vy_fuzz_enable").exists()) return
        InternalHttpFileServer.startServer(ApplicationProvider.getApplicationContext<Application>())
        val file = File.createTempFile("fuzz_serve", ".txt").apply {
            writeText("VYKnifeSwiss fuzz target body 0123456789")
            deleteOnExit()
        }
        val token = InternalHttpFileServer.hostFile(
            file,
            mimeType = "text/plain",
            ttlMs = 15 * 60_000L,
        )
        File("/tmp/vy_fuzz_server").writeText(
            "PORT=${InternalHttpFileServer.port}\nTOKEN=$token\n"
        )
        CountDownLatch(1).await(10, TimeUnit.MINUTES)
    }
}
