package info.socrtwo.quillbox.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.socrtwo.quillbox.data.local.entity.RuleEntity
import info.socrtwo.quillbox.data.model.CriteriaField
import info.socrtwo.quillbox.data.model.MatchLogic
import info.socrtwo.quillbox.data.model.RuleActionType
import info.socrtwo.quillbox.data.model.RuleCriterion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onBack: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel()
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Filtering Rules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add rule")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { enabled -> viewModel.setEnabled(rule, enabled) },
                    onDelete = { viewModel.delete(rule) }
                )
            }
        }
    }

    if (showDialog) {
        AddRuleDialog(
            onDismiss = { showDialog = false },
            onConfirm = { name, logic, criteria, action, target ->
                viewModel.addRule(name, logic, criteria, action, target)
                showDialog = false
            }
        )
    }
}

@Composable
private fun RuleCard(rule: RuleEntity, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete rule")
                }
            }
            val criteriaText = rule.criteria.joinToString(" ${rule.logic.name} ") {
                "${it.field.name.lowercase()} contains \"${it.value}\""
            }
            Text(criteriaText, style = MaterialTheme.typography.bodySmall)
            val actionText = when (rule.actionType) {
                RuleActionType.MOVE_TO_FOLDER -> "→ move to ${rule.targetFolder}"
                RuleActionType.MARK_READ -> "→ mark as read"
                RuleActionType.DELETE -> "→ delete"
            }
            Text(actionText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, MatchLogic, List<RuleCriterion>, RuleActionType, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var logic by remember { mutableStateOf(MatchLogic.OR) }
    var action by remember { mutableStateOf(RuleActionType.MOVE_TO_FOLDER) }
    var targetFolder by remember { mutableStateOf("Spam") }
    val criteria = remember {
        mutableStateListOf(EditableCriterion(CriteriaField.SENDER, ""))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    name,
                    logic,
                    criteria.filter { it.value.isNotBlank() }
                        .map { RuleCriterion(it.field, it.value.trim()) },
                    action,
                    targetFolder
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("New rule") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Match", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MatchLogic.entries.forEach { l ->
                        FilterChip(
                            selected = logic == l,
                            onClick = { logic = l },
                            label = { Text(if (l == MatchLogic.AND) "All (AND)" else "Any (OR)") }
                        )
                    }
                }

                Text("Criteria", style = MaterialTheme.typography.labelLarge)
                criteria.forEachIndexed { index, c ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                CriteriaField.entries.forEach { f ->
                                    FilterChip(
                                        selected = c.field == f,
                                        onClick = { criteria[index] = c.copy(field = f) },
                                        label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = c.value,
                                onValueChange = { criteria[index] = c.copy(value = it) },
                                label = { Text("contains…") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                OutlinedButton(onClick = { criteria.add(EditableCriterion(CriteriaField.SUBJECT, "")) }) {
                    Text("Add criterion")
                }

                Text("Action", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleActionType.entries.forEach { a ->
                        FilterChip(
                            selected = action == a,
                            onClick = { action = a },
                            label = {
                                Text(
                                    when (a) {
                                        RuleActionType.MOVE_TO_FOLDER -> "Move"
                                        RuleActionType.MARK_READ -> "Mark read"
                                        RuleActionType.DELETE -> "Delete"
                                    }
                                )
                            }
                        )
                    }
                }
                if (action == RuleActionType.MOVE_TO_FOLDER) {
                    OutlinedTextField(
                        value = targetFolder,
                        onValueChange = { targetFolder = it },
                        label = { Text("Destination folder") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    )
}

private data class EditableCriterion(val field: CriteriaField, val value: String)
