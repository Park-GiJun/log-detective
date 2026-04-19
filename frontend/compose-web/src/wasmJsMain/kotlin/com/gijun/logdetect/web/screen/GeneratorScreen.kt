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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.gijun.logdetect.web.api.GeneratorStatusResponse
import com.gijun.logdetect.web.theme.StatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeneratorScreen() {
    var status by remember { mutableStateOf(GeneratorStatusResponse()) }
    var rateInput by remember { mutableStateOf("100") }
    var fraudRatioInput by remember { mutableStateOf("0.15") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 상태 폴링
    LaunchedEffect(Unit) {
        while (true) {
            ApiClient.getGeneratorStatus()
                .onSuccess {
                    status = it
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
            text = "Control log event generation and configure traffic patterns",
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

        // 실시간 상태
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Live Status",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(16.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LabeledValue(
                        label = "Status",
                        value = if (status.running) "RUNNING" else "STOPPED",
                        valueColor = if (status.running) StatusColors.active else StatusColors.inactive,
                    )
                    LabeledValue(
                        label = "Total Sent",
                        value = status.totalSent.toString(),
                    )
                    LabeledValue(
                        label = "Total Failed",
                        value = status.totalFailed.toString(),
                        valueColor = if (status.totalFailed > 0) StatusColors.error else StatusColors.active,
                    )
                    LabeledValue(
                        label = "Configured Rate",
                        value = "${status.configuredRate} evt/s",
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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

                // 입력 필드
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    OutlinedTextField(
                        value = rateInput,
                        onValueChange = { rateInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Rate (events/sec)") },
                        singleLine = true,
                        modifier = Modifier.width(200.dp),
                    )
                    OutlinedTextField(
                        value = fraudRatioInput,
                        onValueChange = { fraudRatioInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Fraud Ratio (0.0 ~ 1.0)") },
                        singleLine = true,
                        modifier = Modifier.width(200.dp),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 버튼들
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val rate = rateInput.toIntOrNull() ?: return@launch
                                val ratio = fraudRatioInput.toDoubleOrNull() ?: return@launch
                                ApiClient.startGenerator(rate, ratio)
                                    .onSuccess { successMessage = "Generator started (rate=$rate, fraudRatio=$ratio)" }
                                    .onFailure { errorMessage = "Start failed: ${it.message}" }
                            }
                        },
                        enabled = !status.running,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusColors.active,
                        ),
                    ) {
                        Text("Start")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                ApiClient.stopGenerator()
                                    .onSuccess { successMessage = "Generator stopped" }
                                    .onFailure { errorMessage = "Stop failed: ${it.message}" }
                            }
                        },
                        enabled = status.running,
                    ) {
                        Text("Stop")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val rate = rateInput.toIntOrNull() ?: return@launch
                                val ratio = fraudRatioInput.toDoubleOrNull() ?: return@launch
                                ApiClient.burstGenerator(rate, ratio)
                                    .onSuccess { successMessage = "Burst sent (rate=$rate, fraudRatio=$ratio)" }
                                    .onFailure { errorMessage = "Burst failed: ${it.message}" }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusColors.warning,
                        ),
                    ) {
                        Text("Burst")
                    }
                }
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
