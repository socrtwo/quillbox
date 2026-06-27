package info.socrtwo.quillbox.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import info.socrtwo.quillbox.desktop.mail.FetchedMessage
import info.socrtwo.quillbox.desktop.mail.MailClient
import info.socrtwo.quillbox.desktop.model.Account
import info.socrtwo.quillbox.desktop.model.MailProtocol
import info.socrtwo.quillbox.desktop.model.SecurityType
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val mailClient = MailClient()

@Composable
fun App() {
    var account by remember { mutableStateOf<Account?>(null) }
    MaterialTheme {
        if (account == null) {
            AccountForm(onSave = { account = it })
        } else {
            MailScreen(account!!, onSignOut = { account = null })
        }
    }
}

@Composable
private fun AccountForm(onSave: (Account) -> Unit) {
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("993") }
    var smtpHost by remember { mutableStateOf("") }
    var smtpPort by remember { mutableStateOf("587") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pop3 by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Add Mail Account", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(displayName, { displayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email address") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pop3 = false }) { Text(if (!pop3) "● IMAP" else "IMAP") }
            OutlinedButton(onClick = { pop3 = true }) { Text(if (pop3) "● POP3" else "POP3") }
        }
        OutlinedTextField(host, { host = it }, label = { Text("Incoming host (imap.example.com)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(port, { port = it }, label = { Text("Incoming port") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(smtpHost, { smtpHost = it }, label = { Text("SMTP host (smtp.example.com)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(smtpPort, { smtpPort = it }, label = { Text("SMTP port") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                onSave(
                    Account(
                        displayName = displayName.ifBlank { email },
                        email = email.trim(),
                        incomingHost = host.trim(),
                        incomingPort = port.toIntOrNull() ?: 993,
                        protocol = if (pop3) MailProtocol.POP3 else MailProtocol.IMAP,
                        incomingSecurity = SecurityType.SSL_TLS,
                        smtpHost = smtpHost.trim(),
                        smtpPort = smtpPort.toIntOrNull() ?: 587,
                        smtpSecurity = SecurityType.STARTTLS,
                        username = username.trim(),
                        password = password
                    )
                )
            }
        ) { Text("Connect") }
    }
}

@Composable
private fun MailScreen(account: Account, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<FetchedMessage>>(emptyList()) }
    var selected by remember { mutableStateOf<FetchedMessage?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var composing by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true; status = null
            runCatching { mailClient.fetchInbox(account) }
                .onSuccess { messages = it; status = "${it.size} message(s)" }
                .onFailure { status = "Sync failed: ${it.message}" }
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Quillbox — ${account.displayName}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).weight(1f))
            Button(onClick = { refresh() }, enabled = !loading) { Text("Refresh") }
            Button(onClick = { composing = true }) { Text("Compose") }
            OutlinedButton(onClick = onSignOut) { Text("Sign out") }
        }
        if (loading) CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        status?.let { Text(it, modifier = Modifier.padding(horizontal = 8.dp)) }
        Divider()
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.width(340.dp).fillMaxSize()) {
                items(messages) { m ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { selected = m }.padding(10.dp)
                    ) {
                        Text(m.from.ifBlank { "(unknown)" }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(m.subject, style = MaterialTheme.typography.bodySmall)
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(m.sentDate)),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Divider()
                }
            }
            Divider(modifier = Modifier.width(1.dp).fillMaxSize())
            Box(modifier = Modifier.weight(1f).fillMaxSize().padding(16.dp)) {
                val msg = selected
                if (msg == null) {
                    Text("Select a message", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text(msg.subject, style = MaterialTheme.typography.headlineSmall)
                        Text("From: ${msg.from}", style = MaterialTheme.typography.bodyMedium)
                        Text("To: ${msg.to}", style = MaterialTheme.typography.bodySmall)
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(msg.bodyText.ifBlank { "(no text content)" }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (composing) {
        ComposeDialog(
            account = account,
            onClose = { composing = false },
            onSend = { to, cc, bcc, subject, body ->
                scope.launch {
                    val result = mailClient.send(account, to, cc, bcc, subject, body)
                    status = result.fold({ "Sent" }, { "Send failed: ${it.message}" })
                    composing = false
                }
            }
        )
    }
}
