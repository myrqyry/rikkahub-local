package me.rerere.tts.kitten

/**
 * Kitten TTS Nano tokenizer — ported from KittenML's Python reference.
 *
 * Python upstream:
 *   self._word_index_dictionary = {symbol: i for i, symbol in enumerate(list(
 *     '$;:,.!?¡¿—…"«»"" ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'
 *     'ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃˈˌːˑʼʴʰʱʲʷˠˤ˞↓↑→↗↘\'̩\'ᵻ'))}
 *   def generate:  input_ids = [[0] + [dict[c] for c in ' '.join(re.findall(r"\w+|[^\w\s]", phonemized)) if c in dict] + [0]]
 *
 * The Python dict construction overwrites duplicate keys (\" appears 3×, ' appears 2×),
 * so the final map size is 175, not 178. We replicate that by enumerating and overwriting.
 *
 * Without espeak-ng on device, we fall back to direct grapheme tokenization (still produces audio,
 * but phonemized IPA yields better quality). The same mapping works for IPA strings.
 */
class KittenTtsTokenizer {

    // Exact codepoints of the original Python string, in order — avoids escaping hell.
    private val symbolCodePoints = intArrayOf(
        0x0024, 0x003B, 0x003A, 0x002C, 0x002E, 0x0021, 0x003F, 0x00A1, 0x00BF, 0x2014,
        0x2026, 0x0022, 0x00AB, 0x00BB, 0x0022, 0x0022, 0x0020,
        0x0041, 0x0042, 0x0043, 0x0044, 0x0045, 0x0046, 0x0047, 0x0048, 0x0049, 0x004A, 0x004B, 0x004C, 0x004D, 0x004E, 0x004F,
        0x0050, 0x0051, 0x0052, 0x0053, 0x0054, 0x0055, 0x0056, 0x0057, 0x0058, 0x0059, 0x005A,
        0x0061, 0x0062, 0x0063, 0x0064, 0x0065, 0x0066, 0x0067, 0x0068, 0x0069, 0x006A, 0x006B, 0x006C, 0x006D, 0x006E, 0x006F,
        0x0070, 0x0071, 0x0072, 0x0073, 0x0074, 0x0075, 0x0076, 0x0077, 0x0078, 0x0079, 0x007A,
        0x0251, 0x0250, 0x0252, 0x00E6, 0x0253, 0x0299, 0x03B2, 0x0254, 0x0255, 0x00E7, 0x0257, 0x0256, 0x00F0, 0x02A4, 0x0259,
        0x0258, 0x025A, 0x025B, 0x025C, 0x025D, 0x025E, 0x025F, 0x0284, 0x0261, 0x0260, 0x0262, 0x029B, 0x0266, 0x0267, 0x0127,
        0x0265, 0x029C, 0x0268, 0x026A, 0x029D, 0x026D, 0x026C, 0x026B, 0x026E, 0x029F, 0x0271, 0x026F, 0x0270, 0x014B, 0x0273,
        0x0272, 0x0274, 0x00F8, 0x0275, 0x0278, 0x03B8, 0x0153, 0x0276, 0x0298, 0x0279, 0x027A, 0x027E, 0x027B, 0x0280, 0x0281,
        0x027D, 0x0282, 0x0283, 0x0288, 0x02A7, 0x0289, 0x028A, 0x028B, 0x2C71, 0x028C, 0x0263, 0x0264, 0x028D, 0x03C7, 0x028E,
        0x028F, 0x0291, 0x0290, 0x0292, 0x0294, 0x02A1, 0x0295, 0x02A2, 0x01C0, 0x01C1, 0x01C2, 0x01C3, 0x02C8, 0x02CC, 0x02D0,
        0x02D1, 0x02BC, 0x02B4, 0x02B0, 0x02B1, 0x02B2, 0x02B7, 0x02E0, 0x02E4, 0x02DE, 0x2193, 0x2191, 0x2192, 0x2197, 0x2198,
        0x0027, 0x0329, 0x0027, 0x1D7B
    )

    val symbols: List<String> = symbolCodePoints.map { String(Character.toChars(it)) }

    // Mirrors Python: {symbol: i for i, symbol in enumerate(list(...))} — last write wins on duplicates
    val wordIndex: Map<String, Int> = buildMap {
        for ((i, sym) in symbols.withIndex()) {
            put(sym, i)
        }
    }

    val vocabSize: Int = wordIndex.size

    // Pre-compiled regex \w+|[^\w\s] — same as Python re.findall
    private val wordRegex = Regex("""\w+|[^\w\s]""")

    /**
     * Encode raw or IPA text to token ids with BOS=0 and EOS=0 bookends.
     * Steps: split via regex, join with single space, map each codepoint if in vocab.
     */
    fun encode(text: String): List<Int> {
        if (text.isBlank()) return listOf(0, 0)
        val tokens = wordRegex.findAll(text).map { it.value }.joinToString(" ")
        val ids = mutableListOf<Int>()
        // Iterate by codepoints to handle combining char U+0329 correctly
        var idx = 0
        while (idx < tokens.length) {
            val cp = tokens.codePointAt(idx)
            val ch = String(Character.toChars(cp))
            wordIndex[ch]?.let { ids.add(it) }
            idx += Character.charCount(cp)
        }
        return listOf(0) + ids + listOf(0)
    }

    companion object {
        const val BOS_ID = 0
        const val EOS_ID = 0

        // Canonical 8 voices from KittenML/kitten-tts-nano-0.1 voices.npz
        val DEFAULT_VOICES = listOf(
            "expr-voice-2-m", "expr-voice-2-f",
            "expr-voice-3-m", "expr-voice-3-f",
            "expr-voice-4-m", "expr-voice-4-f",
            "expr-voice-5-m", "expr-voice-5-f",
        )
    }
}
