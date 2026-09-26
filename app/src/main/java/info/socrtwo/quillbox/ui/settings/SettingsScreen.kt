package info.socrtwo.quillbox.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.socrtwo.quillbox.data.local.entity.AccountEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onManageRules: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedAccountId.collectAsStateWithLifecycle()
    val spamEnabled by viewModel.spamEnabled.collectAsStateWithLifecycle()
    val autoMoveSpam by viewModel.autoMoveSpam.collectAsStateWithLifecycle()
    val safeSenders by viewModel.safeSenders.collectAsStateWithLifecycle()
    val blockedSenders by viewModel.blockedSenders.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Accounts",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(accounts, key = { it.id }) { account ->
                    val effectiveSelected = selectedId == account.id ||
                        (selectedId == null && accounts.firstOrNull()?.id == account.id)
                    AccountRow(
                        account = account,
                        selected = effectiveSelected,
                        onSelect = { viewModel.selectAccount(account.id) },
                        onDelete = { viewModel.deleteAccount(account) }
                    )
                    HorizontalDivider()
                }
            }

            Text(
                "Junk protection",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            ListItem(
                headlineContent = { Text("Check incoming mail for junk") },
                supportingContent = {
                    Text("Open DNS blocklists, sender authentication, brand-impersonation detection and a classifier that learns from Junk / Not junk. Only DNS queries leave the device.")
                },
                trailingContent = { Switch(checked = spamEnabled, onCheckedChange = { viewModel.setSpamEnabled(it) }) }
            )
            ListItem(
                headlineContent = { Text("Move junk to Spam automatically") },
                supportingContent = {
                    Text(
                        if (safeSenders.isEmpty() && blockedSenders.isEmpty()) "When off, junk is only labelled in the Inbox."
                        else "${safeSenders.size} safe sender(s), ${blockedSenders.size} blocked sender(s)."
                    )
                },
                trailingContent = { Switch(checked = autoMoveSpam, enabled = spamEnabled, onCheckedChange = { viewModel.setAutoMoveSpam(it) }) }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Add mail account") },
                leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onAddAccount)
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Filtering rules") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onManageRules)
            )
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (selected) "Selected account" else "Tap to select",
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(account.displayName, style = MaterialTheme.typography.titleMedium)
            Text(account.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete account")
        }
    }
}
