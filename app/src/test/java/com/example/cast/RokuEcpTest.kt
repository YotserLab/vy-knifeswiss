package com.example.cast

import com.example.cast.control.RokuEcp
import com.example.cast.control.RokuSession
import com.example.cast.discovery.CastDevice
import com.example.cast.discovery.CastDeviceType
import com.example.cast.media.CastMedia
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RokuEcpTest {

    @Test
    fun `launchPath encodes video launch for media app`() {
        assertEquals(
            "/launch/15985?t=v&u=http%3A%2F%2Fp%2Fv.mp4&videoName=Meu+V%C3%ADdeo",
            RokuEcp.launchPath(CastMedia.Kind.VIDEO, "http://p/v.mp4", "Meu Vídeo")
        )
    }

    @Test
    fun `launchPath uses a for audio and p for image`() {
        assertEquals(
            "/launch/15985?t=a&u=http%3A%2F%2Fh%2Fs.mp3&videoName=song",
            RokuEcp.launchPath(CastMedia.Kind.AUDIO, "http://h/s.mp3", "song")
        )
        assertEquals(
            "/launch/15985?t=p&u=http%3A%2F%2Fh%2Fimg.jpg&videoName=pic",
            RokuEcp.launchPath(CastMedia.Kind.IMAGE, "http://h/img.jpg", "pic")
        )
    }

    @Test
    fun `launchPath url encodes query strings inside media url`() {
        val path = RokuEcp.launchPath(CastMedia.Kind.VIDEO, "http://h/v.mp4?x=1&y=2", "t")
        assertEquals(
            "/launch/15985?t=v&u=http%3A%2F%2Fh%2Fv.mp4%3Fx%3D1%26y%3D2&videoName=t",
            path
        )
    }

    @Test
    fun `roku session does not support seek or volume`() = runBlocking {
        val device = CastDevice("r1", "Roku", CastDeviceType.ROKU, "192.0.2.9")
        val session = RokuSession(device)
        assertFalse(session.supportsSeek)
        assertFalse(session.supportsVolume)
        assertFalse(session.seek(1_000))
        assertFalse(session.setVolume(10))
        assertNull(session.positionInfo())
        session.close()
    }
}
