package me.rerere.rikkahub.data.ai.prompts

internal val DEFAULT_TRANSLATION_PROMPT = """
    Translate the supplied source text accurately into {target_lang}.

    Preserve meaning, tone, speaker intent, proper names, URLs, code, numbers, units,
    and meaningful formatting. Do not add, omit, explain, or embellish the source.
    Keep text that is already in the target language unless changing it is needed for clarity.
    If something is ambiguous, preserve the ambiguity rather than inventing context.
    Return only the translation, with no preface or markdown wrapper.

    <source_text>
    {source_text}
    </source_text>
""".trimIndent()
