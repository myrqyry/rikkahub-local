package me.rerere.rikkahub.skills

/**
 * Formats selected skill content into a structured system prompt section.
 *
 * The output is designed to be appended to the assistant's system prompt so
 * the LLM is aware of which skills are available and how to invoke them.
 */
class SystemPromptFormatter {

    /**
     * Format a list of [skills] into a markdown system prompt section.
     *
     * The output follows this structure:
     * ```
     * ## Available Skills
     *
     * The following skills are available. Use them by invoking `use_skill`:
     *
     * <skill_name>
     * <skill_description>
     * ```
     */
    fun formatSkillSection(skills: List<SkillContent>): String {
        if (skills.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("## Available Skills")
        sb.appendLine()
        sb.appendLine("The following skills are available. Use them by invoking `use_skill`:")
        sb.appendLine()

        for (skill in skills) {
            sb.appendLine(skill.name)
            sb.appendLine(skill.description)
            sb.appendLine()
        }

        return sb.toString().trimEnd()
    }
}

/**
 * Content for a single skill to be included in the system prompt.
 *
 * @property name The skill's name (used as the `use_skill` argument).
 * @property description A brief description of the skill's capabilities.
 */
data class SkillContent(
    val name: String,
    val description: String,
)