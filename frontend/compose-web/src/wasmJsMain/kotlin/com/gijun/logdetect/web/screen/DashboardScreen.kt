package com.gijun.logdetect.web.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gijun.logdetect.web.api.ApiClient
import com.gijun.logdetect.web.api.GeneratorStatusResponse
import com.gijun.logdetect.web.theme.StatusColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen() {
    var status by remember { mutableStateOf(GeneratorStatusResponse()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 3초마다 상태 폴링
    LaunchedEffect(Unit) {
        while (true) {
            ApiClient.getGeneratorStatus()
                .onSuccess {
                    status = it
                    errorMessage = null
                }
                .onFailure { errorMessage = it.message }
            delay(3000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
    ) {
        // 헤더
        Text(
            text = "Dashboard",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "System overview and real-time monitoring",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 에러 배너
        errorMessage?.let { msg ->
            ErrorBanner(msg)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 상태 카드들
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(
                title = "Generator",
                value = if (status.running) "RUNNING" else "STOPPED",
                valueColor = if (status.running) StatusColors.active else StatusColors.inactive,
            )
            StatusCard(
                title = "Total Sent",
                value = formatNumber(status.totalSent),
                valueColor = MaterialTheme.colorScheme.primary,
            )
            StatusCard(
                title = "Total Failed",
                value = formatNumber(status.totalFailed),
                valueColor = if (status.totalFailed > 0) StatusColors.error else StatusColors.active,
            )
            StatusCard(
                title = "Rate",
                value = "${status.configuredRate} evt/s",
                valueColor = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 하단 섹션: Detection Rules + Services
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 탐지 규칙 카드
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Detection Rules",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    DetectionRuleRow("R001", "BruteForceLogin", "HIGH", StatusColors.severityHigh)
                    DetectionRuleRow("R002", "SqlInjectionPattern", "HIGH", StatusColors.severityHigh)
                    DetectionRuleRow("R003", "ErrorRateSpike", "MEDIUM", StatusColors.severityMedium)
                    DetectionRuleRow("R004", "OffHourAccess", "MEDIUM", StatusColors.severityMedium)
                    DetectionRuleRow("R005", "GeoAnomaly", "HIGH", StatusColors.severityHigh)
                    DetectionRuleRow("R006", "RareEvent", "LOW", StatusColors.severityLow)
                }
            }

            // 서비스 상태 카드
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "System Services",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ServiceRow("Log Generator", status.running)
                    ServiceRow("Ingest Service", null)
                    ServiceRow("Detection Service", null)
                    ServiceRow("Alert Service", null)
                    ServiceRow("Gateway", null)
                    ServiceRow("Eureka Server", null)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    valueColor: Color,
) {
    Card(
        modifier = Modifier.width(200.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun DetectionRuleRow(
    ruleId: String,
    name: String,
    severity: String,
    severityColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ruleId,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = severity,
            style = MaterialTheme.typography.labelMedium,
            color = severityColor,
        )
    }
}

@Composable
private fun ServiceRow(name: String, running: Boolean?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = when (running) {
            true -> StatusColors.active
            false -> StatusColors.error
            null -> StatusColors.inactive
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = when (running) {
                true -> "Online"
                false -> "Offline"
                null -> "N/A"
            },
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
internal fun ErrorBanner(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = StatusColors.error.copy(alpha = 0.15f),
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = StatusColors.error,
            modifier = Modifier.padding(12.dp),
        )
    }
}

private fun formatNumber(n: Long): String {
    if (n < 1_000) return n.toString()
    if (n < 1_000_000) return "${n / 1_000}.${(n % 1_000) / 100}K"
    return "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
}
