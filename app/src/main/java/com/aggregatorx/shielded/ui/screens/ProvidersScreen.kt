package com.aggregatorx.shielded.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.aggregatorx.shielded.data.model.ProviderEntity
import com.aggregatorx.shielded.ui.theme.*
import com.aggregatorx.shielded.ui.viewmodel.ProvidersViewModel

@Composable
fun ProvidersScreen(vm: ProvidersViewModel = hiltViewModel()) {
    val providers by vm.providers.collectAsState()
    val state by vm.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        if (state.message != null) kotlinx.coroutines.delay(2000); vm.clearMessage()
    }

    Column(modifier = Modifier.fillMaxSize().background(PureBlack)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PROVIDERS (${providers.size})", color = NeonGreen,
                style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Provider", tint = NeonGreen)
            }
        }

        state.message?.let {
            Text(it, color = NeonGreen, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp))
        }
        state.error?.let {
            Text(it, color = AccentRed, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp))
        }

        HorizontalDivider(color = BorderGreen, thickness = 1.dp)

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(providers, key = { it.name }) { provider ->
                ProviderRow(
                    provider = provider,
                    onToggle = { vm.toggleEnabled(provider.name, !provider.isEnabled) },
                    onDelete = { vm.deleteProvider(provider.name) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddProviderDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { p -> vm.addProvider(p); showAddDialog = false }
        )
    }
}

@Composable
private fun ProviderRow(provider: ProviderEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    val borderColor = if (provider.isEnabled) NeonGreen.copy(alpha = 0.4f) else BorderGreen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .background(CardBlack, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(provider.name, color = if (provider.isEnabled) NeonGreen else TextDim,
                    style = MaterialTheme.typography.labelLarge)
                if (provider.requiresJs) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(3.dp))
                            .background(AccentAmber.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) { Text("JS", color = AccentAmber, fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
                }
            }
            Text(provider.baseUrl, color = TextDim, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("searches: ${provider.totalSearches}", color = TextDim,
                    style = MaterialTheme.typography.labelSmall)
                Text("rate: ${"%.0f".format(provider.successRate * 100)}%",
                    color = if (provider.successRate > 0.7f) NeonGreen else AccentRed,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        Switch(
            checked = provider.isEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = NeonGreen,
                checkedTrackColor = NeonGreenFaint,
                uncheckedThumbColor = TextDim,
                uncheckedTrackColor = BorderGreen
            )
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AccentRed, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AddProviderDialog(onDismiss: () -> Unit, onAdd: (ProviderEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://") }
    var searchPath by remember { mutableStateOf("/search?q={query}&page={page}") }
    var resultSel by remember { mutableStateOf(".result,.item,article") }
    var requiresJs by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBlack,
        titleContentColor = NeonGreen,
        textContentColor = TextPrimary,
        title = { Text("ADD PROVIDER", fontFamily = FontFamily.Monospace) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShieldTextField("Name", name) { name = it }
                ShieldTextField("Base URL", baseUrl) { baseUrl = it }
                ShieldTextField("Search Path ({query},{page})", searchPath) { searchPath = it }
                ShieldTextField("Result CSS Selector", resultSel) { resultSel = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = requiresJs, onCheckedChange = { requiresJs = it },
                        colors = CheckboxDefaults.colors(checkedColor = NeonGreen, uncheckedColor = TextDim))
                    Text("Requires JavaScript", color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && baseUrl.isNotBlank()) {
                    onAdd(ProviderEntity(
                        name = name.trim(),
                        baseUrl = baseUrl.trim(),
                        searchPath = searchPath.trim(),
                        resultSelector = resultSel.trim(),
                        requiresJs = requiresJs
                    ))
                }
            }) { Text("ADD", color = NeonGreen, fontFamily = FontFamily.Monospace) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = TextDim, fontFamily = FontFamily.Monospace) }
        }
    )
}

@Composable
private fun ShieldTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonGreen,
            unfocusedBorderColor = BorderGreen,
            focusedLabelColor = NeonGreen,
            unfocusedLabelColor = TextDim,
            cursorColor = NeonGreen
        )
    )
}
