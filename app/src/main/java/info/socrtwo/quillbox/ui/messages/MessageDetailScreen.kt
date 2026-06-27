package info.socrtwo.quillbox.ui.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    onBack: () -> Unit,
    viewModel: MessageDetailViewModel = hiltViewModel()
) {
    val message by viewModel.message.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth())
            Text(
                text = msg.bodyText.ifBlank { "(no text content)" },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
