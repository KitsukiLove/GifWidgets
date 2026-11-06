package app.gifwidgets.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import app.gifwidgets.control.DesignStyle
import app.gifwidgets.control.Season
import kotlin.math.max
import kotlin.math.min

/**
 * Background music and little interface sounds, like the KixKi Store page.
 *
 * - Everything is synthesised on the phone (Synth.kt): no audio files, no permissions, no internet.
 * - Music plays ONLY while one of the app's screens is on screen (the app reports it with [onForeground]). When the
 *   last screen stops, the player thread ends and the AudioTrack is released: no service, no wake lock, nothing
 *   runs in the background.
 * - It asks for audio focus before the music starts and stops when another app takes it.
 * - It is off until the user turns music on; the blips follow the "Sonidos" setting.
 */
object KixkiAudio {
    private const val SR = SfxSynth.SAMPLE_RATE

    private var app: Context? = null
    private var musicOn = false
    private var soundOn = true
    private var style = DesignStyle.PIXEL
    private var season = Season.CLASICA
    private var night = false
    private var foreground = false
    private var focusLost = false

    private var player: MusicThread? = null
    private var sfx: SfxPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    // throttle like the page: never closer than 90 ms, never more than 6 sounds per 2.5 s
    private var lastBlip = 0L
    private val recent = ArrayDeque<Long>()
    private var lastNote = -1

    @Synchronized
    fun init(context: Context) {
        if (app == null) app = context.applicationContext
    }

    /** Called by the theme whenever the user's choices or the look change. Cheap when nothing changed. */
    @Synchronized
    fun configure(musicOn: Boolean, soundOn: Boolean, style: DesignStyle, season: Season, night: Boolean) {
        this.soundOn = soundOn
        this.style = style
        this.season = season
        this.night = night
        this.musicOn = musicOn
        sync()
    }

    /** The app reports whether any of its screens is started. */
    @Synchronized
    fun onForeground(isForeground: Boolean) {
        foreground = isForeground
        if (isForeground) focusLost = false
        sync()
    }

    private fun sync() {
        val wantMusic = musicOn && foreground && !focusLost
        val current = player
        if (!wantMusic) {
            if (current != null) {
                current.finish()
                player = null
                abandonFocus()
            }
            return
        }
        val piece = MusicPieces.pick(style, season, night)
        if (current != null) {
            current.wanted = piece
            return
        }
        if (!requestFocus()) return
        player = MusicThread(piece).also { it.start() }
    }

    // ----- audio focus -----

    private fun audioManager(): AudioManager? = app?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private fun attributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun requestFocus(): Boolean {
        val am = audioManager() ?: return true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes())
            .setOnAudioFocusChangeListener { change -> onFocusChange(change) }
            .build()
        focusRequest = request
        return am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        audioManager()?.abandonAudioFocusRequest(request)
        focusRequest = null
    }

    @Synchronized
    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusLost = true
                sync()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.duck = 0.3f
            AudioManager.AUDIOFOCUS_GAIN -> {
                player?.duck = 1f
                if (focusLost) {
                    focusLost = false
                    sync()
                }
            }
        }
    }

    // ----- little sounds -----

    /** A soft blip near [hz] on the current season's scale. */
    @Synchronized
    fun blip(hz: Double = 660.0, seconds: Double = .07) {
        if (!allowSound()) return
        val note = SfxSynth.noteFor(season, hz, lastNote)
        lastNote = note
        val volume = (if (night) .7 else 1.0) * (if (player != null) .55 else 1.0)
        play(SfxSynth.beep(season, note, seconds, night, volume))
    }

    /** Kixki's "beee" (a tap on the sheep). */
    @Synchronized
    fun baa() {
        if (!allowSound()) return
        val volume = (if (night) .7 else 1.0) * (if (player != null) .55 else 1.0)
        play(SfxSynth.baa(night, season == Season.HALLOWEEN, Math.random(), volume))
    }

    private fun allowSound(): Boolean {
        if (!soundOn || !foreground) return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlip < 90) return false
        while (recent.isNotEmpty() && now - recent.first() > 2500) recent.removeFirst()
        if (recent.size >= 6) return false
        // be polite: if another app is playing music and ours is not, stay quiet
        if (player == null && now - lastBlip > 4000 && audioManager()?.isMusicActive == true) return false
        lastBlip = now
        recent.addLast(now)
        return true
    }

    private fun play(samples: ShortArray) {
        val context = app ?: return
        val p = sfx ?: SfxPlayer().also { sfx = it }
        p.play(samples, context)
    }

    // ----- implementation: music thread -----

    private class MusicThread(first: Piece) : Thread("kixki-music") {
        @Volatile var wanted: Piece = first
        @Volatile var duck: Float = 1f
        @Volatile private var stopping = false

        init {
            isDaemon = true
        }

        fun finish() {
            stopping = true
        }

        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SR)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minBytes = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bytes = max(minBytes, SR * 2 * 3 / 10) // about 0.3 s: short enough to react to changes
            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (e: Exception) {
                return
            }
            try {
                track.play()
                var piece = wanted
                var renderer = SynthRenderer(piece, SR)
                var gain = 0f
                val fadeIn = 1f / (SR * 1.6f)
                val fadeOut = 1f / (SR * 0.7f)
                var switching = false
                val chunk = ShortArray(1024)
                var bar = renderer.nextBar()
                var pos = 0
                while (true) {
                    if (!switching && !stopping && wanted !== piece) switching = true
                    val n = min(chunk.size, bar.size - pos)
                    val fading = stopping || switching
                    for (i in 0 until n) {
                        gain = if (fading) max(0f, gain - fadeOut) else min(duck, gain + fadeIn)
                        chunk[i] = (bar[pos + i] * gain).toInt().toShort()
                    }
                    var written = 0
                    while (written < n) {
                        val w = track.write(chunk, written, n - written, AudioTrack.WRITE_NON_BLOCKING)
                        if (w < 0) return
                        written += w
                        if (w == 0) {
                            if (stopping && gain <= 0f) break
                            try { sleep(12) } catch (e: InterruptedException) { return }
                        }
                    }
                    pos += n
                    if (stopping && gain <= 0f) break
                    if (switching && gain <= 0f) {
                        piece = wanted
                        renderer = SynthRenderer(piece, SR)
                        switching = false
                        bar = renderer.nextBar()
                        pos = 0
                    } else if (pos >= bar.size) {
                        bar = renderer.nextBar()
                        pos = 0
                    }
                }
            } finally {
                try { track.pause() } catch (e: IllegalStateException) { /* already stopped */ }
                try { track.flush() } catch (e: IllegalStateException) { /* already stopped */ }
                track.release()
            }
        }
    }

    // ----- implementation: short sounds (one track that is released when idle) -----

    private class SfxPlayer {
        private val thread = HandlerThread("kixki-sfx").also { it.start() }
        private val handler = Handler(thread.looper)
        private var track: AudioTrack? = null
        private val idle = Runnable { release() }

        fun play(samples: ShortArray, context: Context) {
            handler.post {
                val t = track ?: create() ?: return@post
                try {
                    t.write(samples, 0, samples.size)
                } catch (e: IllegalStateException) {
                    release()
                    return@post
                }
                handler.removeCallbacks(idle)
                handler.postDelayed(idle, 3000)
            }
        }

        private fun create(): AudioTrack? = try {
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(max(AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), SR))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            t.play()
            track = t
            t
        } catch (e: Exception) {
            null
        }

        private fun release() {
            val t = track ?: return
            track = null
            try { t.stop() } catch (e: IllegalStateException) { /* already stopped */ }
            t.release()
        }
    }
}
