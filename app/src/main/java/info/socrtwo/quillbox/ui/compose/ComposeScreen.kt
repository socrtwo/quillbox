package info.socrtwo.quillbox.ui.compose

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.socrtwo.quillbox.data.mail.OutgoingAttachment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    onSent: () -> Unit,
    onBack: () -> Unit,
    viewModel: ComposeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var body by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(state.sent) { if (state.sent) onSent() }

    val attachLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            readAttachment(context, uri)?.let { viewModel.addAttachment(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compose") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = !state.sending,
                        onClick = {
                            viewModel.send(
                                htmlBody = body.annotatedString.toSimpleHtml(),
                                plainBody = body.text
                            )
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            OutlinedTextField(
                value = state.to,
                onValueChange = { v -> viewModel.update { copy(to = v) } },
                label = { Text("To") },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { viewModel.toggleCcBcc() }) {
                Text(if (state.showCcBcc) "Hide Cc/Bcc" else "Add Cc/Bcc")
            }
            if (state.showCcBcc) {
                OutlinedTextField(
                    value = state.cc,
                    onValueChange = { v -> viewModel.update { copy(cc = v) } },
                    label = { Text("Cc") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.bcc,
                    onValueChange = { v -> viewModel.update { copy(bcc = v) } },
                    label = { Text("Bcc") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = state.subject,
                onValueChange = { v -> viewModel.update { copy(subject = v) } },
                label = { Text("Subject") },
                modifier = Modifier.fillMaxWidth()
            )

            FormattingToolbar(
                onStyle = { kind -> body = applyStyleToSelection(body, kind) },
                onBullet = { body = insertLineMarker(body, "• ") },
                onNumbered = { body = insertLineMarker(body, "1. ") },
                onAttach = { attachLauncher.launch(arrayOf("*/*")) }
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Message") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
            )

            if (state.attachments.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                state.attachments.forEach { att ->
                    AssistChip(
                        onClick = { viewModel.removeAttachment(att) },
                        label = { Text("${att.fileName} ✕") },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun FormattingToolbar(
    onStyle: (TextStyleKind) -> Unit,
    onBullet: () -> Unit,
    onNumbered: () -> Unit,
    onAttach: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(onClick = { onStyle(TextStyleKind.BOLD) }) {
            Icon(Icons.Filled.FormatBold, contentDescription = "Bold")
        }
        IconButton(onClick = { onStyle(TextStyleKind.ITALIC) }) {
            Icon(Icons.Filled.FormatItalic, contentDescription = "Italic")
        }
        IconButton(onClick = { onStyle(TextStyleKind.UNDERLINE) }) {
            Icon(Icons.Filled.FormatUnderlined, contentDescription = "Underline")
        }
        IconButton(onClick = onBullet) {
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Bulleted list")
        }
        IconButton(onClick = onNumbered) {
            Icon(Icons.Filled.FormatListNumbered, contentDescription = "Numbered list")
        }
        IconButton(onClick = onAttach) {
            Icon(Icons.Filled.AttachFile, contentDescription = "Attach")
        }
    }
}

/** Reads an attachment's bytes, display name and MIME type from a SAF [Uri]. */
private fun readAttachment(context: android.content.Context, uri: Uri): OutgoingAttachment? {
    val resolver = context.contentResolver
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    var name = "attachment"
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)?.let { name = it }
        }
    }
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    return OutgoingAttachment(fileName = name, mimeType = mime, bytes = bytes)
}
