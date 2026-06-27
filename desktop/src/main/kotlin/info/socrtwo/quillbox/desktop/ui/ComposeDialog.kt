package info.socrtwo.quillbox.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.socrtwo.quillbox.desktop.model.Account

@Composable
fun ComposeDialog(
    account: Account,
    onClose: () -> Unit,
    onSend: (to: List<String>, cc: List<String>, bcc: List<String>, subject: String, body: String) -> Unit
) {
    var to by remember { mutableStateOf("") }
    var cc by remember { mutableStateOf("") }
    var bcc by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("New message (from ${account.email})") },
        confirmButton = {
            TextButton(onClick = {
                onSend(split(to), split(cc), split(bcc), subject, body)
            }) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(to, { to = it }, label = { Text("To (comma-separated)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cc, { cc = it }, label = { Text("Cc") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(bcc, { bcc = it }, label = { Text("Bcc") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp))
            }
        }
    )
}

private fun split(raw: String): List<String> =
    raw.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
