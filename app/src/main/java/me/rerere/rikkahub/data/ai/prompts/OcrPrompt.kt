package me.rerere.rikkahub.data.ai.prompts

val DEFAULT_OCR_PROMPT = """
    Transcribe the text actually visible in the provided image.

    Preserve original spelling, punctuation, line breaks, reading order, and
    document structure as closely as possible. Use simple Markdown for tables
    or lists and LaTeX only when it faithfully represents visible math.
    Mark genuinely unreadable text as [unreadable] rather than guessing.
    Do not invent, interpret, translate, or correct text.

    When a non-text feature is needed to understand the content (for example,
    an arrow linking two boxes), add a brief factual description of its position
    and relationship. Do not identify people or objects from uncertain appearance.
    Return the transcription and only necessary visual structure, with no preface.
""".trimIndent()
