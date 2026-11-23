package com.example.spectests
import com.example.ui.screens.media.AudioRecorderScreen
import com.example.ui.screens.media.MediaPlayerScreen
import com.example.ui.screens.media.TranscriptionScreen

import com.example.cast.control.ChromecastSession
import com.example.cast.control.RokuEcp
import com.example.cast.control.RokuSession
import com.example.cast.control.UpnpSoap
import com.example.cast.discovery.CastDevice
import com.example.cast.discovery.CastDeviceType
import com.example.cast.media.CastMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiaSpecTest {

    // MED-R01: player usa android.media.MediaPlayer
    @Test
    fun test_MED_R01_media_player_native() {
        assertTrue(srcContains("com/example/ui/screens/media/MediaPlayerScreen.kt", "android.media.MediaPlayer"))
    }

    // MED-R02: gravação gera REC_yyyyMMdd_HHmmss.m4a em 44,1kHz
    @Test
    fun test_MED_R02_audio_recording_contract() {
        val s = src("com/example/service/AudioRecordingService.kt")
        assertTrue(s.contains("REC_") && s.contains(".m4a") && s.contains("44100"))
    }

    // MED-R03: serviço de gravação é foreground do tipo microphone
    @Test
    fun test_MED_R03_foreground_microphone_service() {
        assertTrue(srcContains("com/example/service/AudioRecordingService.kt", "FOREGROUND_SERVICE_TYPE_MICROPHONE"))
    }

    // MED-R04: screen recorder usa MediaProjection e gera SCREEN_*.mp4
    @Test
    fun test_MED_R04_screen_recorder_contract() {
        val s = src("com/example/service/ScreenRecordingService.kt")
        assertTrue(s.contains("MediaProjection") && s.contains("SCREEN_") && s.contains(".mp4"))
    }

    // MED-R05: biblioteca lista mp3/m4a/aac/wav
    @Test
    fun test_MED_R05_audio_library_extensions() {
        assertTrue(srcContains("com/example/ui/screens/media/AudioRecorderScreen.kt", "\"mp3\", \"m4a\", \"aac\", \"wav\""))
    }

    // MED-R06: transcrição pede reconhecimento preferencialmente offline
    @Test
    fun test_MED_R06_offline_speech_recognizer() {
        assertTrue(srcContains("com/example/ui/screens/media/TranscriptionScreen.kt", "EXTRA_PREFER_OFFLINE"))
    }

    // MED-R07: descoberta cobre SSDP, mDNS e probe Roku
    @Test
    fun test_MED_R07_discovery_mechanisms_exist() {
        assertTrue(srcContains("com/example/cast/discovery/SsdpDiscovery.kt", "SSDP") || srcContains("com/example/cast/discovery/SsdpDiscovery.kt", "239.255.255.250"))
        assertTrue(srcContains("com/example/cast/discovery/RokuProbe.kt", "8060"))
        assertTrue(srcContains("com/example/cast/discovery/MdnsCastDiscovery.kt", "mdns") || srcContains("com/example/cast/discovery/MdnsCastDiscovery.kt", "_googlecast"))
    }

    // MED-R08: TTL do token = duração + 30min, piso 4h
    @Test
    fun test_MED_R08_served_token_ttl_formula() {
        assertTrue(srcContains("com/example/cast/media/CastMediaServer.kt", "30 * 60_000L, 4 * 60 * 60_000L"))
    }

    // MED-R09: poll de posição renova o token servido
    @Test
    fun test_MED_R09_token_renewed_on_poll() {
        assertTrue(srcContains("com/example/cast/control/CastSessionManager.kt", "InternalHttpFileServer.renew"))
    }

    // MED-R10: envelopes SOAP UPnP — escape XML, hms<->ms, DIDL por tipo de mídia
    @Test
    fun test_MED_R10_upnp_soap_envelope_and_escape() {
        val env = UpnpSoap.envelope("SetAVTransportURI", "urn:schemas-upnp-org:service:AVTransport:1",
            mapOf("CurrentURI" to UpnpSoap.escape("http://x/y<&\"")))
        assertTrue(env.contains("SetAVTransportURI"))
        assertFalse(env.contains("y<&\""))
        assertEquals("&lt;&amp;", UpnpSoap.escape("<&"))
        assertEquals(90_000L, UpnpSoap.parseHmsToMs("0:01:30"))
        assertEquals("0:01:30", UpnpSoap.msToHms(90_000L))
        assertTrue(UpnpSoap.didlLite("t", "u", "video/mp4", CastMedia.Kind.VIDEO, 0).contains("videoItem"))
        assertTrue(UpnpSoap.didlLite("t", "u", "audio/mp4", CastMedia.Kind.AUDIO, 0).contains("musicTrack"))
        assertEquals(75, UpnpSoap.parseVolume("<CurrentVolume>75</CurrentVolume>"))
    }

    // MED-R11: Roku ECP sem seek nem volume; launch path com tipo de mídia
    @Test
    fun test_MED_R11_roku_no_seek_no_volume() {
        val dev = CastDevice("id", "Roku TV", CastDeviceType.ROKU, "192.168.0.9", port = 8060)
        val session = RokuSession(dev)
        assertFalse(session.supportsSeek)
        assertFalse(session.supportsVolume)
        assertFalse(session.supportsPositionInfo)
        val path = RokuEcp.launchPath(CastMedia.Kind.VIDEO, "http://x/v.mp4", "título")
        assertTrue(path.startsWith("/launch/15985?t=v"))
    }

    // MED-R12: Google Cast SDK fixado em 21.5.0
    @Test
    fun test_MED_R12_cast_sdk_pinned_21_5_0() {
        assertTrue(java.io.File("../gradle/libs.versions.toml").readText().contains("playServicesCastFramework = \"21.5.0\""))
    }

    // MED-R13: match por friendly name normalizado; vazio não casa
    @Test
    fun test_MED_R13_friendly_name_matching() {
        assertTrue(ChromecastSession.matchesDevice("Living-Room TV!", "living room tv"))
        assertTrue(ChromecastSession.matchesDevice("QUARTO", "quarto"))
        assertFalse(ChromecastSession.matchesDevice(null, "tv"))
        assertFalse(ChromecastSession.matchesDevice("Sala", "Quarto"))
        assertFalse(ChromecastSession.matchesDevice("!!!", "tv"))
    }

    // MED-R14: ao carregar no cast, player local pausa
    @Test
    fun test_MED_R14_local_player_pauses_on_cast() {
        val s = src("com/example/ui/screens/media/MediaPlayerScreen.kt")
        val loadIdx = s.indexOf("CastSessionManager.load(")
        val pauseIdx = s.indexOf("mediaPlayer.pause()", startIndex = loadIdx)
        assertTrue(loadIdx > 0 && pauseIdx > loadIdx)
    }

    // MED-R15: playlists persistidas em Room
    @Test
    fun test_MED_R15_playlists_are_room_entities() {
        assertTrue(srcContains("com/example/data/MediaPlaylist.kt", "@Entity"))
        assertTrue(srcContains("com/example/data/PlaylistItem.kt", "@Entity"))
    }
}
