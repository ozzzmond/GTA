package com.joel.gta.ui.components

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joel.gta.data.parser.ChordRegex
import com.joel.gta.data.model.SongLine
import com.joel.gta.ui.theme.ChordMonospaceStyle
import com.joel.gta.ui.theme.LocalGtaColors
import com.joel.gta.ui.theme.LyricMonospaceStyle
import com.joel.gta.ui.theme.SongFontStyle

@Composable
fun MetaBadge(label: String) {
    val customColors = LocalGtaColors.current
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = customColors.chordAccent.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.chordAccent.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = customColors.chordAccent
        )
    }
}

fun splitSongLinesForColumns(lines: List<SongLine>): Pair<List<SongLine>, List<SongLine>> {
    if (lines.size <= 4) return lines to emptyList()
    val mid = lines.size / 2
    var splitIndex = mid
    val searchRange = (mid - 6).coerceAtLeast(1)..(mid + 6).coerceAtMost(lines.size - 2)
    for (i in searchRange) {
        if (lines[i] is SongLine.SectionHeader) {
            splitIndex = i
            break
        }
    }
    if (lines.getOrNull(splitIndex) is SongLine.LyricLine && lines.getOrNull(splitIndex - 1) is SongLine.ChordLine) splitIndex--
    return lines.take(splitIndex) to lines.drop(splitIndex)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun RenderSongLine(
    line: SongLine,
    fontSizeSp: Float,
    songFontStyle: SongFontStyle = SongFontStyle.MONOSPACE,
    onChordClick: (String) -> Unit = {}
) {
    val customColors = LocalGtaColors.current
    when (line) {
        is SongLine.SectionHeader -> {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "[${line.title}]",
                fontFamily = songFontStyle.fontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (fontSizeSp + 1).sp,
                lineHeight = ((fontSizeSp + 1) * 1.35f).sp,
                letterSpacing = if (songFontStyle == SongFontStyle.MONOSPACE) 0.8.sp else 0.5.sp,
                color = customColors.sectionHeader,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
            )
        }

        is SongLine.ChordLine -> {
            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = line.chords,
                style = ChordMonospaceStyle.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = songFontStyle.chordFontWeight,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.35f).sp,
                    letterSpacing = 0.8.sp,
                    color = customColors.chordAccent
                ),
                onTextLayout = { layoutResult = it },
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 1.dp)
                    .pointerInput(line.chords) {
                        detectTapGestures { tapOffset ->
                            layoutResult?.let { layout ->
                                val offset = layout.getOffsetForPosition(tapOffset)
                                val chord = extractChordAtOffset(line.chords, offset)
                                if (chord != null) {
                                    onChordClick(chord)
                                }
                            }
                        }
                    }
            )
        }

        is SongLine.LyricLine -> {
            Text(
                text = line.lyrics,
                style = LyricMonospaceStyle.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = songFontStyle.lyricFontWeight,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.35f).sp,
                    letterSpacing = 0.8.sp,
                    color = customColors.textPrimary
                ),
                modifier = Modifier.padding(top = 1.dp, bottom = 5.dp)
            )
        }

        is SongLine.ChordProLine -> {
            FlowRow(modifier = Modifier.padding(vertical = 3.dp)) {
                line.segments.forEach { segment ->
                    Column {
                        Text(
                            text = segment.chord ?: " ",
                            color = customColors.chordAccent,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = songFontStyle.chordFontWeight,
                            fontSize = (fontSizeSp * 0.85f).sp,
                            modifier = Modifier.padding(bottom = 4.dp).then(
                                if (segment.chord != null) Modifier.clickable { onChordClick(segment.chord) } else Modifier
                            )
                        )
                        Text(
                            text = segment.text.ifEmpty { " " },
                            color = customColors.textPrimary,
                            fontFamily = songFontStyle.fontFamily,
                            fontWeight = songFontStyle.lyricFontWeight,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.4f).sp
                        )
                    }
                }
            }
        }

        is SongLine.TabLine -> {
            val tabFontSize = (fontSizeSp - 1f).coerceAtLeast(11f)
            Text(
                text = line.content,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                fontSize = tabFontSize.sp,
                lineHeight = (tabFontSize * 1.25f).sp,
                letterSpacing = 0.8.sp,
                color = customColors.tabLineColor,
                modifier = Modifier.padding(vertical = 1.5.dp)
            )
        }

        is SongLine.EmptyLine -> {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

fun extractChordAtOffset(text: String, offset: Int): String? {
    if (text.isBlank() || offset !in text.indices) {
        return null
    }

    val isDelim = { c: Char -> c.isWhitespace() || c in "-–—|,:;[]{}" }

    if (isDelim(text[offset])) {
        if (offset > 0 && !isDelim(text[offset - 1])) {
            return extractChordAtOffset(text, offset - 1)
        }
        if (offset + 1 in text.indices && !isDelim(text[offset + 1])) {
            return extractChordAtOffset(text, offset + 1)
        }
        return null
    }

    var start = offset
    while (start > 0 && !isDelim(text[start - 1])) {
        start--
    }

    var end = offset
    while (end < text.length && !isDelim(text[end])) {
        end++
    }

    val word = text.substring(start, end).trim(' ', '\t', '[', ']', '{', '}', ',', ';', ':', '-', '–', '—', '|')

    if (word.isNotBlank() && ChordRegex.CHORD_TOKEN_REGEX.matches(word)) {
        return word
    }

    if (word.startsWith("(") && word.endsWith(")")) {
        val unwrapped = word.substring(1, word.length - 1).trim()
        if (ChordRegex.CHORD_TOKEN_REGEX.matches(unwrapped)) {
            return unwrapped
        }
    }

    val cleaned = word.trim('(', ')', '/', '.')
    if (cleaned.isNotBlank() && ChordRegex.CHORD_TOKEN_REGEX.matches(cleaned)) {
        return cleaned
    }

    return null
}
