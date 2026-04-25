package com.gijun.logdetect.web.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gijun.logdetect.web.api.ApiClient
import com.gijun.logdetect.web.api.GeneratorStatusResponse
import com.gijun.logdetect.web.api.ScenarioResponse
import com.gijun.logdetect.web.theme.StatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen() {
    val statuses = remember { mutableStateListOf<GeneratorStatusResponse>() }
    val scenarios = remember { mutableStateListOf<ScenarioResponse>() }
    var selectedScenario by remember { mutableStateOf<ScenarioResponse?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 시나리오 목록 1회 로드
    LaunchedEffect(Unit) {
        ApiClient.getScenarios()
            .onSuccess {
                scenarios.clear()
                scenarios.addAll(it)
                if (selectedScenario == null) selectedScenario = it.firstOrNull()
            }
            .onFailure { errorMessage = "시나리오 로드 실패: ${it.message}" }
    }

    // 상태 폴링
    LaunchedEffect(Unit) {
        while (true) {
            ApiClient.getGeneratorStatuses()
                .onSuccess {
                    statuses.clear()
                    statuses.addAll(it)
                    errorMessage = null
                }
                .onFailure { errorMessage = it.message }
            delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
    ) {
        Text(
            text = "Generator Control",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "시나리오를 선택해 다중 동시 실행 가능. 상태 카드는 시나리오별로 분리 표시됨.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        errorMessage?.let { msg ->
            ErrorBanner(msg)
            Spacer(modifier = Modifier.height(16.dp))
        }
        successMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = StatusColors.active.copy(alpha = 0.15f),
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StatusColors.active,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 제어 패널
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Control Panel",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                    modifier = Modifier.width(420.dp),
                ) {
                    OutlinedTextField(
                        value = selectedScenario?.let { "#${it.id} ${it.name}" } ?: "시나리오를 선택하세요",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Scenario") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                    ) {
                        scenarios.forEach { sc ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "#${sc.id} ${sc.name} — ${sc.type} / ${sc.attackType} / " +
                                            "rate=${sc.rate} fraud=${sc.fraudRatio}% success=${sc.successful}",
                                    )
                                },
                                onClick = {
                                    selectedScenario = sc
                                    dropdownExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 버튼들
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            val sc = selectedScenario ?: return@Button
                            scope.launch {
                                ApiClient.startGenerator(sc.id)
                                    .onSuccess { successMessage = "Generator started — #${sc.id} ${sc.name}" }
                                    .onFailure { errorMessage = "Start failed: ${it.message}" }
                            }
                        },
                        enabled = selectedScenario != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusColors.active,
                        ),
                    ) {
                        Text("Start")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                ApiClient.stopAllGenerators()
                                    .onSuccess { successMessage = "All generators stopped" }
                                    .onFailure { errorMessage = "Stop all failed: ${it.message}" }
                            }
                        },
                    ) {
                        Text("Stop All")
                    }

                    Button(
                        onClick = {
                            val sc = selectedScenario ?: return@Button
                            scope.launch {
                                ApiClient.burstGenerator(sc.id)
                                    .onSuccess { successMessage = "Burst sent — #${sc.id} ${sc.name}" }
                                    .onFailure { errorMessage = "Burst failed: ${it.message}" }
                            }
                        },
                        enabled = selectedScenario != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusColors.warning,
                        ),
                    ) {
                        Text("Burst")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 시나리오별 실시간 상태
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Live Status (per scenario)",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (statuses.isEmpty()) {
                    Text(
                        text = "실행 중이거나 카운터가 남은 시나리오가 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        statuses.forEach { status ->
                            val name = scenarios.firstOrNull { it.id == status.scenarioId }?.name ?: "(unknown)"
                            ScenarioStatusRow(status, name) {
                                scope.launch {
                                    ApiClient.stopGenerator(status.scenarioId)
                                        .onSuccess { successMessage = "Stopped — #${status.scenarioId}" }
                                        .onFailure { errorMessage = "Stop failed: ${it.message}" }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScenarioStatusRow(
    status: GeneratorStatusResponse,
    scenarioName: String,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = "#${status.scenarioId} $scenarioName",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (status.running) {
                    OutlinedButton(onClick = onStop) {
                        Text("Stop")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LabeledValue(
                    label = "Status",
                    value = if (status.running) "RUNNING" else "STOPPED",
                    valueColor = if (status.running) StatusColors.active else StatusColors.inactive,
                )
                LabeledValue(label = "Total Sent", value = status.totalSent.toString())
                LabeledValue(
                    label = "Total Failed",
                    value = status.totalFailed.toString(),
                    valueColor = if (status.totalFailed > 0) StatusColors.error else StatusColors.active,
                )
                LabeledValue(label = "Configured Rate", value = "${status.configuredRate} evt/s")
            }
        }
    }
}

@Composable
private fun LabeledValue(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor,
        )
    }
}
