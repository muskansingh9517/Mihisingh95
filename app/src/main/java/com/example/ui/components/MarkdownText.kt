package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class MarkdownBlock {
    data class TextBlock(val content: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
}

@Composable
fun FormattedAiMessage(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MarkdownBlock.TextBlock -> {
                    MarkdownParagraph(
                        text = block.content,
                        textColor = textColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is MarkdownBlock.CodeBlock -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    CodeBlockCard(language = block.language, code = block.code)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
            if (index < blocks.size - 1 && block is MarkdownBlock.TextBlock) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun MarkdownParagraph(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val annotatedString = remember(text, textColor) {
        buildAnnotatedString {
            val lines = text.split("\n")
            lines.forEachIndexed { lineIdx, line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("### ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)) {
                            append(trimmed.removePrefix("### "))
                        }
                    }
                    trimmed.startsWith("## ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = textColor)) {
                            append(trimmed.removePrefix("## "))
                        }
                    }
                    trimmed.startsWith("# ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = textColor)) {
                            append(trimmed.removePrefix("# "))
                        }
                    }
                    trimmed.startsWith("* ") || trimmed.startsWith("- ") -> {
                        append("  •  ")
                        appendFormattedInlineText(trimmed.substring(2), textColor)
                    }
                    else -> {
                        appendFormattedInlineText(line, textColor)
                    }
                }
                if (lineIdx < lines.size - 1) {
                    append("\n")
                }
            }
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(
            lineHeight = 22.sp,
            color = textColor
        )
    )
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendFormattedInlineText(
    text: String,
    textColor: Color
) {
    var i = 0
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            val endBold = text.indexOf("**", i + 2)
            if (endBold != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                    append(text.substring(i + 2, endBold))
                }
                i = endBold + 2
                continue
            }
        } else if (text.startsWith("`", i) && !text.startsWith("```", i)) {
            val endCode = text.indexOf("`", i + 1)
            if (endCode != -1) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x33888888),
                        fontSize = 13.sp
                    )
                ) {
                    append(" ${text.substring(i + 1, endCode)} ")
                }
                i = endCode + 1
                continue
            }
        } else if (text.startsWith("*", i)) {
            val endItalic = text.indexOf("*", i + 1)
            if (endItalic != -1) {
                withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(text.substring(i + 1, endItalic))
                }
                i = endItalic + 1
                continue
            }
        }

        append(text[i])
        i++
    }
}

@Composable
fun CodeBlockCard(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1E1E2E),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Header bar with language & copy button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181825))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFA6ADC8),
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("code", code)
                        clipboard.setPrimaryClip(clip)
                        copied = true
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(2000)
                            copied = false
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("copy_code_button")
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy Code",
                        tint = if (copied) Color(0xFFA6E3A1) else Color(0xFFA6ADC8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Code content with horizontal scrolling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFFCDD6F4)
                )
            }
        }
    }
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>()
    val codeFenceRegex = Regex("```([a-zA-Z0-9_-]*)\n([\\s\\S]*?)```")

    var lastIndex = 0
    for (match in codeFenceRegex.findAll(text)) {
        val range = match.range
        if (range.first > lastIndex) {
            val textContent = text.substring(lastIndex, range.first).trim()
            if (textContent.isNotEmpty()) {
                result.add(MarkdownBlock.TextBlock(textContent))
            }
        }
        val language = match.groupValues[1]
        val codeContent = match.groupValues[2].trimEnd()
        result.add(MarkdownBlock.CodeBlock(language, codeContent))
        lastIndex = range.last + 1
    }

    if (lastIndex < text.length) {
        val remaining = text.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) {
            result.add(MarkdownBlock.TextBlock(remaining))
        }
    }

    if (result.isEmpty()) {
        result.add(MarkdownBlock.TextBlock(text))
    }
    return result
}
