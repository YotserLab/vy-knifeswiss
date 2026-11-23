package com.example.cast.control

import com.example.cast.discovery.CastDevice
import com.example.cast.media.CastMedia

/**
 * A control session with a single cast target. All remote calls are suspend
 * functions that return Boolean success (null/false on any transport or
 * device error — never throws for device-side failures).
 *
 * [close] is intentionally non-suspend so it is safe to call from
 * `onCleared`/dispose paths; callers should invoke [stop] first when they
 * want playback halted on the device.
 */
interface CastSession {
    val device: CastDevice

    /** False for targets (e.g. Roku ECP) that cannot seek arbitrary positions. */
    val supportsSeek: Boolean get() = true

    /** False for targets without a remote volume API. */
    val supportsVolume: Boolean get() = true

    /** False for targets (e.g. Roku ECP) that cannot report playback position. */
    val supportsPositionInfo: Boolean get() = true

    /** Points the device at [url] and starts playback of [media]. */
    suspend fun load(media: CastMedia, url: String): Boolean
    suspend fun play(): Boolean
    suspend fun pause(): Boolean
    suspend fun seek(positionMs: Long): Boolean
    suspend fun stop(): Boolean

    /** [level] is clamped to the device-supported range (0..100 for DLNA). */
    suspend fun setVolume(level: Int): Boolean = false

    /** Current device volume (0..100), or null when unsupported/unreadable. */
    suspend fun getVolume(): Int? = null

    /** Current playback position/duration/state, or null when unsupported. */
    suspend fun positionInfo(): PositionInfo? = null

    /** Releases the session. Does not send Stop — call [stop] first if needed. */
    fun close()
}
