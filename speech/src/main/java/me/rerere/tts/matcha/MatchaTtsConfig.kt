package me.rerere.tts.matcha

data class MatchaTtsConfig(
    val speechSpeed: Float = 1.0f,
    val durationScale: Float = 1.0f,
    val flowSteps: Int = 10,
    val seed: Long? = null,
) {
    init {
        require(speechSpeed.isFinite() && speechSpeed in 0.5f..2.0f) {
            "speechSpeed must be in [0.5,2.0]"
        }
        require(durationScale.isFinite() && durationScale in 0.5f..2.0f) {
            "durationScale must be in [0.5,2.0]"
        }
        require(flowSteps in 4..30) { "flowSteps must be in [4,30]" }
    }

    val effectiveDurationScale: Float
        get() = durationScale / speechSpeed
}
