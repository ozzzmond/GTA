package com.joel.gta.data.parser

object ChordRegex {
    /**
     * Matches standard guitar chord notation:
     * Roots: A-G with optional # or b
     * Qualities: m, min, maj, dim, aug, sus, add
     * Extensions: 2, 4, 5, 6, 7, 9, 11, 13
     * Slash bass: /A-G(#|b)?
     * Special: N.C. (no chord)
     */
    val CHORD_TOKEN_REGEX = Regex(
        "^([A-G][#b]?(?:m|min|maj|dim|aug|sus|add|M)?(?:[0-9]{1,2})?(?:maj7|min7|m7|dim7|aug7|add9|add2|add4|sus2|sus4|b5|#5|b9|#9|-5)?(?:\\/[A-G][#b]?)?|N\\.?C\\.?)$",
        RegexOption.IGNORE_CASE
    )

    /** Matches inline bracketed chord in either square brackets [G] or angle brackets <G> */
    val CHORDPRO_INLINE_REGEX = Regex(
        "[\\[<]([A-G][#b]?(?:[a-zA-Z0-9#b\\/+-]+)?|N\\.?C\\.?)[\\]>]"
    )

    val BRACKETED_CHORD_REGEX = CHORDPRO_INLINE_REGEX

    /** Matches section headers like [Verse 1], <Verse 1>, [Chorus], <Chorus>, [Bridge], [Intro], [Solo] */
    val SECTION_HEADER_REGEX = Regex(
        "^[\\[<]?(Intro|Verse|Chorus|Bridge|Pre-Chorus|Outro|Solo|Interlude|Hook|Tab|Ending|Riff|Instrumental).*?[\\]>]?$",
        RegexOption.IGNORE_CASE
    )

    /** Matches ChordPro directives like {title: Song Name}, {artist: ...}, {key: ...} */
    val CHORDPRO_DIRECTIVE_REGEX = Regex(
        "^\\{([a-zA-Z_-]+):?\\s*(.*?)\\}$"
    )

    /** Detects guitar tablature lines like e|---0-1-3---| */
    val TAB_LINE_REGEX = Regex(
        "^[eBGDAE]?[\\|:][-0-9hprbp/\\\\~xX|: ]+$",
        RegexOption.IGNORE_CASE
    )
}
