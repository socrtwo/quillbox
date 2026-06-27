package info.socrtwo.quillbox.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.socrtwo.quillbox.data.model.MailProtocol
import info.socrtwo.quillbox.data.model.SecurityType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSetupScreen(
    onSaved: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Add Mail Account") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = { v -> viewModel.onField { copy(displayName = v) } },
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.email,
                onValueChange = { v -> viewModel.onField { copy(email = v) } },
                label = { Text("Email address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Incoming mail", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MailProtocol.entries.forEach { p ->
                    FilterChip(
                        selected = state.protocol == p,
                        onClick = { viewModel.onProtocolChange(p) },
                        label = { Text(p.name) }
                    )
                }
            }

            OutlinedTextField(
                value = state.incomingHost,
                onValueChange = { v -> viewModel.onField { copy(incomingHost = v) } },
                label = { Text("Incoming host (e.g. imap.example.com)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.incomingPort,
                onValueChange = { v -> viewModel.onField { copy(incomingPort = v) } },
                label = { Text("Incoming port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            SecurityChips(
                selected = state.incomingSecurity,
                onSelect = { viewModel.onIncomingSecurityChange(it) }
            )

            Text("Outgoing mail (SMTP)", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = state.smtpHost,
                onValueChange = { v -> viewModel.onField { copy(smtpHost = v) } },
                label = { Text("SMTP host (e.g. smtp.example.com)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.smtpPort,
                onValueChange = { v -> viewModel.onField { copy(smtpPort = v) } },
                label = { Text("SMTP port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            SecurityChips(
                selected = state.smtpSecurity,
                onSelect = { sec -> viewModel.onField { copy(smtpSecurity = sec) } }
            )

            Text("Credentials", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = state.username,
                onValueChange = { v -> viewModel.onField { copy(username = v) } },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = { v -> viewModel.onField { copy(password = v) } },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            state.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    enabled = !state.saving,
                    onClick = { viewModel.save(verify = false, onSaved = onSaved) }
                ) { Text("Save") }

                Button(
                    enabled = !state.saving,
                    onClick = { viewModel.save(verify = true, onSaved = onSaved) }
                ) { Text("Verify & Save") }

                if (state.saving) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityChips(selected: SecurityType, onSelect: (SecurityType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecurityType.entries.forEach { sec ->
            FilterChip(
                selected = selected == sec,
                onClick = { onSelect(sec) },
                label = {
                    Text(
                        when (sec) {
                            SecurityType.SSL_TLS -> "SSL/TLS"
                            SecurityType.STARTTLS -> "STARTTLS"
                            SecurityType.NONE -> "None"
                        }
                    )
                }
            )
        }
    }
}
