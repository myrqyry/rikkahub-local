package me.rerere.locallm.litert.workspace

/**
 * Deterministic effect preflight (roadmap E4). Analyzes a process command line
 * before execution to surface the side effects it would have: which paths it
 * reads/writes, whether it touches the network, and whether it runs a native
 * binary. Callers (the executor layer / approval broker) can reject effect
 * mismatches before anything runs.
 */
data class ProcessEffects(
    val reads: Set<String> = emptySet(),
    val writes: Set<String> = emptySet(),
    val network: Boolean = false,
    val nativeExecution: Boolean = false,
) {
    val isClean: Boolean
        get() = reads.isEmpty() && writes.isEmpty() && !network && !nativeExecution
}

object CommandEffectAnalyzer {

    private val NATIVE_BINARIES = setOf(
        "gradle", "git", "adb", "pnpm", "npm", "yarn", "cc", "gcc", "clang",
        "python", "python3", "go", "rustc", "cargo", "node", "make", "cmake",
        "sh", "bash", "zsh", "awk", "ssh", "curl", "wget",
    )

    private val READ_COMMANDS = setOf("cat", "grep", "sed", "head", "tail", "less", "wc", "diff", "sort", "cut")

    fun analyze(command: List<String>): ProcessEffects {
        if (command.isEmpty()) return ProcessEffects()
        val reads = LinkedHashSet<String>()
        val writes = LinkedHashSet<String>()
        var network = false
        var native = false

        if (command[0] in NATIVE_BINARIES) native = true

        var i = 1
        var expectingRedirTarget = false
        var redirOp: String? = null
        while (i < command.size) {
            val tok = command[i]
            when {
                expectingRedirTarget -> {
                    if (redirOp == ">" || redirOp == ">>") writes += tok
                    if (redirOp == "<") reads += tok
                    expectingRedirTarget = false
                    redirOp = null
                }

                tok == ">" || tok == ">>" || tok == "<" -> {
                    expectingRedirTarget = true
                    redirOp = tok
                }

                tok == "curl" || tok == "wget" || tok == "http" || tok == "https" ||
                    tok.startsWith("http://") || tok.startsWith("https://") -> network = true

                else -> {
                    if (command[0] in READ_COMMANDS && !tok.startsWith("-")) reads += tok
                }
            }
            i++
        }
        return ProcessEffects(reads.toSet(), writes.toSet(), network, native)
    }
}
