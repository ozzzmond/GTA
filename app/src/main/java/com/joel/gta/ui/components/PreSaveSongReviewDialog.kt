package com.joel.gta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joel.gta.data.model.ParsedSong
import com.joel.gta.data.model.SongLine
import com.joel.gta.data.parser.SongParser
import com.joel.gta.data.scraper.ScrapedSong
import com.joel.gta.ui.theme.LocalGtaColors

private enum class ReviewMode {
    EDIT_RAW,
    LIVE_PREVIEW
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreSaveSongReviewDialog(
    scrapedSong: ScrapedSong,
    dialogTitle: String = "Review & Edit Song",
    saveButtonText: String = "Save to Songbook",
    initialTags: String = "",
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String?, rawContent: String, key: String?, capo: String?, tags: String) -> Unit
) {
    val customColors = LocalGtaColors.current
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 840 ||
            (configuration.screenWidthDp >= 600 && configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)

    var songTitle by remember(scrapedSong) { mutableStateOf(scrapedSong.title) }
    var songArtist by remember(scrapedSong) { mutableStateOf(scrapedSong.artist ?: "") }
    var songKey by remember(scrapedSong) { mutableStateOf(scrapedSong.key ?: "") }
    var songCapo by remember(scrapedSong) { mutableStateOf(scrapedSong.capo ?: "") }
    var songTags by remember(scrapedSong) { mutableStateOf(initialTags) }
    var rawText by remember(scrapedSong) { mutableStateOf(scrapedSong.rawContent) }
    var activeMode by remember { mutableStateOf(ReviewMode.LIVE_PREVIEW) }


    // Real-time live parsed song
    val parsedLiveSong: ParsedSong = remember(rawText, songTitle) {
        SongParser.parse(rawText, defaultTitle = songTitle.ifBlank { "Untitled Song" }).let { base ->
            base.copy(
                artist = songArtist.takeIf { it.isNotBlank() } ?: base.artist,
                key = songKey.takeIf { it.isNotBlank() } ?: base.key,
                capo = songCapo.takeIf { it.isNotBlank() } ?: base.capo
            )
        }
    }

    val textSelectionColors = remember(customColors.chordAccent) {
        TextSelectionColors(
            handleColor = customColors.chordAccent,
            backgroundColor = customColors.chordAccent.copy(alpha = 0.35f)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isWideScreen) 24.dp else 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = customColors.surfaceBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(customColors.canvasBackground)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = customColors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = dialogTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = customColors.textPrimary
                            )
                            val sourceLabel = if (scrapedSong.sourceUrl.isNotBlank()) {
                                "Source: ${scrapedSong.sourceUrl.take(45)}${if (scrapedSong.sourceUrl.length > 45) "..." else ""}"
                            } else {
                                "Source: Songbook / Text Ingest"
                            }
                            Text(
                                text = sourceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = customColors.chordAccent
                            )
                        }
                    }

                    // Save Button
                    Button(
                        onClick = {
                            onSave(
                                songTitle.trim(),
                                songArtist.trim().takeIf { it.isNotBlank() },
                                rawText.trim(),
                                songKey.trim().takeIf { it.isNotBlank() },
                                songCapo.trim().takeIf { it.isNotBlank() },
                                songTags.trim()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.chordAccent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = saveButtonText,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                HorizontalDivider(color = customColors.divider, thickness = 1.dp)

                // Editable Metadata Strip (Title, Artist, Key, Capo)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = songTitle,
                        onValueChange = { songTitle = it },
                        label = { Text("Song Title *") },
                        singleLine = true,
                        modifier = Modifier.weight(1.8f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = customColors.chordAccent,
                            unfocusedBorderColor = customColors.divider,
                            focusedTextColor = customColors.textPrimary,
                            unfocusedTextColor = customColors.textPrimary,
                            cursorColor = customColors.chordAccent,
                            selectionColors = textSelectionColors
                        )
                    )

                    OutlinedTextField(
                        value = songArtist,
                        onValueChange = { songArtist = it },
                        label = { Text("Artist") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = customColors.chordAccent,
                            unfocusedBorderColor = customColors.divider,
                            focusedTextColor = customColors.textPrimary,
                            unfocusedTextColor = customColors.textPrimary,
                            cursorColor = customColors.chordAccent,
                            selectionColors = textSelectionColors
                        )
                    )

                    OutlinedTextField(
                        value = songKey,
                        onValueChange = { songKey = it },
                        label = { Text("Key") },
                        singleLine = true,
                        modifier = Modifier.width(80.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = customColors.chordAccent,
                            unfocusedBorderColor = customColors.divider,
                            focusedTextColor = customColors.textPrimary,
                            unfocusedTextColor = customColors.textPrimary,
                            cursorColor = customColors.chordAccent,
                            selectionColors = textSelectionColors
                        )
                    )

                    OutlinedTextField(
                        value = songCapo,
                        onValueChange = { songCapo = it },
                        label = { Text("Capo") },
                        singleLine = true,
                        modifier = Modifier.width(90.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = customColors.chordAccent,
                            unfocusedBorderColor = customColors.divider,
                            focusedTextColor = customColors.textPrimary,
                            unfocusedTextColor = customColors.textPrimary,
                            cursorColor = customColors.chordAccent,
                            selectionColors = textSelectionColors
                        )
                    )
                }

                // Tags Input & Quick Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = songTags,
                        onValueChange = { songTags = it },
                        label = { Text("Tags (e.g. OPM, Acoustic, Rock)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = customColors.chordAccent,
                            unfocusedBorderColor = customColors.divider,
                            focusedTextColor = customColors.textPrimary,
                            unfocusedTextColor = customColors.textPrimary,
                            cursorColor = customColors.chordAccent,
                            selectionColors = textSelectionColors
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.LocalOffer, contentDescription = null, tint = customColors.chordAccent, modifier = Modifier.size(18.dp))
                        }
                    )

                    // Quick tag suggestions
                    val quickTags = listOf("Worship", "OPM", "Acoustic", "Rock", "Slow Rock", "Encore")
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        items(quickTags.size) { idx ->
                            val tag = quickTags[idx]
                            val isSelected = songTags.split(",").any { it.trim().equals(tag, ignoreCase = true) }
                            SuggestionChip(
                                onClick = {
                                    val currentList = songTags.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                                    if (isSelected) {
                                        currentList.removeAll { it.equals(tag, ignoreCase = true) }
                                    } else {
                                        currentList.add(tag)
                                    }
                                    songTags = currentList.joinToString(", ")
                                },
                                label = { Text(tag, fontSize = 11.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (isSelected) customColors.chordAccent.copy(alpha = 0.25f) else customColors.canvasBackground,
                                    labelColor = if (isSelected) customColors.chordAccent else customColors.textSecondary
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) customColors.chordAccent else customColors.divider
                                )
                            )
                        }
                    }
                }


                // Mode Selector Bar (Only needed if not side-by-side wide screen)
                if (!isWideScreen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { activeMode = ReviewMode.EDIT_RAW },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (activeMode == ReviewMode.EDIT_RAW) customColors.chordAccent.copy(alpha = 0.25f) else customColors.canvasBackground,
                                contentColor = if (activeMode == ReviewMode.EDIT_RAW) customColors.chordAccent else customColors.textSecondary
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (activeMode == ReviewMode.EDIT_RAW) customColors.chordAccent else customColors.divider
                            )
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Raw Text", fontWeight = FontWeight.SemiBold)
                        }

                        FilledTonalButton(
                            onClick = { activeMode = ReviewMode.LIVE_PREVIEW },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (activeMode == ReviewMode.LIVE_PREVIEW) customColors.chordAccent.copy(alpha = 0.25f) else customColors.canvasBackground,
                                contentColor = if (activeMode == ReviewMode.LIVE_PREVIEW) customColors.chordAccent else customColors.textSecondary
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (activeMode == ReviewMode.LIVE_PREVIEW) customColors.chordAccent else customColors.divider
                            )
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Live Preview", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Content Viewport: Side-by-Side (Tablet) or Tab-Switched (Phone)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isWideScreen) {
                        // Side-by-side: Left is Raw Editor, Right is Live Preview
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "RAW CHORDS & LYRICS (EDITABLE)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = customColors.textSecondary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                RawTextEditorField(
                                    text = rawText,
                                    onTextChanged = { rawText = it },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            VerticalDivider(color = customColors.divider, thickness = 1.dp)

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "LIVE STAGE PREVIEW",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = customColors.chordAccent,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                LivePreviewContainer(
                                    parsedSong = parsedLiveSong,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        // Phone Tab View
                        when (activeMode) {
                            ReviewMode.EDIT_RAW -> {
                                RawTextEditorField(
                                    text = rawText,
                                    onTextChanged = { rawText = it },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            ReviewMode.LIVE_PREVIEW -> {
                                LivePreviewContainer(
                                    parsedSong = parsedLiveSong,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun RawTextEditorField(
    text: String,
    onTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val customColors = LocalGtaColors.current
    val clipboardManager = LocalClipboardManager.current
    val textSelectionColors = remember(customColors.chordAccent) {
        TextSelectionColors(
            handleColor = customColors.chordAccent,
            backgroundColor = customColors.chordAccent.copy(alpha = 0.35f)
        )
    }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length)))
    }

    LaunchedEffect(text) {
        if (textFieldValue.text != text) {
            textFieldValue = textFieldValue.copy(text = text)
        }
    }

    fun markSelectionAsChord() {
        val currentText = textFieldValue.text
        val selection = textFieldValue.selection

        if (!selection.collapsed) {
            // Wrap the selected range in brackets [selected]
            val min = selection.min
            val max = selection.max
            val selected = currentText.substring(min, max).trim()
            val wrapped = if (selected.startsWith("[") && selected.endsWith("]")) {
                selected
            } else {
                "[$selected]"
            }
            val newText = currentText.replaceRange(min, max, wrapped)
            val newSelection = TextRange(min, min + wrapped.length)
            textFieldValue = TextFieldValue(text = newText, selection = newSelection)
            onTextChanged(newText)
        } else {
            val cursor = selection.start
            if (currentText.isEmpty()) {
                val newText = "[]"
                textFieldValue = TextFieldValue(text = newText, selection = TextRange(1))
                onTextChanged(newText)
                return
            }

            // Find word boundaries around cursor
            var start = cursor
            while (start > 0 && !currentText[start - 1].isWhitespace() && currentText[start - 1] !in "[]\n") {
                start--
            }
            var end = cursor
            while (end < currentText.length && !currentText[end].isWhitespace() && currentText[end] !in "[]\n") {
                end++
            }

            if (start < end) {
                val word = currentText.substring(start, end)
                val wrapped = if (word.startsWith("[") && word.endsWith("]")) word else "[$word]"
                val newText = currentText.replaceRange(start, end, wrapped)
                val newSelection = TextRange(start + wrapped.length)
                textFieldValue = TextFieldValue(text = newText, selection = newSelection)
                onTextChanged(newText)
            } else {
                val newText = currentText.substring(0, cursor) + "[]" + currentText.substring(cursor)
                textFieldValue = TextFieldValue(text = newText, selection = TextRange(cursor + 1))
                onTextChanged(newText)
            }
        }
    }

    fun markSelectionAsSection() {
        val currentText = textFieldValue.text
        val selection = textFieldValue.selection

        if (!selection.collapsed) {
            val min = selection.min
            val max = selection.max
            val selected = currentText.substring(min, max).trim()
            val wrapped = if (selected.startsWith("[") && selected.endsWith("]")) {
                selected
            } else {
                "[$selected]"
            }
            val newText = currentText.replaceRange(min, max, wrapped)
            val newSelection = TextRange(min, min + wrapped.length)
            textFieldValue = TextFieldValue(text = newText, selection = newSelection)
            onTextChanged(newText)
        } else {
            val cursor = selection.start
            // Find start and end of current line
            val lineStart = currentText.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
            val lineEnd = currentText.indexOf('\n', cursor).let { if (it == -1) currentText.length else it }
            val lineContent = currentText.substring(lineStart, lineEnd).trim()

            if (lineContent.isNotEmpty() && !lineContent.startsWith("[") && !lineContent.endsWith("]")) {
                // Wrap the whole line into a section header
                val wrapped = "[$lineContent]"
                val newText = currentText.substring(0, lineStart) + wrapped + currentText.substring(lineEnd)
                val newSelection = TextRange(lineStart, lineStart + wrapped.length)
                textFieldValue = TextFieldValue(text = newText, selection = newSelection)
                onTextChanged(newText)
            } else {
                // Insert [Section] with "Section" selected so user can directly type custom tag name
                val placeholder = "[Section]"
                val newText = currentText.substring(0, cursor) + placeholder + currentText.substring(cursor)
                val newSelection = TextRange(cursor + 1, cursor + 8)
                textFieldValue = TextFieldValue(text = newText, selection = newSelection)
                onTextChanged(newText)
            }
        }
    }

    Column(modifier = modifier) {
        // Quick Action Text Editing Bar ([Chords], [Section], Paste, Copy All, Clear)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = customColors.surfaceBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { markSelectionAsChord() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.LibraryMusic,
                            contentDescription = "Mark Selection as Chord",
                            modifier = Modifier.size(15.dp),
                            tint = customColors.chordAccent
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "[Chords]",
                            color = customColors.chordAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    VerticalDivider(modifier = Modifier.height(16.dp), color = customColors.divider)

                    TextButton(
                        onClick = { markSelectionAsSection() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = "Mark Selection as Section Header",
                            modifier = Modifier.size(15.dp),
                            tint = customColors.sectionHeader
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "[Section]",
                            color = customColors.sectionHeader,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    VerticalDivider(modifier = Modifier.height(16.dp), color = customColors.divider)

                    TextButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                onTextChanged(clip)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp), tint = customColors.chordAccent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste", color = customColors.chordAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    VerticalDivider(modifier = Modifier.height(16.dp), color = customColors.divider)

                    TextButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(text))
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp), tint = customColors.textSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy All", color = customColors.textPrimary, fontSize = 12.sp)
                    }

                    VerticalDivider(modifier = Modifier.height(16.dp), color = customColors.divider)

                    TextButton(
                        onClick = { onTextChanged("") },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(15.dp), tint = customColors.textSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", color = customColors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
            SelectionContainer(modifier = Modifier.weight(1f).fillMaxWidth()) {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        if (newValue.text != text) {
                            onTextChanged(newValue.text)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = customColors.textPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = customColors.textPrimary,
                        unfocusedTextColor = customColors.textPrimary,
                        focusedContainerColor = customColors.canvasBackground,
                        unfocusedContainerColor = customColors.canvasBackground,
                        focusedBorderColor = customColors.chordAccent,
                        unfocusedBorderColor = customColors.divider,
                        cursorColor = customColors.chordAccent,
                        selectionColors = textSelectionColors
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }
    }
}

@Composable
private fun LivePreviewContainer(
    parsedSong: ParsedSong,
    modifier: Modifier = Modifier
) {
    val customColors = LocalGtaColors.current
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = customColors.canvasBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Header badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                if (!parsedSong.key.isNullOrBlank()) {
                    BadgePill("KEY: ${parsedSong.key}")
                }
                if (!parsedSong.capo.isNullOrBlank()) {
                    BadgePill("CAPO: ${parsedSong.capo}")
                }
                BadgePill(parsedSong.format.name.replace("_", " "))
            }

            HorizontalDivider(color = customColors.divider, thickness = 1.dp, modifier = Modifier.padding(bottom = 12.dp))

            parsedSong.lines.forEach { line ->
                when (line) {
                    is SongLine.SectionHeader -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "[${line.title}]",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = customColors.sectionHeader
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    is SongLine.ChordLine -> {
                        Text(
                            text = line.chords,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = customColors.chordAccent
                        )
                    }
                    is SongLine.LyricLine -> {
                        Text(
                            text = line.lyrics,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            color = customColors.textPrimary
                        )
                    }
                    is SongLine.ChordProLine -> {
                        val annotated = buildAnnotatedString {
                            line.segments.forEach { segment ->
                                if (segment.chord != null) {
                                    withStyle(SpanStyle(color = customColors.chordAccent, fontWeight = FontWeight.Bold)) {
                                        append("[${segment.chord}]")
                                    }
                                }
                                withStyle(SpanStyle(color = customColors.textPrimary, fontWeight = FontWeight.Normal)) {
                                    append(segment.text)
                                }
                            }
                        }
                        Text(
                            text = annotated,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                    is SongLine.TabLine -> {
                        Text(
                            text = line.content,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = customColors.tabLineColor
                        )
                    }
                    is SongLine.EmptyLine -> {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgePill(text: String) {
    val customColors = LocalGtaColors.current
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = customColors.surfaceBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.divider)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = customColors.chordAccent,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
