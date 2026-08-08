package me.rerere.asr

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

/**
 * Host-side log-mel spectrogram for the PANNs-CNN14 AudioSet model
 * (`cnn14_audioset_fp16.tflite`). The model takes a fixed log-mel input of
 * shape [1, 1, 1001, 64] and emits 527 sigmoid class probabilities.
 *
 * Preprocessing mirrors the model card exactly:
 * SR=32000, NFFT=1024, HOP=320, NMEL=64, PAD=512 (reflect-pad), periodic Hann
 * window, 1024-pt FFT -> power spectrum -> mel-filterbank matmul -> 10*log10.
 *
 * The mel filterbank is NOT computed here; it ships with the model as
 * `mel_basis.bin` ([64, 513] float32, mel-major, little-endian) and is loaded
 * from the model's directory, so the values match the model's training exactly.
 */
object PannsMel {
    const val SAMPLE_RATE = 32000
    const val NFFT = 1024
    const val HOP = 320
    const val NMEL = 64
    const val PAD = NFFT / 2 // 512
    const val FRAMES = 1001
    const val CLIP_SAMPLES = 320_000 // 10 s @ 32 kHz

    /** Total float count of the log-mel input the model expects (1001 * 64). */
    const val INPUT_FLOATS = FRAMES * NMEL // 64064

    /** Load `mel_basis.bin` (raw float32, mel-major [64, 513], little-endian). */
    fun loadMelBasis(file: File): FloatArray? {
        if (!file.isFile) return null
        val size = NMEL * (NFFT / 2 + 1) // 64 * 513
        return runCatching {
            val bytes = file.readBytes()
            if (bytes.size < size * 4) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            FloatArray(size) { buffer.get(it) }
        }.getOrNull()
    }

    /**
     * Compute the log-mel spectrogram of [pcm] (raw mono floats normalized to
     * [-1, 1]) using the preloaded [melBasis]. Returns [INPUT_FLOATS] floats
     * ([FRAMES] x [NMEL]), the exact input the cnn14 model consumes.
     */
    fun computeLogMel(pcm: FloatArray, melBasis: FloatArray): FloatArray {
        val padded = FloatArray(CLIP_SAMPLES + 2 * PAD)
        val n = pcm.size.coerceAtMost(CLIP_SAMPLES)
        for (i in 0 until n) padded[i + PAD] = pcm[i]
        // reflect-pad both edges (np.pad mode="reflect")
        for (k in 1..PAD) {
            if (k < n) padded[PAD - k] = pcm[k]
            if (n - 1 - k >= 0) padded[PAD + n - 1 + k] = pcm[n - 1 - k]
        }

        val hann = FloatArray(NFFT) { 0.5f - 0.5f * cos(2.0 * Math.PI * it / NFFT).toFloat() }
        val re = FloatArray(NFFT)
        val im = FloatArray(NFFT)
        val power = FloatArray(NFFT / 2 + 1)
        val out = FloatArray(INPUT_FLOATS)
        val bins = NFFT / 2 + 1 // 513

        for (f in 0 until FRAMES) {
            val start = f * HOP
            for (n in 0 until NFFT) {
                re[n] = padded[start + n] * hann[n]
                im[n] = 0f
            }
            fft(re, im)
            for (b in 0 until bins) power[b] = re[b] * re[b] + im[b] * im[b]
            for (m in 0 until NMEL) {
                var acc = 0f
                val row = m * bins
                for (b in 0 until bins) acc += power[b] * melBasis[row + b]
                out[f * NMEL + m] = 10f * log10(max(acc, 1e-10f))
            }
        }
        return out
    }

    /** In-place radix-2 iterative FFT over [re]/[im] (size = NFFT = 1024). */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            for (i in 0 until n step len) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val aRe = re[i + k]; val aIm = im[i + k]
                    val bRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val bIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = aRe + bRe; im[i + k] = aIm + bIm
                    re[i + k + len / 2] = aRe - bRe; im[i + k + len / 2] = aIm - bIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
            }
            len = len shl 1
        }
    }
}
