package com.shilapi.xcertplay

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RemoteControlClient
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.orchestration.CarPlayController

/** Generic hardware keys with API17 and API21+ media registration backends. */
internal object CarPlayMediaKeys {
    private const val TAG = "DiPlay-MediaKeys"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backend: Backend by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) Api21Backend() else LegacyBackend()
    }
    private class KeyTest(val context: Context, val onKey: (KeyEvent) -> Unit)
    private var keyTest: KeyTest? = null

    internal fun beginKeyTest(context: Context, onKey: (KeyEvent) -> Unit): Any {
        backend.resetKeys()
        return KeyTest(context.applicationContext, onKey).also { keyTest = it }
    }

    internal fun endKeyTest(expected: Any) {
        if (keyTest !== expected) return
        keyTest = null
        backend.resetKeys()
    }

    fun attach(context: Context, controller: CarPlayController) = backend.attach(context, controller)
    fun detach(expected: CarPlayController?) = backend.detach(expected)
    fun onHostResumed(expected: CarPlayController?) = backend.refresh(expected)
    fun onMediaAudioChanged(active: Boolean) = mainHandler.post { backend.update(active) }
    fun onIphonePlaying(playing: Boolean) {
        if (playing) mainHandler.post { backend.regainFocus() }
    }

    internal fun dispatch(event: KeyEvent, source: String = "media-broadcast"): Boolean {
        keyTest?.let { test ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                GolfHardwareKeyLog.record(test.context, "HardwareKey source=$source code=${event.keyCode} " +
                    "scan=${event.scanCode} action=${event.action} repeat=0 diagnosticOnly=true")
                test.onKey(event)
            }
            return event.keyCode != KeyEvent.KEYCODE_BACK
        }
        return backend.dispatch(event, source)
    }
    internal fun dispatchWindow(event: KeyEvent, expected: CarPlayController?): Boolean =
        expected != null && backend.matches(expected) && dispatch(event, "window")

    private interface Backend {
        fun attach(context: Context, controller: CarPlayController)
        fun detach(expected: CarPlayController?)
        fun update(active: Boolean)
        fun regainFocus()
        fun refresh(expected: CarPlayController?)
        fun matches(expected: CarPlayController): Boolean
        fun resetKeys()
        fun dispatch(event: KeyEvent, source: String): Boolean
    }

    private abstract class FocusBackend : Backend {
        protected var context: Context? = null
        protected var controller: CarPlayController? = null
        protected var mediaActive = false
        private var bindings = emptyList<GolfKeyBindings.Binding>()
        private val keys = GolfHardwareKeyDispatcher(
            media = { send(it) },
            telephony = { controller?.sendTelephonyButton(it) == true },
            siri = { controller?.requestSiri() == true },
            report = { line -> context?.let { GolfHardwareKeyLog.record(it, line) } },
            logicalKey = { event -> GolfKeyBindings.logicalKey(bindings, event) },
        )
        private var focusHeld = false
        private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
            Log.i(TAG, "audio focus change=$change")
            if (change == AudioManager.AUDIOFOCUS_LOSS) focusHeld = false
        }

        override fun attach(context: Context, controller: CarPlayController) {
            if (this.controller !== controller) {
                this.controller?.playbackListener = null
                release()
            }
            this.context = context.applicationContext
            bindings = GolfKeyBindings.load(context)
            this.controller = controller
            controller.playbackListener = ::onPlaybackChanged
            update(mediaActive)
        }

        override fun detach(expected: CarPlayController?) {
            if (expected == null || controller !== expected) return
            expected.playbackListener = null
            release()
        }

        override fun regainFocus() {
            reclaimMediaButtons()
            if (focusHeld) return
            val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            @Suppress("DEPRECATION")
            val result = audio.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
            focusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        protected open fun reclaimMediaButtons() {}

        override fun refresh(expected: CarPlayController?) {
            if (expected != null && controller === expected) update(mediaActive)
        }
        override fun matches(expected: CarPlayController): Boolean = controller === expected
        override fun resetKeys() {
            keys.reset()
            bindings = context?.let(GolfKeyBindings::load) ?: emptyList()
        }

        override fun dispatch(event: KeyEvent, source: String): Boolean {
            if (controller == null) return false
            return keys.dispatch(event, source)
        }

        protected fun send(index: Int): Boolean {
            if (keyTest != null) return false // Includes modern transport callbacks during diagnostics.
            val sent = controller?.sendMediaButton(index) ?: false
            Log.i(TAG, "media key -> CarPlay $index sent=$sent")
            return sent
        }

        protected open fun release() {
            val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            @Suppress("DEPRECATION")
            audio?.abandonAudioFocus(focusListener)
            focusHeld = false
            mediaActive = false
            keys.reset()
            controller = null
            context = null
        }

        private fun onPlaybackChanged(playing: Boolean) {
            if (playing) mainHandler.post { regainFocus() }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private class Api21Backend : FocusBackend() {
        private var session: MediaSession? = null

        override fun update(active: Boolean) {
            val currentContext = context ?: return
            if (controller == null) return
            mediaActive = active
            if (session == null) {
                session = MediaSession(currentContext, "DiPlay CarPlay").apply {
                    setCallback(CarPlayMediaCallback(
                        hardwareEvent = { event -> CarPlayMediaKeys.dispatch(event, "media-session") },
                    ) { index, _ -> send(index) }, mainHandler)
                    isActive = true
                }
            }
            if (active) regainFocus()
            val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS
            session?.setPlaybackState(
                PlaybackState.Builder().setActions(actions).setState(
                    if (active) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1f,
                ).build(),
            )
        }

        override fun release() {
            session?.apply { isActive = false; release() }
            session = null
            super.release()
        }
    }

    @Suppress("DEPRECATION")
    private class LegacyBackend : FocusBackend() {
        private var remote: RemoteControlClient? = null
        private var receiver: ComponentName? = null

        override fun update(active: Boolean) {
            val currentContext = context ?: return
            if (controller == null) return
            mediaActive = active
            if (remote == null) {
                receiver = ComponentName(currentContext, CarPlayMediaButtonReceiver::class.java)
                val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(receiver)
                val pending = PendingIntent.getBroadcast(currentContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                remote = RemoteControlClient(pending).apply {
                    setTransportControlFlags(
                        RemoteControlClient.FLAG_KEY_MEDIA_PLAY or RemoteControlClient.FLAG_KEY_MEDIA_PAUSE or
                            RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE or RemoteControlClient.FLAG_KEY_MEDIA_NEXT or
                            RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS,
                    )
                }
            }
            if (active) regainFocus() else reclaimMediaButtons()
            remote?.setPlaybackState(
                if (active) RemoteControlClient.PLAYSTATE_PLAYING else RemoteControlClient.PLAYSTATE_PAUSED,
            )
        }

        override fun reclaimMediaButtons() {
            val currentContext = context ?: return
            val component = receiver ?: return
            val client = remote ?: return
            val audio = currentContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // ComponentName is available on API8; the PendingIntent overload requires API18.
            // Focus alone does not reorder the OEM media receiver stack. Also reclaim on the
            // iPhone's playback(true) callbacks, even if focus and the media stream stay active.
            runCatching {
                audio.registerMediaButtonEventReceiver(component)
                audio.registerRemoteControlClient(client)
            }.onFailure {
                GolfHardwareKeyLog.record(currentContext, "HardwareKey registration failed=${it.javaClass.simpleName}")
            }
        }

        override fun release() {
            val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audio != null) {
                remote?.let { audio.unregisterRemoteControlClient(it) }
                receiver?.let { audio.unregisterMediaButtonEventReceiver(it) }
            }
            remote = null
            receiver = null
            super.release()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class CarPlayMediaCallback(
    private val hardwareEvent: ((KeyEvent) -> Boolean)? = null,
    private val send: (Int, Bundle?) -> Unit,
) : MediaSession.Callback() {
    override fun onPlay() = send(CarPlayMediaButton.PLAY, null)
    override fun onPause() = send(CarPlayMediaButton.PAUSE, null)
    override fun onSkipToNext() = send(CarPlayMediaButton.NEXT, null)
    override fun onSkipToPrevious() = send(CarPlayMediaButton.PREVIOUS, null)

    override fun onMediaButtonEvent(intent: Intent): Boolean {
        @Suppress("DEPRECATION")
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
        hardwareEvent?.let { return it(event) }
        val index = when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> CarPlayMediaButton.PLAY_PAUSE
            else -> CarPlayMediaButton.forKeyCode(event.keyCode) ?: return false
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) send(index, null)
        return true
    }
}

class CarPlayMediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        @Suppress("DEPRECATION")
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        if (CarPlayMediaKeys.dispatch(event) && isOrderedBroadcast) abortBroadcast()
    }
}
