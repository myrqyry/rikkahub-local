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

    /** Native binaries that are inherently network-capable. */
    private val NETWORK_CAPABLE = setOf("curl", "wget", "ssh", "scp")

    /** git subcommands that touch a remote (fetch/push/clone/pull). */
    private val GIT_NETWORK_SUBCOMMANDS = setOf("fetch", "push", "clone", "pull")

    fun analyze(command: List<String>): ProcessEffects {
        if (command.isEmpty()) return ProcessEffects()
        val reads = LinkedHashSet<String>()
        val writes = LinkedHashSet<String>()
        var network = false
        var native = false

        val bin = command[0]
        if (bin in NATIVE_BINARIES) native = true

        // Network-capable binaries: ssh/scp/curl/wget, or git with a remote subcommand.
        if (bin in NETWORK_CAPABLE) network = true
        if (bin == "git" && command.size > 1 && command[1] in GIT_NETWORK_SUBCOMMANDS) network = true

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

                // URL/host-style tokens imply network regardless of the binary.
                tok.startsWith("http://") || tok.startsWith("https://") ||
                    tok.startsWith("git@") || tok.startsWith("ssh://") || tok.startsWith("git://") -> network = true

                else -> {
                    if (command[0] in READ_COMMANDS && !tok.startsWith("-")) reads += tok
                }
            }
            i++
        }
        return ProcessEffects(reads.toSet(), writes.toSet(), network, native)
    }
}
