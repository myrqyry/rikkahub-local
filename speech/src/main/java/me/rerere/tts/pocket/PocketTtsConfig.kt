package me.rerere.tts.pocket

data class PocketTtsConfig(
    val flowSteps: Int = 4,
    val maxFrames: Int = 1000,
    val framesAfterEos: Int = 0,
    val temperature: Float = 0.8f,
    val eosThreshold: Float = 0.5f,
    val intraThreads: Int = 4,
    val seed: Long = -1,
) {
    init {
        require(flowSteps in 1..32) { "flow_steps must be in [1,32]" }
        require(maxFrames in 1..1000) { "max_frames must be in [1,1000]" }
        require(framesAfterEos in 0..50) { "frames_after_eos must be in [0,50]" }
        require(temperature.isFinite() && temperature in 0f..10f) { "temperature must be finite in [0,10]" }
        require(eosThreshold.isFinite()) { "eos_threshold must be finite" }
        require(intraThreads in 1..64) { "intra_threads must be in [1,64]" }
    }
}
