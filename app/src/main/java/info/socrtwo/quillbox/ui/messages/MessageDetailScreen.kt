package info.socrtwo.quillbox.ui.messages

import android.content.Intent
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.socrtwo.quillbox.data.local.entity.AttachmentEntity
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    onBack: () -> Unit,
    viewModel: MessageDetailViewModel = hiltViewModel()
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val imagesAllowed by viewModel.imagesAllowed.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Message") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.moveToSpam(onBack) }) {
                        Icon(Icons.Filled.Report, contentDescription = "Move to Spam")
                    }
                    IconButton(onClick = { viewModel.delete(onBack) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        val msg = message
        if (msg == null) {
            Column(modifier = Modifier.padding(padding).padding(24.dp)) { Text("Loading…") }
            return@Scaffold
        }

        val html = msg.bodyHtml?.takeIf { it.isNotBlank() }
        val showImageBanner = html != null && html.contains("<img", ignoreCase = true) && !imagesAllowed

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // --- Header (fixed) ---
            Column(modifier = Modifier.padding(16.dp)) {
                Text(msg.subject, style = MaterialTheme.typography.headlineSmall)
                Text("From: ${msg.fromAddress}", style = MaterialTheme.typography.bodyMedium)
                Text("To: ${msg.toAddresses}", style = MaterialTheme.typography.bodySmall)
                if (msg.ccAddresses.isNotBlank()) {
                    Text("Cc: ${msg.ccAddresses}", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(msg.sentDate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (attachments.isNotEmpty()) {
                AttachmentBar(
                    attachments = attachments,
                    onOpen = { openAttachment(context, it, share = false) },
                    onShare = { openAttachment(context, it, share = true) }
                )
            }

            if (showImageBanner) {
                ImageBanner(
                    onShowOnce = { viewModel.showImages() },
                    onAlways = { viewModel.alwaysShowImagesFromSender() }
                )
            }

            HorizontalDivider()

            // --- Body (fills remaining space, scrolls internally) ---
            if (html != null) {
                HtmlBody(
                    html = html,
                    loadImages = imagesAllowed,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        msg.bodyText.ifBlank { "(no text content)" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentBar(
    attachments: List<AttachmentEntity>,
    onOpen: (AttachmentEntity) -> Unit,
    onShare: (AttachmentEntity) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Text(
            "Attachments",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        attachments.forEach { att ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { onOpen(att) },
                    label = { Text("${att.fileName}  (${formatSize(att.sizeBytes)})") },
                    leadingIcon = { Icon(Icons.Filled.AttachFile, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onShare(att) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share / save")
                }
            }
        }
    }
}

@Composable
private fun ImageBanner(onShowOnce: () -> Unit, onAlways: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Image, contentDescription = null)
            Text(
                "Images hidden",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(top = 6.dp)
            )
            OutlinedButton(onClick = onShowOnce) { Text("Show") }
            Button(onClick = onAlways) { Text("Always") }
        }
    }
}

@Composable
private fun HtmlBody(html: String, loadImages: Boolean, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                // JavaScript stays disabled — email HTML is untrusted.
                settings.javaScriptEnabled = false
                settings.loadsImagesAutomatically = true
            }
        },
        update = { webView ->
            // Block remote images until the user opts in (privacy / tracking pixels).
            webView.settings.blockNetworkImage = !loadImages
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/** Opens an attachment with an external viewer, or shares it so it can be saved elsewhere. */
private fun openAttachment(context: android.content.Context, att: AttachmentEntity, share: Boolean) {
    runCatching {
        val file = File(att.filePath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val action = if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW
        val intent = Intent(action).apply {
            if (share) {
                type = att.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
            } else {
                setDataAndType(uri, att.mimeType)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, att.fileName))
    }
}
