package com.gijun.logdetect.web.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gijun.logdetect.web.api.ApiClient
import com.gijun.logdetect.web.api.AttackType
import com.gijun.logdetect.web.api.CreateScenarioRequest
import com.gijun.logdetect.web.api.RequestType
import com.gijun.logdetect.web.api.ScenarioResponse
import com.gijun.logdetect.web.theme.StatusColors
import kotlinx.coroutines.launch

@Composable
fun ScenarioScreen() {
    var scenarios by remember { mutableStateOf<List<ScenarioResponse>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadScenarios() {
        scope.launch {
            ApiClient.getScenarios()
                .onSuccess { scenarios = it; errorMessage = null }
                .onFailure { errorMessage = it.message }
        }
    }

    LaunchedEffect(Unit) { loadScenarios() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
    ) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Scenarios",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Manage log generation scenarios",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { showCreateDialog = true },
            ) {
                Text("+ New Scenario")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        errorMessage?.let { msg ->
            ErrorBanner(msg)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 시나리오 테이블
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                // 테이블 헤더
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TableHeader("ID", Modifier.width(60.dp))
                    TableHeader("Name", Modifier.weight(1f))
                    TableHeader("Type", Modifier.width(80.dp))
                    TableHeader("Attack Type", Modifier.width(140.dp))
                    TableHeader("Success", Modifier.width(80.dp))
                    TableHeader("Rate", Modifier.width(80.dp))
                    TableHeader("Fraud", Modifier.width(80.dp))
                    TableHeader("Actions", Modifier.width(80.dp))
                }

                if (scenarios.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No scenarios configured",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    scenarios.forEach { scenario ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ScenarioRow(
                            scenario = scenario,
                            onDelete = {
                                scope.launch {
                                    ApiClient.deleteScenario(scenario.id)
                                        .onSuccess { loadScenarios() }
                                        .onFailure { errorMessage = "Delete failed: ${it.message}" }
                                }
                            },
                        )
                    }
                }
            }
        }

        // 생성 다이얼로그
        if (showCreateDialog) {
            Spacer(modifier = Modifier.height(24.dp))
            CreateScenarioForm(
                onCreated = {
                    showCreateDialog = false
                    loadScenarios()
                },
                onCancel = { showCreateDialog = false },
                onError = { errorMessage = it },
            )
        }
    }
}

@Composable
private fun TableHeader(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun ScenarioRow(
    scenario: ScenarioResponse,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = scenario.id.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(60.dp),
        )
        Text(
            text = scenario.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = scenario.type.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = scenario.attackType.name,
            style = MaterialTheme.typography.bodyMedium,
            color = attackTypeColor(scenario.attackType),
            modifier = Modifier.width(140.dp),
        )
        Text(
            text = if (scenario.successful) "Yes" else "No",
            style = MaterialTheme.typography.bodyMedium,
            color = if (scenario.successful) StatusColors.active else StatusColors.error,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = scenario.rate.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = scenario.fraudRatio.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(80.dp),
        )
        TextButton(
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = StatusColors.error),
            modifier = Modifier.width(80.dp),
        ) {
            Text("Delete")
        }
    }
}

@Composable
private fun CreateScenarioForm(
    onCreated: () -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(RequestType.REST) }
    var selectedAttack by remember { mutableStateOf(AttackType.BRUTE_FORCE) }
    var successful by remember { mutableStateOf(true) }
    var rate by remember { mutableStateOf("50") }
    var fraudRatio by remember { mutableStateOf("10") }
    var typeExpanded by remember { mutableStateOf(false) }
    var attackExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Create New Scenario",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Scenario Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Request Type
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Request Type",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedButton(onClick = { typeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedType.name)
                        }
                        DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            RequestType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = { selectedType = type; typeExpanded = false },
                                )
                            }
                        }
                    }
                }

                // Attack Type
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Attack Type",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedButton(onClick = { attackExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedAttack.name)
                        }
                        DropdownMenu(expanded = attackExpanded, onDismissRequest = { attackExpanded = false }) {
                            AttackType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = { selectedAttack = type; attackExpanded = false },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it.filter { c -> c.isDigit() } },
                    label = { Text("Rate") },
                    singleLine = true,
                    modifier = Modifier.width(150.dp),
                )
                OutlinedTextField(
                    value = fraudRatio,
                    onValueChange = { fraudRatio = it.filter { c -> c.isDigit() } },
                    label = { Text("Fraud Ratio") },
                    singleLine = true,
                    modifier = Modifier.width(150.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = successful,
                        onCheckedChange = { successful = it },
                    )
                    Text(
                        text = "Successful",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            onError("Name is required")
                            return@Button
                        }
                        scope.launch {
                            val request = CreateScenarioRequest(
                                name = name,
                                type = selectedType,
                                attackType = selectedAttack,
                                successful = successful,
                                rate = rate.toLongOrNull() ?: 50,
                                fraudRatio = fraudRatio.toLongOrNull() ?: 10,
                            )
                            ApiClient.createScenario(request)
                                .onSuccess { onCreated() }
                                .onFailure { onError("Create failed: ${it.message}") }
                        }
                    },
                ) {
                    Text("Create")
                }
            }
        }
    }
}

private fun attackTypeColor(type: AttackType) = when (type) {
    AttackType.BRUTE_FORCE -> StatusColors.severityHigh
    AttackType.SQL_INJECTION -> StatusColors.severityHigh
    AttackType.ERROR_SPIKE -> StatusColors.severityMedium
    AttackType.OFF_HOUR_ACCESS -> StatusColors.severityMedium
    AttackType.GEO_ANOMALY -> StatusColors.severityHigh
    AttackType.RARE_EVENT -> StatusColors.severityLow
}
