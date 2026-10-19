package me.rerere.rikkahub.data.ai.prompts

internal val DEFAULT_COMPRESS_PROMPT = """
    Create a compact continuation record of the conversation below.

    Preserve in priority order:
    1. The user's current goal, exact requests, and constraints.
    2. Decisions, changes to decisions, reasons, and corrections (intent is not outcome).
    3. What actually happened: verified results vs plans, proposals, guesses, and failures.
    4. Work in progress, unresolved problems, next steps, and blockers.
    5. Important names, paths, dates, identifiers, settings, and references needed to resume.
    6. Distinctions between user claims, assistant statements, tool evidence, and uncertainty.

    Don't turn suggestions into decisions or guesses into facts. Preserve disagreement
    and uncertainty instead of resolving them without evidence. Don't imply that
    an action was completed or tested unless the conversation establishes it.
    For missing attachments or tool content, say the referenced evidence is not
    included rather than inventing details. Preserve the conversation's language
    when practical; use {locale} as a fallback, not a competing requirement.

    Aim for approximately {target_tokens} tokens. If space is tight, prioritize
    continuity-critical details over generic recap. Return only the record,
    starting with "Previous conversation context:" or an equivalent in its language.

    {additional_context}

    <conversation>
    {content}
    </conversation>
""".trimIndent()
