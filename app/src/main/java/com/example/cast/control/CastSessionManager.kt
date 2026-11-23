package com.example.cast.control

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.cast.discovery.CastDevice
import com.example.cast.discovery.CastDeviceType
import com.example.cast.media.CastMedia
import com.example.cast.media.CastMediaServer
import com.example.service.InternalHttpFileServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.example.R
import com.example.Res

/**
 * App-level holder for the active [CastSession]: sessions survive
 * navigation, so playback keeps running while the user browses the
 * library. All public operations funnel onto a private Main scope and
 * the lifecycle ops (connect/load/stop/disconnect) are additionally
 * serialized through [opMutex] — without it, two concurrent connects
 * would interleave at suspension points and leak the first session
 * (a real GMS leak for Chromecast). [CastState] is only mutated through
 * `_state.update`, making every read of [state] consistent.
 */
object CastSessionManager {
    private const val TAG = "CastSession"

    data class CastState(
        val session: CastSession? = null,
        val device: CastDevice? = null,
        val media: CastMedia? = null,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val volume: Int = -1, // -1 = unknown
        val connecting: Boolean = false,
        val lastError: String? = null,
        // Token of the file hosted by InternalHttpFileServer for the current
        // media — renewed every poll tick so the URL cannot expire mid-playback.
        val servedToken: String? = null
    )

    private const val POLL_INTERVAL_MS = 1_000L

    private val _state = MutableStateFlow(CastState())
    val state: StateFlow<CastState> = _state.asStateFlow()

    // Lazy so pure helpers (deviceErrorFor) stay usable in JVM unit tests,
    // where Dispatchers.Main is unavailable unless Robolectric shadows it.
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    private val opMutex = Mutex()
    private var pollJob: Job? = null
    private var lastPollState: String? = null

    /** Reason a session cannot be created for [type], or null to use the generic failure. */
    internal fun deviceErrorFor(type: CastDeviceType): String? = when (type) {
        CastDeviceType.DIAL -> Res.get(R.string.dispositivo_limitado)
        else -> null
    }

    /**
     * Connects to [device], replacing any existing session. The concrete
     * [CastSession] is chosen by [CastDevice.type]: DLNA requires
     * `avTransportControlUrl`, Chromecast needs GMS and a matching route,
     * Roku is stateless ECP, and DIAL-only targets cannot play local media.
     * Afterwards the initial volume is fetched once for session types
     * that report it (GetVolume on DLNA, the GMS session on Chromecast).
     */
    suspend fun connect(context: Context, device: CastDevice): Boolean =
        withContext(scope.coroutineContext) {
            opMutex.withLock {
                disconnectInternal()
                _state.update { it.copy(device = device, connecting = true, lastError = null) }
                val session = try {
                    createSession(context, device)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) e.printStackTrace()
                    null
                }
                if (session != null) {
                    Log.d(TAG, "connected to ${device.name} (${device.type}) via ${session.javaClass.simpleName}")
                    _state.update {
                        it.copy(session = session, device = device, connecting = false, lastError = null)
                    }
                    // Initial volume fetch for any session type — getVolume()
                    // defaults to null for targets that cannot report it, so
                    // this is a single best-effort read (Chromecast reads the
                    // GMS session volume, DLNA goes through GetVolume).
                    val volume = try {
                        session.getVolume()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) e.printStackTrace()
                        null
                    }
                    if (volume != null) _state.update { it.copy(volume = volume) }
                    true
                } else {
                    Log.w(TAG, "connect failed for ${device.name} (${device.type})")
                    _state.update {
                        it.copy(
                            connecting = false,
                            lastError = deviceErrorFor(device.type) ?: Res.get(R.string.nao_foi_possivel_conectar)
                        )
                    }
                    false
                }
            }
        }

    /**
     * Hosts [media] via [CastMediaServer] and points the connected device
     * at the resulting URL. On success starts the 1s poll loop that renews
     * the served-file token ([CastState.servedToken]) and, on targets with
     * position feedback ([CastSession.supportsPositionInfo]), feeds
     * [CastState.positionMs]/[CastState.isPlaying].
     */
    suspend fun load(context: Context, media: CastMedia): Boolean =
        withContext(scope.coroutineContext) {
            opMutex.withLock {
                val session = _state.value.session
                if (session == null) {
                    _state.update { it.copy(lastError = Res.get(R.string.sem_sessao_ativa)) }
                    return@withLock false
                }
                val served = CastMediaServer.serve(context, media)
                if (served == null) {
                    _state.update { it.copy(lastError = Res.get(R.string.arquivo_indisponivel)) }
                    return@withLock false
                }
                val ok = try {
                    session.load(media, served.url)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) e.printStackTrace()
                    false
                }
                Log.d(TAG, "load(${media.title}) -> $ok")
                if (!ok) {
                    pollJob?.cancel()
                    pollJob = null
                    _state.update { it.copy(lastError = Res.get(R.string.tv_recusou_a_midia)) }
                    return@withLock false
                }
                // The session may have been replaced while load() suspended —
                // never write media state or start polling for a stale session.
                if (_state.value.session !== session) {
                    try {
                        session.stop()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) e.printStackTrace()
                    }
                    return@withLock false
                }
                _state.update {
                    it.copy(
                        media = media,
                        isPlaying = true,
                        positionMs = 0,
                        durationMs = media.durationMs,
                        lastError = null,
                        servedToken = served.token
                    )
                }
                startPolling(session)
                true
            }
        }

    suspend fun play(): Boolean = withContext(scope.coroutineContext) {
        val session = _state.value.session ?: return@withContext false
        val ok = onSession { it.play() }
        if (ok) {
            _state.update { it.copy(isPlaying = true) }
            // Restart the poll loop when stop() cancelled it — still on
            // the same session (guarded by identity). startPolling decides
            // internally whether there is work (position updates and/or
            // served-token renewal).
            if (_state.value.session === session && pollJob?.isActive != true) {
                startPolling(session)
            }
        }
        ok
    }

    suspend fun pause(): Boolean = withContext(scope.coroutineContext) {
        val ok = onSession { it.pause() }
        if (ok) _state.update { it.copy(isPlaying = false) }
        ok
    }

    suspend fun seek(positionMs: Long): Boolean = withContext(scope.coroutineContext) {
        val ok = onSession { it.seek(positionMs) }
        if (ok) _state.update { it.copy(positionMs = positionMs) }
        ok
    }

    suspend fun stop(): Boolean = withContext(scope.coroutineContext) {
        opMutex.withLock {
            pollJob?.cancel()
            pollJob = null
            val ok = onSession { it.stop() }
            if (ok) _state.update { it.copy(isPlaying = false, positionMs = 0) }
            ok
        }
    }

    suspend fun setVolume(level: Int): Boolean = withContext(scope.coroutineContext) {
        val ok = onSession { it.setVolume(level) }
        if (ok) _state.update { it.copy(volume = level) }
        ok
    }

    /** Best-effort stop, releases the session and resets to a blank state. */
    suspend fun disconnect() = withContext(scope.coroutineContext) {
        opMutex.withLock { disconnectInternal() }
    }

    /**
     * Clears [CastState.lastError] once the UI has surfaced it (e.g. a
     * toast). Without consumption the same error would re-fire on every
     * recomposition/screen entry that observes the state.
     */
    fun consumeError() {
        _state.update { it.copy(lastError = null) }
    }

    /** Session teardown — callers must hold [opMutex]. */
    private suspend fun disconnectInternal() {
        pollJob?.cancel()
        pollJob = null
        val session = _state.value.session
        if (session != null) {
            try {
                session.stop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) e.printStackTrace()
            }
            try {
                session.close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) e.printStackTrace()
            }
        }
        _state.value = CastState()
    }

    private suspend fun createSession(context: Context, device: CastDevice): CastSession? =
        when (device.type) {
            CastDeviceType.DLNA ->
                if (device.avTransportControlUrl.isNotEmpty()) DlnaSession(device) else null
            CastDeviceType.ROKU -> RokuSession(device)
            CastDeviceType.CHROMECAST ->
                if (ChromecastSession.isAvailable(context)) {
                    ChromecastSession.connect(context, device)
                } else {
                    null
                }
            CastDeviceType.DIAL -> null
        }

    private fun startPolling(session: CastSession) {
        // One loop, two jobs per tick: renew the hosted-file token so the
        // served URL never expires mid-playback, and — when the target can
        // report position — refresh positionMs/durationMs/isPlaying. The
        // loop also runs for position-less targets (e.g. Roku ECP), where
        // token renewal alone keeps the stream reachable.
        val pollsPosition = session.supportsPositionInfo
        if (!pollsPosition && _state.value.servedToken == null) return
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                try {
                    _state.value.servedToken?.let { InternalHttpFileServer.renew(it) } // MED-R09
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) e.printStackTrace()
                }
                if (pollsPosition) {
                    val info = try {
                        session.positionInfo()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) e.printStackTrace()
                        null
                    }
                    if (info != null) {
                        if (info.state != lastPollState) {
                            Log.d(TAG, "receiver state ${lastPollState} -> ${info.state} pos=${info.relTimeMs} dur=${info.durationMs}")
                            lastPollState = info.state
                        }
                        _state.update {
                            it.copy(
                                positionMs = info.relTimeMs,
                                durationMs = info.durationMs,
                                isPlaying = info.state == "PLAYING"
                            )
                        }
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Runs [block] against the active session; false on absence or any device error. */
    private suspend fun onSession(block: suspend (CastSession) -> Boolean): Boolean {
        val session = _state.value.session ?: return false
        return try {
            block(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
            false
        }
    }
}
