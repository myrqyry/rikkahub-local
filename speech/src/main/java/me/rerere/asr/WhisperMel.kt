package me.rerere.asr

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Host-side log-Mel spectrogram for OpenAI Whisper models.
 *
 * Parameters match the whisper standard pipeline exactly:
 * SR=16000, NFFT=400, HOP=160, NMEL=80, FMIN=0, FMAX=8000.
 *
 * The Mel filterbank is computed once at init (Hann + FFT + filterbank), then
 * reused for every call. The final output is zero-mean + unit-variance normalized
 * (whisper-style normalization), yielding [FRAMES * NMEL] = 240_000 floats.
 *
 * Input: 480_000 mono float samples (30 s @ 16 kHz, normalized to [-1, 1]).
 * Output: 3000 frames × 80 bins = 240_000 floats, row-major [f * NMEL + m].
 */
object WhisperMel {
    const val SAMPLE_RATE = 16000
    const val NFFT = 400
    const val HOP = 160
    const val NMEL = 80
    const val FRAMES = 3000 // 30 s / 10 ms
    const val CLIP_SAMPLES = 480_000 // 30 s @ 16 kHz
    const val N_BINS = NFFT / 2 + 1 // 201
    const val FMAX = 8000.0
    const val FMIN = 0.0

    /** Precomputed Mel filterbank [NMEL × N_BINS], row-major. */
    private val filterbank: FloatArray = buildMelFilterbank()

    /** Precomputed Hann window of length NFFT. */
    private val hann: FloatArray = FloatArray(NFFT) {
        (0.5f - 0.5f * cos(2.0 * Math.PI * it / NFFT).toFloat()).toFloat()
    }

    private val fftRe = FloatArray(NFFT)
    private val fftIm = FloatArray(NFFT)
    private val magnitude = FloatArray(N_BINS)
    private val melEnergies = FloatArray(NMEL)

    /**
     * Compute whisper-compatible log-Mel spectrogram.
     *
     * Pipeline: Hann window → FFT → |magnitude|^2 → Mel filterbank matmul → ln
     * → zero-mean/unit-var normalization, padded to 3000 frames (zero-pad right).
     *
     * Returns [FRAMES * NMEL] = 240_000 floats,
     * ready for the model's encode signature `float32[1,80,3000]`.
     */
    fun compute(pcm: FloatArray): FloatArray {
        val nSamples = pcm.size.coerceAtMost(CLIP_SAMPLES)
        val nFrames = (nSamples - NFFT) / HOP + 1
        val totalFrames = nFrames.coerceAtMost(FRAMES)

        val out = FloatArray(FRAMES * NMEL) // zero-padded

        for (f in 0 until totalFrames) {
            val start = f * HOP
            // Apply Hann window
            for (n in 0 until NFFT) {
                fftRe[n] = pcm[start + n] * hann[n]
                fftIm[n] = 0f
            }
            // FFT (in-place, radix-2)
            fftRadix2(fftRe, fftIm, NFFT)
            // Magnitude squared
            for (b in 0 until N_BINS) {
                val r = fftRe[b]; val i = fftIm[b]
                magnitude[b] = r * r + i * i
            }
            // Mel filterbank matmul
            for (m in 0 until NMEL) {
                var acc = 0f
                val rowOff = m * N_BINS
                for (b in 0 until N_BINS) {
                    acc += magnitude[b] * filterbank[rowOff + b]
                }
                melEnergies[m] = acc
            }
            // ln, with floor
            val frameOff = f * NMEL
            for (m in 0 until NMEL) {
                val v = melEnergies[m].coerceAtLeast(1e-10f)
                out[frameOff + m] = ln(v.toDouble()).toFloat()
            }
        }

        // Whisper normalization: zero mean, unit variance, over non-padded frames
        val nActive = totalFrames * NMEL
        var sum = 0.0
        for (i in 0 until nActive) sum += out[i].toDouble()
        val mean = (sum / nActive).toFloat()
        var sqSum = 0.0
        for (i in 0 until nActive) {
            val d = (out[i] - mean).toDouble()
            sqSum += d * d
        }
        val std = sqrt(sqSum / nActive).toFloat().coerceAtLeast(1e-6f)
        for (i in 0 until nActive) out[i] = (out[i] - mean) / std

        return out
    }

    /** In-place radix-2 iterative FFT. [re]/[im] must each be at least [n] long. */
    private fun fftRadix2(re: FloatArray, im: FloatArray, n: Int) {
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
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
            val wIm = kotlin.math.sin(ang).toFloat()
            for (i in 0 until n step len) {
                var curRe = 1f; var curIm = 0f
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

    /** Build the Mel filterbank: [NMEL × N_BINS], row-major. */
    private fun buildMelFilterbank(): FloatArray {
        val melMin = hzToMel(FMIN)
        val melMax = hzToMel(FMAX)
        val melPoints = FloatArray(NMEL + 2) {
            melMin + (melMax - melMin) * it / (NMEL + 1)
        }
        val freqs = FloatArray(NMEL + 2) { melToHz(melPoints[it].toDouble()).toFloat() }

        val binFreqs = FloatArray(N_BINS) {
            it.toFloat() * SAMPLE_RATE / NFFT
        }

        val fbank = FloatArray(NMEL * N_BINS)
        for (m in 1..NMEL) {
            val rowOff = (m - 1) * N_BINS
            for (b in 0 until N_BINS) {
                val f = binFreqs[b]
                val lo = freqs[m - 1]
                val mid = freqs[m]
                val hi = freqs[m + 1]
                if (f <= lo || f >= hi) continue
                val v = if (f < mid) (f - lo) / (mid - lo) else (hi - f) / (hi - mid)
                fbank[rowOff + b] = v
            }
        }
        return fbank
    }

    private fun hzToMel(f: Double): Float =
        (2595.0 * kotlin.math.log10(1.0 + f / 700.0)).toFloat()

    private fun melToHz(m: Double): Double =
        700.0 * (10.0.pow(m / 2595.0) - 1.0)
}
