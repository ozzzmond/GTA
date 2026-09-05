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
    /**
     * Matches standard, extended, and jazz guitar chord notation:
     * Roots: A-G with optional #, b, ♯, or ♭
     * Qualities: m, min, maj, dim, aug, sus, add, alt, +, ø, °, Δ, -
     * Extensions & Tensions: 2, 4, 5, 6, 7, 9, 11, 13, 6/9, 69
     * Alterations: b5, #5, +5, -5, b9, #9, +9, -9, #11, b13, #13, alt
     * Compound / Parenthesized modifiers: e.g. (b9), (#9), (b13), (#11), (b5), (add9), (maj7), (b9,b13)
     * Slash bass: /A-G(#|b|♯|♭)?
     * Special: N.C. / NC (no chord)
     */
    val CHORD_TOKEN_REGEX = Regex(
        "^([A-G][#b♯♭]?" +
        "(?:maj|min|m|M|dim|aug|sus|add|alt|\\+|ø|°|Δ|-)?" +
        "(?:13|11|9|7|6\\/9|69|6|5|4|2)?" +
        "(?:maj7|maj9|maj11|maj13|min7|min9|min11|min13|m7|m9|m11|m13|dim7|aug7|sus4|sus2|sus|add9|add2|add4|add11|add13|alt|m)??" +
        "(?:(?:b|#|\\+|-)(?:5|9|11|13)|alt)*" +
        "(?:\\([#b+\\-a-zA-Z0-9,/:\\s]+\\))?" +
        "(?:(?:b|#|\\+|-)(?:5|9|11|13)|alt)*" +
        "(?:\\/[A-G][#b♯♭]?)?" +
        "|N\\.?C\\.?)$",
        RegexOption.IGNORE_CASE
    )

    /** Matches inline bracketed chord in either square brackets [G] or angle brackets <G> */
    val CHORDPRO_INLINE_REGEX = Regex(
        "[\\[<]([A-G][#b♯♭]?(?:[a-zA-Z0-9#b♯♭\\/+\\-()ø°Δ,]+)?|N\\.?C\\.?)[\\]>]"
    )

    val BRACKETED_CHORD_REGEX = CHORDPRO_INLINE_REGEX

    /** Matches section headers like [Verse 1], <Verse 1>, [Chorus], <Chorus>, [Bridge], [Intro], [Solo] */
    val SECTION_HEADER_REGEX = Regex(
        "^[\\[<]?(Intro|Verse|Chorus|Bridge|Pre-Chorus|Outro|Solo|Interlude|Hook|Tab|Ending|Riff|Instrumental)(?:\\s+[0-9A-Za-z]+)?[\\]>]?:?$",
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
