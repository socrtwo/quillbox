package info.socrtwo.quillbox.ui.compose

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration

/** Toolbar formatting actions. */
enum class TextStyleKind { BOLD, ITALIC, UNDERLINE }

private fun styleFor(kind: TextStyleKind): SpanStyle = when (kind) {
    TextStyleKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    TextStyleKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    TextStyleKind.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
}

/**
 * Applies the given style to the current selection, preserving any existing spans.
 * If there is no selection the value is returned unchanged.
 */
fun applyStyleToSelection(value: TextFieldValue, kind: TextStyleKind): TextFieldValue {
    val sel = value.selection
    if (sel.collapsed) return value
    val start = minOf(sel.start, sel.end)
    val end = maxOf(sel.start, sel.end)
    val newStyle = styleFor(kind)

    val annotated = buildAnnotatedString {
        append(value.annotatedString)
        addStyle(newStyle, start, end)
    }
    return value.copy(annotatedString = annotated)
}

/** Inserts a list marker at the start of the line containing the cursor. */
fun insertLineMarker(value: TextFieldValue, marker: String): TextFieldValue {
    val text = value.text
    val cursor = value.selection.start.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val newText = text.substring(0, lineStart) + marker + text.substring(lineStart)
    return TextFieldValue(
        annotatedString = AnnotatedString(newText),
        selection = androidx.compose.ui.text.TextRange(cursor + marker.length)
    )
}

/**
 * Serializes an [AnnotatedString] to lightweight HTML, honoring bold/italic/underline
 * spans and converting newlines to <br>. Good enough for sending formatted mail.
 */
fun AnnotatedString.toSimpleHtml(): String {
    if (isEmpty()) return "<div></div>"

    fun esc(c: Char): String = when (c) {
        '<' -> "&lt;"
        '>' -> "&gt;"
        '&' -> "&amp;"
        '\n' -> "<br>"
        else -> c.toString()
    }

    val sb = StringBuilder("<div>")
    var prevBold = false
    var prevItalic = false
    var prevUnderline = false

    fun openClose(bold: Boolean, italic: Boolean, underline: Boolean) {
        // Close in reverse order when turning off.
        if (prevUnderline && !underline) sb.append("</u>")
        if (prevItalic && !italic) sb.append("</i>")
        if (prevBold && !bold) sb.append("</b>")
        if (!prevBold && bold) sb.append("<b>")
        if (!prevItalic && italic) sb.append("<i>")
        if (!prevUnderline && underline) sb.append("<u>")
        prevBold = bold; prevItalic = italic; prevUnderline = underline
    }

    for (i in indices) {
        val active = spanStyles.filter { i in it.start until it.end }.map { it.item }
        val bold = active.any { (it.fontWeight?.weight ?: 0) >= FontWeight.Bold.weight }
        val italic = active.any { it.fontStyle == FontStyle.Italic }
        val underline = active.any { it.textDecoration == TextDecoration.Underline }
        openClose(bold, italic, underline)
        sb.append(esc(this[i]))
    }
    openClose(bold = false, italic = false, underline = false)
    sb.append("</div>")
    return sb.toString()
}
