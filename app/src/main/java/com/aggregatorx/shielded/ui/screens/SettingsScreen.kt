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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.aggregatorx.shielded.ui.theme.*
import com.aggregatorx.shielded.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Column(modifier = Modifier.fillMaxSize().background(PureBlack)) {
        Text("SETTINGS", color = NeonGreen, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(12.dp))
        HorizontalDivider(color = BorderGreen, thickness = 1.dp)

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {

            // ── Proxy Section ─────────────────────────────────────────────
            item {
                SectionHeader("PROXY / NETCIPHER")
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.proxyHost,
                            onValueChange = vm::updateProxyHost,
                            label = { Text("Host", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                            modifier = Modifier.weight(2f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            colors = neonFieldColors()
                        )
                        OutlinedTextField(
                            value = state.proxyPort,
                            onValueChange = vm::updateProxyPort,
                            label = { Text("Port", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            colors = neonFieldColors()
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = vm::applyProxy,
                            border = BorderStroke(1.dp, NeonGreen),
                            modifier = Modifier.weight(1f)
                        ) { Text("APPLY", color = NeonGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                        OutlinedButton(
                            onClick = vm::clearProxy,
                            border = BorderStroke(1.dp, AccentRed),
                            modifier = Modifier.weight(1f)
                        ) { Text("CLEAR", color = AccentRed, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                    }
                    if (state.proxyEnabled) {
                        Text("● PROXY ACTIVE: ${state.proxyHost}:${state.proxyPort}",
                            color = NeonGreen, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // ── Status messages ───────────────────────────────────────────
            state.message?.let { msg ->
                item { Text(msg, color = NeonGreen, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp)) }
            }
            state.error?.let { err ->
                item { Text(err, color = AccentRed, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp)) }
            }

            // ── Audit Log Section ─────────────────────────────────────────
            item {
                HorizontalDivider(color = BorderGreen, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("AUDIT LOG (${state.auditLogs.size})")
                    IconButton(onClick = vm::clearAuditLogs, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = AccentRed, modifier = Modifier.size(18.dp))
                    }
                }
            }

            items(state.auditLogs, key = { it.id }) { log ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .background(CardBlack, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        dateFormat.format(Date(log.timestamp)),
                        color = TextDim, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(56.dp)
                    )
                    Text(
                        log.actionType,
                        color = if (log.isSuccess) NeonGreen else AccentRed,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(
                        "${log.providerName ?: ""} ${log.details}".trim(),
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, color = NeonGreen, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
private fun neonFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonGreen,
    unfocusedBorderColor = BorderGreen,
    focusedLabelColor = NeonGreen,
    unfocusedLabelColor = TextDim,
    cursorColor = NeonGreen
)
