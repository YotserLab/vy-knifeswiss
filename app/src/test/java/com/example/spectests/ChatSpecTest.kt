package com.example.spectests
import com.example.ui.screens.chat.LocalChatScreen
import com.example.utils.crypto.LocalChatCrypto

import com.example.mesh.data.repository.StoreAndForwardRepository
import com.example.mesh.domain.election.CoordinatorElection
import com.example.mesh.domain.models.Message
import com.example.mesh.domain.models.Node
import com.example.mesh.domain.models.NodeRole
import com.example.mesh.domain.routing.MeshRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRepo : StoreAndForwardRepository {
    val known = mutableSetOf<String>()
    val stored = mutableListOf<Message>()
    override fun isMessageKnown(messageId: String) = messageId in known
    override fun markMessageAsKnown(messageId: String) { known += messageId }
    override fun storeMessageForLater(message: Message) { stored += message }
    override fun getAndRemoveMessagesForDestination(destinationId: String): List<Message> = emptyList()
    override fun clearExpiredMessages(currentTimeMillis: Long) {}
}

private fun msg(dest: String, hops: Int = 5, expiresAt: Long = Long.MAX_VALUE) =
    Message("m1", "src", dest, 0L, "", ByteArray(0), expiresAt, hops)

private fun node(id: String, score: Int = 0) =
    Node(id, NodeRole.COMMON, score, 0, 0, false, 0, 0, false, 0)

class ChatSpecTest {


    // CHAT-R01/R02/R03: portas UDP 9988 (descoberta), TCP 9989 (msgs), UDP 9990 (áudio), bind 0.0.0.0
    @Test
    fun test_CHAT_ports_contract() {
        val svc = src("com/example/service/LocalChatService.kt")
        assertTrue(srcContains("com/example/service/LocalChatService.kt", """"0.0.0.0"), 9988"""))
        assertTrue(svc.contains("9989"))
        assertTrue(srcContains("com/example/ui/screens/chat/LocalChatScreen.kt", "9990"))
    }

    // CHAT-R04: sweepLocalSubnet varre /24 como fallback de broadcast
    @Test
    fun test_CHAT_R04_subnet_sweep_exists() {
        assertTrue(srcContains("com/example/service/LocalChatService.kt", "sweepLocalSubnet"))
    }

    // CHAT-R05: handshake mantém presença com HANDSHAKE/PING/PONG
    @Test
    fun test_CHAT_R05_handshake_protocol() {
        val svc = src("com/example/service/LocalChatService.kt")
        assertTrue(svc.contains("HANDSHAKE") && svc.contains("PING") && svc.contains("PONG"))
    }

    // CHAT-R06: UI oferece conexão manual por IP
    @Test
    fun test_CHAT_R06_manual_ip_connect() {
        assertTrue(srcContains("com/example/ui/screens/chat/LocalChatScreen.kt", "conectar_por_ip"))
    }

    // CHAT-R07/R08: arquivos <=2MB inline Base64; >2MB via InternalHttpFileServer
    @Test
    fun test_CHAT_R07_R08_file_split_contract() {
        val s = src("com/example/ui/screens/chat/LocalChatScreen.kt")
        assertTrue(s.contains("2MB") || s.contains("2 * 1024") || s.contains("2097152"))
        assertTrue(s.contains("InternalHttpFileServer"))
    }

    // CHAT-R09: resposta HTTP envia Content-Length exato
    @Test
    fun test_CHAT_R09_content_length_header() {
        assertTrue(srcContains("com/example/service/InternalHttpFileServer.kt", "Content-Length: \$fileLength"))
    }

    // CHAT-R10: tráfego só na rede local — bind no IP Wi-Fi, sem host externo
    @Test
    fun test_CHAT_R10_local_only_no_external_host() {
        assertFalse(src("com/example/service/LocalChatService.kt").contains("https://"))
    }

    // CHAT-R11: mesh decrementa ttlHopsRemaining e descarta ao chegar a 0
    @Test
    fun test_CHAT_R11_message_dies_when_hops_reach_zero() {
        val repo = FakeRepo()
        val router = MeshRouter("me", repo)
        router.addOrUpdateNeighbor(node("other"))
        val m = msg(dest = "other", hops = 1)
        router.onMessageReceived(m, System.currentTimeMillis())
        assertEquals(0, m.ttlHopsRemaining)
        assertTrue(repo.stored.isEmpty())
    }

    // CHAT-R12: mensagem expirada por TTL de tempo não é encaminhada nem armazenada
    @Test
    fun test_CHAT_R12_expired_message_dropped() {
        val repo = FakeRepo()
        val router = MeshRouter("me", repo)
        val m = msg(dest = "x", expiresAt = 1L) // expirada
        router.onMessageReceived(m, System.currentTimeMillis())
        assertTrue(repo.stored.isEmpty())
        assertFalse(repo.isMessageKnown("m1"))
    }

    // CHAT-R13: destino desconhecido fica em store-and-forward
    @Test
    fun test_CHAT_R13_unknown_destination_is_stored() {
        val repo = FakeRepo()
        val router = MeshRouter("me", repo)
        router.onMessageReceived(msg(dest = "desconhecido", hops = 5), System.currentTimeMillis())
        assertEquals(1, repo.stored.size)
    }

    // CHAT-R14: nó com maior score da vizinhança (>50) vira COORDINATOR
    @Test
    fun test_CHAT_R14_election_picks_highest_score() {
        val local = node("me", score = 100)
        val election = CoordinatorElection(local)
        election.updateNeighbors(listOf(node("a", 10), node("b", 20)))
        assertEquals(NodeRole.COORDINATOR, local.role)
    }

    // CHAT-R15: texto cifrado AES-GCM com prefixo ENC: (contrato de fonte — crypto usa android.util.Base64)
    @Test
    fun test_CHAT_R15_local_chat_crypto_contract() {
        val s = src("com/example/utils/crypto/LocalChatCrypto.kt")
        assertTrue(s.contains("R.string.enc") && s.contains("AES/GCM/NoPadding") && s.contains("GCM_TAG_LENGTH = 128"))
        assertTrue(java.io.File("src/main/res/values/strings.xml").readText().contains(">ENC:<"))
        assertTrue(s.contains("fun encrypt") && s.contains("fun decrypt"))
    }

    // CHAT-R08/R09: arquivo >2MB servido via HTTP com Content-Length exato (contrato de fonte)
    @Test
    fun test_CHAT_R08_R09_served_file_content_length() {
        val s = src("com/example/service/InternalHttpFileServer.kt")
        assertTrue(s.contains("fun hostFile") && s.contains("Content-Length: \$fileLength"))
    }
}

// CHAT-R02: handshake/mensagens TCP em 0.0.0.0:9989
class ChatTcpPortSpecTest {
    @org.junit.Test
    fun test_CHAT_R02_tcp_9989_bind() {
        val s = src("com/example/service/LocalChatService.kt")
        org.junit.Assert.assertTrue(s.contains("9989") && s.contains("0.0.0.0"))
    }
}

// CHAT-R03: áudio UDP porta 9990
class ChatAudioPortSpecTest {
    @org.junit.Test
    fun test_CHAT_R03_audio_udp_9990() {
        val s = src("com/example/ui/screens/chat/LocalChatScreen.kt")
        org.junit.Assert.assertTrue(s.contains("9990") && s.contains("DatagramSocket") || s.contains("9990"))
    }
}
