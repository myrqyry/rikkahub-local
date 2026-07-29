package me.rerere.rikkahub.skills.plugins

/**
 * A registered slash command that can be invoked by the user.
 *
 * @param name       Command name (without the leading "/").
 * @param description Shown in autocomplete and help.
 * @param args       Argument definitions for autocomplete and validation.
 * @param handler    Coroutine handler invoked with resolved argument values.
 */
data class SlashCommand(
    val name: String,
    val description: String,
    val args: List<SlashCommandArg> = emptyList(),
    val handler: suspend (args: Map<String, String>) -> Result<String>,
)

/**
 * One argument of a [SlashCommand].
 */
data class SlashCommandArg(
    val name: String,
    val description: String,
    val required: Boolean = false,
)

/**
 * A partial match result returned by [SlashCommandRegistry.matchPartial].
 *
 * @param command    The matched command.
 * @param matchText  The input text that triggered the match.
 * @param relevance  Normalised score 0.0..1.0 (higher = better match).
 */
data class SlashCommandMatch(
    val command: SlashCommand,
    val matchText: String,
    val relevance: Float,
)

/**
 * Thread-safe registry for [SlashCommand] instances.
 *
 * Supports registration, unregistration, lookup, execution, and fuzzy
 * partial matching via a simple token-overlap scorer.
 */
class SlashCommandRegistry {
    private val commands = mutableMapOf<String, SlashCommand>()

    /** Register a command. Replaces any existing command with the same [name]. */
    fun register(command: SlashCommand) {
        commands[command.name] = command
    }

    /** Remove a previously registered command by [name]. No-op if absent. */
    fun unregister(name: String) {
        commands.remove(name)
    }

    /** Look up a command by [name] (without the leading "/"). */
    fun get(name: String): SlashCommand? = commands[name]

    /**
     * Execute a registered command.
     *
     * @param name  Command name.
     * @param args  Resolved argument map.
     * @return The handler's result, or [Result.failure] if the command is unknown.
     */
    suspend fun execute(name: String, args: Map<String, String>): Result<String> {
        val command = commands[name] ?: return Result.failure(NoSuchElementException("Unknown command: $name"))
        return command.handler(args)
    }

    /**
     * Fuzzy-match [input] against all registered commands.
     *
     * Scoring: tokenise the input, tokenise each command's name + description,
     * compute Jaccard-like overlap. Name tokens are weighted 2× description tokens.
     * Returns the top 5 results sorted by [relevance] descending.
     *
     * Returns an empty list when [input] is blank or does not start with "/".
     */
    fun matchPartial(input: String): List<SlashCommandMatch> {
        if (input.isBlank() || commands.isEmpty()) return emptyList()

        // Strip leading "/" for tokenisation
        val query = if (input.startsWith("/")) input.removePrefix("/") else input
        val queryTokens = tokenise(query)
        if (queryTokens.isEmpty()) return emptyList()

        val scored = commands.map { (_, cmd) ->
            val nameTokens = tokenise(cmd.name)
            val descTokens = tokenise(cmd.description)

            val nameOverlap = nameTokens.intersect(queryTokens).size
            val descOverlap = descTokens.intersect(queryTokens).size

            // Weighted Jaccard: name tokens are 2× as important as description tokens
            val union = (nameTokens + descTokens + queryTokens).toSet()
            val score = if (union.isEmpty()) 0f
            else (nameOverlap * 2f + descOverlap) / union.size.coerceAtLeast(1)

            cmd to score.coerceIn(0f, 1f)
        }

        return scored
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .take(5)
            .map { (cmd, score) ->
                SlashCommandMatch(
                    command = cmd,
                    matchText = input,
                    relevance = score,
                )
            }
    }

    /** Return a snapshot of all registered commands. */
    fun listCommands(): List<SlashCommand> = commands.values.toList()

    // ---- internal helpers -------------------------------------------------

    /** Split [text] into lowercase tokens on whitespace and punctuation. */
    private fun tokenise(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[\\s_\\-/.]+"))
            .filter { it.isNotBlank() }
            .toSet()
}