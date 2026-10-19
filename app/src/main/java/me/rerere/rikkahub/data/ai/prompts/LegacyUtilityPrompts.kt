package me.rerere.rikkahub.data.ai.prompts

// Exact previous defaults, kept only to identify unchanged saved copies.
internal val LEGACY_TITLE_PROMPT = """
    I will give you some dialogue content in the `<content>` block.
    You need to summarize the conversation between user and assistant into a short title.
    1. The title language should be consistent with the user's primary language
    2. Do not use punctuation or other special symbols
    3. Reply directly with the title
    4. Summarize using {locale} language
    5. The title should not exceed 10 characters

    <content>
    {content}
    </content>
""".trimIndent()

internal val LEGACY_SUGGESTION_PROMPT = """
    I will provide you with some chat content in the `<content>` block, including conversations between the User and the AI assistant.
    You need to act as the **User** to reply to the assistant, generating 3~5 appropriate and contextually relevant responses to help the assistant improve its answers.

    Rules:
    1. Reply directly with suggestions, do not add any formatting, and separate suggestions with newlines, no need to add markdown list formats.
    2. Use {locale} language.
    3. Ensure each suggestion is valid.
    4. Each suggestion should not exceed 10 characters.
    5. Imitate the user's previous conversational style.
    6. Act as a User, not an Assistant!

    <content>
    {content}
    </content>
""".trimIndent()

internal val LEGACY_TRANSLATION_PROMPT = """
    You are a translation expert, skilled in translating various languages, and maintaining accuracy, faithfulness, and elegance in translation.
    Next, I will send you text. Please translate it into {target_lang}, and return the translation result directly, without adding any explanations or other content.

    Please translate the <source_text> section:

    <source_text>
    {source_text}
    </source_text>
""".trimIndent()

val LEGACY_OCR_PROMPT =
    """
    You are an OCR assistant.

    Extract all visible text from the image and also describe any non-text elements (icons, shapes, arrows, objects, symbols, or emojis).

    For each element, specify:
    - The exact text (for text) or a short description (for non-text).
    - For document-type content, please use markdown and latex format.
    - If there are objects like buildings or characters, try to identify who they are.
    - Its approximate position in the image (e.g., 'top left', 'center right', 'bottom middle').
    - Its spatial relationship to nearby elements (e.g., 'above', 'below', 'next to', 'on the left of').

    Keep the original reading order and layout structure as much as possible.
    Do not interpret or translate—only transcribe and describe what is visually present.
    """.trimIndent()

internal val LEGACY_COMPRESS_PROMPT = """
    You are a conversation compression assistant. Compress the following conversation into a concise summary.

    Requirements:
    1. Preserve key facts, decisions, and important context that would be needed to continue the conversation
    2. Keep the summary in the same language as the original conversation
    3. Target approximately {target_tokens} tokens
    4. Output the summary directly without any explanations or meta-commentary
    5. Format the summary as context information that can be used to continue the conversation
    6. Use {locale} language
    7. Start the output with a clear indicator that this is a summary (e.g., "[Summary of previous conversation]" or equivalent in the target language)

    {additional_context}

    <conversation>
    {content}
    </conversation>
""".trimIndent()
