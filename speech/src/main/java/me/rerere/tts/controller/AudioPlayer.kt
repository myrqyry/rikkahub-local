package me.rerere.tts.controller

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioTrack
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import me.rerere.tts.model.AudioChunk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.PlaybackStatus
import me.rerere.tts.model.TTSResponse
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AudioPlayer(context: Context) {
    private val player = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var positionJob: Job? = null
    @Volatile private var streamTrack: AudioTrack? = null
    @Volatile private var streamPaused = false

    fun pause() {
        streamPaused = true
        streamTrack?.pause() ?: player.pause()
        if (streamTrack != null) _playbackState.update { it.copy(status = PlaybackStatus.Paused) }
    }
    fun resume() {
        streamPaused = false
        streamTrack?.play() ?: player.play()
        if (streamTrack != null) _playbackState.update { it.copy(status = PlaybackStatus.Playing) }
    }
    fun stop() {
        streamPaused = false
        streamTrack?.let { runCatching { it.pause(); it.flush() } }
        player.stop()
    }
    fun clear() = player.clearMediaItems()
    fun release() {
        streamTrack?.let { runCatching { it.release() } }
        streamTrack = null
        player.release()
        scope.cancel()
    }
    fun seekBy(ms: Long) {
        if (streamTrack == null) player.seekTo(player.currentPosition + ms)
        // Live PCM has no seekable source; never seek the inactive ExoPlayer.
    }
    fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
        streamTrack?.let { it.playbackRate = (it.sampleRate * speed).toInt().coerceAtLeast(1) }
        _playbackState.update { it.copy(speed = speed) }
    }

    /** Start audible output when the first PCM frame arrives, not when synthesis completes. */
    suspend fun playPcmStream(chunks: Flow<AudioChunk>) = withContext(Dispatchers.IO) {
        var track: AudioTrack? = null
        var writtenFrames = 0L
        try {
            PcmStreamPump.consume(chunks) { bytes, hz ->
                if (track == null) {
                    val minimum = AudioTrack.getMinBufferSize(
                        hz, AndroidAudioFormat.CHANNEL_OUT_MONO, AndroidAudioFormat.ENCODING_PCM_16BIT
                    )
                    require(minimum > 0) { "AudioTrack unsupported PCM rate: $hz" }
                    track = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AndroidAudioFormat.Builder()
                            .setEncoding(AndroidAudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(hz)
                            .setChannelMask(AndroidAudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(maxOf(minimum, 8192))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                    require(track!!.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack initialization failed" }
                    streamTrack = track
                    val requestedSpeed = _playbackState.value.speed
                    if (requestedSpeed != 1.0f) {
                        track!!.playbackRate = (hz * requestedSpeed).toInt().coerceAtLeast(1)
                    }
                    _playbackState.update { it.copy(status = PlaybackStatus.Buffering, positionMs = 0, durationMs = 0) }
                    if (!streamPaused) track!!.play()
                }
                var offset = 0
                while (offset < bytes.size) {
                    if (!currentCoroutineContext().isActive) return@consume
                    if (streamPaused) { delay(15); continue }
                    val count = track!!.write(bytes, offset, bytes.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                    require(count >= 0) { "AudioTrack write failed: $count" }
                    if (count == 0) delay(8) else {
                        offset += count
                        writtenFrames += count / 2
                        _playbackState.update { it.copy(
                            status = PlaybackStatus.Playing,
                            positionMs = (track!!.playbackHeadPosition.toLong() * 1000) / hz,
                            durationMs = writtenFrames * 1000 / hz
                        ) }
                    }
                }
            }
            track?.let { output ->
                // A finished producer is not a finished playback queue.
                // Pause is not a stalled stream. Do not time out a long user pause.
                var lastHead = output.playbackHeadPosition.toLong()
                var idleMs = 0L
                while (currentCoroutineContext().isActive &&
                    output.playbackHeadPosition.toLong() < writtenFrames) {
                    delay(20)
                    val head = output.playbackHeadPosition.toLong()
                    if (!streamPaused) idleMs = if (head > lastHead) 0 else idleMs + 20
                    if (idleMs >= 10000L) error("PCM playback stalled")
                    lastHead = head
                }
            }
            _playbackState.update { it.copy(status = PlaybackStatus.Ended) }
        } finally {
            if (streamTrack === track) streamTrack = null
            track?.let { runCatching { it.stop(); it.release() } }
            streamPaused = false
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun play(response: TTSResponse) = suspendCancellableCoroutine<Unit> { cont ->
        val bytes = if (response.format == AudioFormat.PCM) {
            pcmToWav(response.audioData, response.sampleRate ?: 24000)
        } else response.audioData

        val dataSourceFactory = DataSource.Factory { ByteArrayDataSource(bytes) }
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.EMPTY))

        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()

        _playbackState.update {
            it.copy(
                status = PlaybackStatus.Buffering,
                positionMs = 0L,
                durationMs = (response.duration?.times(1000))?.toLong() ?: it.durationMs
            )
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        _playbackState.update { it.copy(status = PlaybackStatus.Buffering) }
                        stopPositionUpdates()
                    }
                    Player.STATE_READY -> {
                        val isPlaying = player.isPlaying
                        val duration = if (player.duration > 0) player.duration else playbackState.value.durationMs
                        _playbackState.update {
                            it.copy(
                                status = if (isPlaying) PlaybackStatus.Playing else PlaybackStatus.Paused,
                                durationMs = duration,
                                positionMs = player.currentPosition
                            )
                        }
                        if (isPlaying) startPositionUpdates() else stopPositionUpdates()
                    }
                    Player.STATE_ENDED -> {
                        stopPositionUpdates()
                        _playbackState.update {
                            it.copy(
                                status = PlaybackStatus.Ended,
                                positionMs = player.duration.coerceAtLeast(it.positionMs),
                                durationMs = if (player.duration > 0) player.duration else it.durationMs
                            )
                        }
                        player.removeListener(this)
                        if (cont.isActive) cont.resume(Unit)
                    }
                    Player.STATE_IDLE -> {
                        stopPositionUpdates()
                        _playbackState.update { it.copy(status = PlaybackStatus.Idle) }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                player.removeListener(this)
                stopPositionUpdates()
                _playbackState.update { it.copy(status = PlaybackStatus.Error, errorMessage = error.message) }
                if (cont.isActive) cont.resumeWithException(error)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val status = if (isPlaying) PlaybackStatus.Playing else PlaybackStatus.Paused
                _playbackState.update { it.copy(status = status) }
                if (isPlaying) startPositionUpdates() else stopPositionUpdates()
            }
        }
        player.addListener(listener)
        cont.invokeOnCancellation {
            player.removeListener(listener)
            player.stop()
            stopPositionUpdates()
        }
    }

    private fun startPositionUpdates() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch(Dispatchers.Main.immediate) {
            while (true) {
                _playbackState.update {
                    it.copy(
                        positionMs = player.currentPosition,
                        durationMs = if (player.duration > 0) player.duration else it.durationMs
                    )
                }
                delay(100)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun pcmToWav(
        pcm: ByteArray,
        sampleRate: Int,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val out = ByteArrayOutputStream()
        with(out) {
            write("RIFF".toByteArray())
            write(intToBytes(36 + pcm.size))
            write("WAVE".toByteArray())
            write("fmt ".toByteArray())
            write(intToBytes(16))
            write(shortToBytes(1))
            write(shortToBytes(channels.toShort()))
            write(intToBytes(sampleRate))
            write(intToBytes(byteRate))
            write(shortToBytes((channels * bitsPerSample / 8).toShort()))
            write(shortToBytes(bitsPerSample.toShort()))
            write("data".toByteArray())
            write(intToBytes(pcm.size))
            write(pcm)
        }
        return out.toByteArray()
    }

    private fun intToBytes(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private fun shortToBytes(value: Short) = byteArrayOf(
        (value.toInt() and 0xFF).toByte(),
        ((value.toInt() shr 8) and 0xFF).toByte()
    )
}

