package info.socrtwo.quillbox.ui.messages

import android.content.Intent
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.socrtwo.quillbox.data.local.entity.AttachmentEntity
import info.socrtwo.quillbox.data.local.entity.MessageEntity
import info.socrtwo.quillbox.data.model.CriteriaField
import info.socrtwo.quillbox.data.model.RuleCriterion
import info.socrtwo.quillbox.data.spam.RuleProposal
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
    val inSpam by viewModel.inSpamFolder.collectAsStateWithLifecycle()
    val proposal by viewModel.proposal.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(notice) {
        notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeNotice()
        }
    }

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
                    IconButton(onClick = { viewModel.analyse() }) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Analyse and make rule")
                    }
                    if (inSpam) {
                        IconButton(onClick = { viewModel.notJunk(onBack) }) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = "Not junk")
                        }
                    } else {
                        IconButton(onClick = { viewModel.moveToSpam(onBack) }) {
                            Icon(Icons.Filled.Report, contentDescription = "Junk")
                        }
                    }
                    IconButton(onClick = { viewModel.delete(onBack) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                Spacer(Modifier.height(8.dp))
                VerdictCard(msg)
                SenderHeader(msg)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (inSpam) {
                        OutlinedButton(onClick = { viewModel.notJunk(onBack) }) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null)
                            Text(" Not junk")
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.moveToSpam(onBack) }) {
                            Icon(Icons.Filled.Report, contentDescription = null)
                            Text(" Junk")
                        }
                    }
                    Button(onClick = { viewModel.analyse() }) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Text(" Analyse & make rule")
                    }
                }
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

    if (proposal.loading || proposal.draft != null || proposal.error != null) {
        ProposalDialog(
            state = proposal,
            inSpam = inSpam,
            onDismiss = { viewModel.dismissProposal() },
            onSave = { name, criteria, applyToInbox, alsoMoveThis ->
                viewModel.saveProposedRule(name, criteria, applyToInbox, alsoMoveThis, onBack)
            }
        )
    }
}

/** Sender block: display name, then the bare email address large and bold, coloured by verdict. */
@Composable
private fun SenderHeader(msg: MessageEntity) {
    val isSpam = msg.spamLevel == "SPAM"
    val isSuspicious = msg.spamLevel == "SUSPICIOUS"
    val addressColor = when {
        isSpam || (msg.brandMismatch && msg.claimedBrand != null) -> MaterialTheme.colorScheme.error
        isSuspicious -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val email = msg.senderEmail.ifBlank { MessageDetailViewModel.senderAddress(msg.fromAddress) }
    if (msg.senderName.isNotBlank()) {
        Text(msg.senderName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
    Text(
        text = email.ifBlank { "(no sender address)" },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold,
        color = addressColor
    )
    Text("To: ${msg.toAddresses}", style = MaterialTheme.typography.bodySmall)
    if (msg.ccAddresses.isNotBlank()) {
        Text("Cc: ${msg.ccAddresses}", style = MaterialTheme.typography.bodySmall)
    }
    if (msg.replyTo.isNotBlank()) {
        Text(
            "Replies go to: ${msg.replyTo}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
    Text(
        DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(msg.sentDate)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline
    )
    if (msg.authSummary.isNotBlank()) {
        Text(msg.authSummary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

/** Explains the junk verdict; hidden for clean mail with nothing to say. */
@Composable
private fun VerdictCard(msg: MessageEntity) {
    val isSpam = msg.spamLevel == "SPAM"
    val isSuspicious = msg.spamLevel == "SUSPICIOUS"
    val mismatch = msg.brandMismatch && msg.claimedBrand != null
    val verified = !mismatch && msg.claimedBrand != null && msg.brandExplanation.contains("legitimate", ignoreCase = true)
    if (!isSpam && !isSuspicious && !mismatch && !verified && msg.trained == null) return

    val container = when {
        isSpam || mismatch -> MaterialTheme.colorScheme.errorContainer
        isSuspicious -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (isSpam || mismatch) Icons.Filled.Report else if (isSuspicious) Icons.Filled.Warning else Icons.Filled.VerifiedUser,
                    contentDescription = null
                )
                Text(
                    text = when {
                        mismatch -> "Claims to be ${msg.claimedBrand} — but the sender's domain is not ${msg.claimedBrand}'s"
                        isSpam -> "This message looks like junk or a scam"
                        isSuspicious -> "Some things about this message look suspicious"
                        msg.trained == "ham" -> "You marked this as not junk"
                        else -> "Genuine ${msg.claimedBrand} domain"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text("Junk score ${msg.spamScore}/100", style = MaterialTheme.typography.labelSmall)
            if (msg.brandExplanation.isNotBlank()) {
                Text(msg.brandExplanation, style = MaterialTheme.typography.bodySmall)
            }
            if (msg.autoFiled) {
                Text("Quillbox moved this message to Spam automatically.", style = MaterialTheme.typography.bodySmall)
            }
            if (msg.spamReasons.isNotBlank()) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide details" else "Why?") }
                if (expanded) {
                    msg.spamReasons.lineSequence().forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** The "Analyse & make rule" dialog: verdict summary plus an editable proposed rule. */
@Composable
private fun ProposalDialog(
    state: ProposalUiState,
    inSpam: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, List<RuleCriterion>, Boolean, Boolean) -> Unit
) {
    val draft = state.draft
    val proposal: RuleProposal? = state.proposal
    var name by remember(draft) { mutableStateOf(draft?.name ?: "") }
    val criteria = remember(draft) { mutableStateListOf<RuleCriterion>().apply { draft?.criteria?.let { addAll(it) } } }
    var applyToInbox by remember { mutableStateOf(true) }
    var alsoMoveThis by remember(inSpam) { mutableStateOf(!inSpam) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (draft != null) {
                TextButton(onClick = { onSave(name, criteria.toList(), applyToInbox, alsoMoveThis) }) { Text("Save rule") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (draft != null) "Cancel" else "Close") } },
        title = { Text("AI analysis & rule proposal") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    state.loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("Checking blocklists, sender authentication, impersonation signals and content…")
                        }
                    }
                    state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
                    proposal != null && draft != null -> {
                        Text(proposal.verdictSummary, style = MaterialTheme.typography.bodySmall)
                        proposal.claimedOrganisation?.let {
                            Text("Claims to be: $it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider()
                        Text("Proposed rule", style = MaterialTheme.typography.labelLarge)
                        Text(proposal.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Rule name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Move to Spam when any condition matches:", style = MaterialTheme.typography.labelMedium)
                        criteria.forEachIndexed { index, c ->
                            Column {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    CriteriaField.entries.forEach { f ->
                                        FilterChip(
                                            selected = c.field == f,
                                            onClick = { criteria[index] = c.copy(field = f) },
                                            label = { Text(f.name.lowercase().replaceFirstChar { ch -> ch.uppercase() }) }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = c.value,
                                    onValueChange = { criteria[index] = c.copy(value = it) },
                                    label = { Text("contains…") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                proposal.criteria.getOrNull(index)?.let { pc ->
                                    Text(pc.rationale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                TextButton(onClick = { criteria.removeAt(index) }) { Text("Remove condition") }
                            }
                        }
                        OutlinedButton(onClick = { criteria.add(RuleCriterion(CriteriaField.SUBJECT, "")) }) {
                            Text("Add condition")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = applyToInbox, onCheckedChange = { applyToInbox = it })
                            Text("Apply to the mail already in the Inbox now")
                        }
                        if (!inSpam) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = alsoMoveThis, onCheckedChange = { alsoMoveThis = it })
                                Text("Also move this message to Spam")
                            }
                        }
                    }
                }
            }
        }
    )
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
