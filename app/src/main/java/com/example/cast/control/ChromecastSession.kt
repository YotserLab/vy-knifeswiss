package com.example.cast.control

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.example.BuildConfig
import com.example.cast.discovery.CastDevice
import com.example.cast.media.CastMedia
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.cast.CastDevice as GmsCastDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import com.google.android.gms.cast.framework.CastSession as GmsCastSession

/**
 * [CastSession] backed by the Google Cast SDK (Chromecast / Google Cast
 * targets). Wraps an established GMS [GmsCastSession] and drives playback
 * through [RemoteMediaClient].
 *
 * The session does not own the GMS connection lifecycle: the framework
 * keeps the session alive across activities. [close] ends the current
 * session — call [stop] first if playback should be halted.
 *
 * All GMS calls are guarded: every method returns false/null instead of
 * throwing when Play Services is absent or the session dropped.
 */
// MED-R12
class ChromecastSession(
    override val device: CastDevice,
    private val gmsSession: GmsCastSession,
    private val castContext: CastContext
) : CastSession {

    /** Read inside [guard] so the @MainThread GMS access runs on the main thread. */
    private val remoteMediaClient: RemoteMediaClient?
        get() = runCatching { gmsSession.remoteMediaClient }.getOrNull()

    override suspend fun load(media: CastMedia, url: String): Boolean = guard {
        val client = remoteMediaClient ?: return@guard false
        val metadata = MediaMetadata(metadataTypeFor(media.kind)).apply {
            putString(MediaMetadata.KEY_TITLE, media.title)
        }
        val info = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(media.mimeType)
            .setMetadata(metadata)
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setAutoplay(true)
            .build()
        client.load(request).awaitSuccess().also { ok ->
            Log.d(TAG, "remoteMediaClient.load($url, ${media.mimeType}) -> $ok")
        }
    }

    override suspend fun play(): Boolean = guard {
        val client = remoteMediaClient ?: return@guard false
        client.play().awaitSuccess()
    }

    override suspend fun pause(): Boolean = guard {
        val client = remoteMediaClient ?: return@guard false
        client.pause().awaitSuccess()
    }

    override suspend fun seek(positionMs: Long): Boolean = guard {
        val client = remoteMediaClient ?: return@guard false
        client
            .seek(MediaSeekOptions.Builder().setPosition(positionMs).build())
            .awaitSuccess()
    }

    override suspend fun stop(): Boolean = guard {
        val client = remoteMediaClient ?: return@guard false
        client.stop().awaitSuccess()
    }

    /** [level] is 0..100, mapped to the GMS 0.0..1.0 volume range. */
    override suspend fun setVolume(level: Int): Boolean = guard {
        gmsSession.volume = (level / 100.0).coerceIn(0.0, 1.0)
        true
    }

    /** GMS volume is 0.0..1.0 — mapped to the 0..100 range, null on failure. */
    override suspend fun getVolume(): Int? = guardNullable {
        (gmsSession.volume * 100).roundToInt()
    }

    override suspend fun positionInfo(): PositionInfo? = guardNullable {
        val client = remoteMediaClient ?: return@guardNullable null
        val state = playerStateName(client.playerState)
        if (state != lastLoggedState) {
            lastLoggedState = state
            val status = runCatching { client.mediaStatus }.getOrNull()
            Log.d(
                TAG,
                "playerState=$state idleReason=${status?.idleReason} " +
                    "pos=${client.approximateStreamPosition} dur=${client.streamDuration}"
            )
        }
        PositionInfo(
            relTimeMs = client.approximateStreamPosition,
            durationMs = client.streamDuration,
            state = state
        )
    }

    @Volatile
    private var lastLoggedState: String? = null

    /**
     * Ends the current GMS session. Best-effort and never throws.
     * [SessionManager] is @MainThread-enforced and [close] is non-suspend,
     * so [endCurrentSession] is posted to the main looper and may complete
     * asynchronously after this method returns.
     */
    override fun close() {
        try {
            Handler(Looper.getMainLooper()).post {
                try {
                    castContext.sessionManager.endCurrentSession(true)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
        }
    }

    /**
     * Awaits a [PendingResult] with a timeout; true only when the status is
     * success. On timeout the coroutine completes via [withTimeoutOrNull]
     * returning null — a [setResultCallback] firing afterwards is a
     * harmless no-op because `cont.isActive` is already false.
     */
    private suspend fun PendingResult<RemoteMediaClient.MediaChannelResult>.awaitSuccess(
        timeoutMs: Long = RESULT_TIMEOUT_MS
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            try {
                setResultCallback { result ->
                    if (cont.isActive) cont.resume(result.status.isSuccess)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) e.printStackTrace()
                if (cont.isActive) cont.resume(false)
            }
        }
    } ?: false

    /**
     * Runs [block] on the main thread — [RemoteMediaClient], [GmsCastSession]
     * and [SessionManager] APIs are @MainThread-enforced and throw when
     * called off-main — returning its Boolean result, or false on any
     * non-cancellation failure.
     */
    private suspend fun guard(block: suspend () -> Boolean): Boolean =
        guardNullable(block) == true

    /** [guard] variant for nullable results: null on any non-cancellation failure. */
    private suspend fun <T> guardNullable(block: suspend () -> T?): T? = try {
        withContext(Dispatchers.Main) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) e.printStackTrace()
        null
    }

    private fun playerStateName(state: Int): String = when (state) {
        MediaStatus.PLAYER_STATE_PLAYING -> "PLAYING"
        MediaStatus.PLAYER_STATE_PAUSED -> "PAUSED"
        MediaStatus.PLAYER_STATE_BUFFERING -> "BUFFERING"
        MediaStatus.PLAYER_STATE_IDLE -> "IDLE"
        else -> "UNKNOWN"
    }

    private fun metadataTypeFor(kind: CastMedia.Kind): Int = when (kind) {
        CastMedia.Kind.VIDEO -> MediaMetadata.MEDIA_TYPE_MOVIE
        CastMedia.Kind.AUDIO -> MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
        CastMedia.Kind.IMAGE -> MediaMetadata.MEDIA_TYPE_PHOTO
    }

    companion object {
        private const val TAG = "CastGms"
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val RESULT_TIMEOUT_MS = 10_000L

        /**
         * True when the Cast framework can be initialized — false when
         * Play Services is missing/outdated or the options provider is
         * not wired in the manifest.
         */
        fun isAvailable(context: Context): Boolean = runCatching {
            CastContext.getSharedInstance(context)
            true
        }.getOrDefault(false)

        /**
         * Route-name match between a MediaRouter route and a discovered
         * [CastDevice]. mDNS discovery yields the friendly name, which is
         * what MediaRouter exposes — compared case-insensitively and with
         * punctuation/whitespace normalized so "Living-Room TV" matches
         * "Living Room TV".
         */
        internal fun matchesDevice(routeName: String?, deviceName: String): Boolean { // MED-R13
            val route = routeName?.trim().orEmpty()
            val device = deviceName.trim()
            if (route.isEmpty() || device.isEmpty()) return false
            if (route.equals(device, ignoreCase = true)) return true
            val normalizedRoute = route.filter { it.isLetterOrDigit() }.lowercase()
            val normalizedDevice = device.filter { it.isLetterOrDigit() }.lowercase()
            return normalizedRoute.isNotEmpty() && normalizedRoute == normalizedDevice
        }

        /**
         * Identity check between a route/session-side Cast target and our
         * discovered [CastDevice]. mDNS names are service instance names
         * ("Chromecast-987a…"), not friendly names, so name matching alone
         * fails — and worse, a Cast Group shares the leader's IP, so "same
         * IP + different UUID" must be a *rejection*, not a match:
         *
         * 1. both UUIDs known → equality decides (excludes groups at the
         *    same IP);
         * 2. otherwise, equal LAN IP matches;
         * 3. otherwise, fall back to the friendly-name comparison.
         */
        internal fun targetMatches(
            routeIp: String?,
            routeDeviceId: String?,
            displayName: String?,
            device: CastDevice
        ): Boolean {
            val wantUuid = hexOnly(device.deviceUuid)
            val gotUuid = hexOnly(routeDeviceId)
            if (wantUuid.isNotEmpty() && gotUuid.isNotEmpty()) return wantUuid == gotUuid
            if (!routeIp.isNullOrEmpty() && device.ip.isNotEmpty() && routeIp == device.ip) return true
            return matchesDevice(displayName, device.name)
        }

        private fun hexOnly(value: String?): String =
            value.orEmpty().filter { it.isLetterOrDigit() }.lowercase()

        /** The GMS [GmsCastDevice] attached to a route's extras, or null. */
        private fun gmsDeviceFor(route: MediaRouter.RouteInfo): GmsCastDevice? =
            runCatching { GmsCastDevice.getFromBundle(route.extras) }.getOrNull()

        private fun routeMatches(route: MediaRouter.RouteInfo, device: CastDevice): Boolean {
            val gms = gmsDeviceFor(route)
            val routeIp = runCatching { gms?.inetAddress?.hostAddress }.getOrNull()
            return targetMatches(routeIp, gms?.deviceId, route.name, device)
        }

        private fun gmsDeviceMatches(gms: GmsCastDevice?, device: CastDevice): Boolean {
            gms ?: return false
            val ip = runCatching { gms.inetAddress?.hostAddress }.getOrNull()
            return targetMatches(ip, gms.deviceId, gms.friendlyName, device)
        }

        /**
         * Connects to [device] and returns a [ChromecastSession], or null
         * when no matching route is found or the session fails to start
         * within [timeoutMs]. Reuses the current GMS session when it is
         * already connected to the same target.
         *
         * Runs on [Dispatchers.Main] because MediaRouter/SessionManager
         * callbacks and route selection are main-thread APIs.
         */
        suspend fun connect(
            context: Context,
            device: CastDevice,
            timeoutMs: Long = CONNECT_TIMEOUT_MS
        ): ChromecastSession? = withContext(Dispatchers.Main) {
            val castContext = try {
                CastContext.getSharedInstance(context)
            } catch (e: Exception) {
                Log.w(TAG, "CastContext unavailable", e)
                return@withContext null
            }

            castContext.sessionManager.currentCastSession?.let { existing ->
                // Guard the read: a GMS edge-case throw yields null (no
                // match → falls through to the route flow) instead of
                // escaping connect.
                if (existing.isConnected && gmsDeviceMatches(existing.castDevice, device)) {
                    return@withContext ChromecastSession(device, existing, castContext)
                }
            }

            val mediaRouter = MediaRouter.getInstance(context)
            val selector = MediaRouteSelector.Builder()
                .addControlCategory(
                    CastMediaControlIntent.categoryForCast(
                        CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                    )
                )
                .build()

            val session = awaitSession(mediaRouter, selector, device, castContext.sessionManager, timeoutMs)
            session?.let { ChromecastSession(device, it, castContext) }
        }

        /**
         * Starts route discovery, selects the first Cast route matching
         * [device] (fallback: the only candidate when exactly one exists),
         * and suspends until the GMS session starts, fails, or the timeout
         * elapses. Listeners are always removed afterwards.
         */
        private suspend fun awaitSession(
            mediaRouter: MediaRouter,
            selector: MediaRouteSelector,
            device: CastDevice,
            sessionManager: SessionManager,
            timeoutMs: Long
        ): GmsCastSession? {
            var routeCallback: MediaRouter.Callback? = null
            var sessionListener: SessionManagerListener<GmsCastSession>? = null
            try {
                return withTimeoutOrNull(timeoutMs) {
                    suspendCancellableCoroutine { cont ->
                        var selected = false

                        fun pickRoute(routes: List<MediaRouter.RouteInfo>): MediaRouter.RouteInfo? {
                            val candidates = routes.filter {
                                it.matchesSelector(selector) && it.isEnabled
                            }
                            return candidates.firstOrNull { routeMatches(it, device) }
                                ?: candidates.firstOrNull { matchesDevice(it.description, device.name) }
                                ?: candidates.singleOrNull()
                        }

                        fun trySelect(router: MediaRouter) {
                            if (selected || !cont.isActive) return
                            val route = pickRoute(router.routes) ?: return
                            selected = true
                            Log.d(TAG, "selecting route '${route.name}' for device '${device.name}'")
                            runCatching { router.selectRoute(route) }
                                .onFailure {
                                    selected = false
                                    if (BuildConfig.DEBUG) it.printStackTrace()
                                }
                        }

                        val listener = object : SessionManagerListener<GmsCastSession> {
                            override fun onSessionStarted(session: GmsCastSession, sessionId: String) {
                                // Ignore events for a different target (e.g. a
                                // stale route selection resolving late): only a
                                // session matching the requested device completes
                                // the wait — a mismatch falls through to the next
                                // event or the outer timeout.
                                val friendlyName =
                                    runCatching { session.castDevice?.friendlyName }.getOrNull()
                                if (!gmsDeviceMatches(session.castDevice, device)) {
                                    Log.d(TAG, "ignoring session for '$friendlyName' (want '${device.name}')")
                                    return
                                }
                                Log.d(TAG, "session started for '$friendlyName'")
                                if (cont.isActive) cont.resume(session)
                            }

                            override fun onSessionStartFailed(session: GmsCastSession, error: Int) {
                                Log.w(TAG, "session start failed error=$error")
                                if (cont.isActive) cont.resume(null)
                            }

                            override fun onSessionEnded(session: GmsCastSession, error: Int) {}
                            override fun onSessionEnding(session: GmsCastSession) {}
                            override fun onSessionResumeFailed(session: GmsCastSession, error: Int) {}
                            override fun onSessionResumed(session: GmsCastSession, wasSuspended: Boolean) {}
                            override fun onSessionResuming(session: GmsCastSession, sessionId: String) {}
                            override fun onSessionStarting(session: GmsCastSession) {}
                            override fun onSessionSuspended(session: GmsCastSession, reason: Int) {}
                        }

                        val callback = object : MediaRouter.Callback() {
                            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                                trySelect(router)
                            }

                            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                                trySelect(router)
                            }
                        }

                        sessionListener = listener
                        routeCallback = callback
                        try {
                            sessionManager.addSessionManagerListener(
                                listener, GmsCastSession::class.java
                            )
                            mediaRouter.addCallback(
                                selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
                            )
                            trySelect(mediaRouter)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) e.printStackTrace()
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
            } finally {
                routeCallback?.let { runCatching { mediaRouter.removeCallback(it) } }
                sessionListener?.let {
                    runCatching {
                        sessionManager.removeSessionManagerListener(it, GmsCastSession::class.java)
                    }
                }
            }
        }
    }
}
