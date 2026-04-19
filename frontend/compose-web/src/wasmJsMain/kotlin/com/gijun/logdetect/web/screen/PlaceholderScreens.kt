package com.gijun.logdetect.web.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gijun.logdetect.web.theme.StatusColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetectionScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
    ) {
        Text(
            text = "Detection Rules",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Rule-based anomaly detection engine configuration",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RuleCard(
                ruleId = "R001",
                name = "BruteForceLoginRule",
                description = "10+ login failures from same IP within 5 minutes",
                severity = "HIGH",
                severityColor = StatusColors.severityHigh,
            )
            RuleCard(
                ruleId = "R002",
                name = "SqlInjectionPatternRule",
                description = "SQLi signature detected in request message",
                severity = "HIGH",
                severityColor = StatusColors.severityHigh,
            )
            RuleCard(
                ruleId = "R003",
                name = "ErrorRateSpikeRule",
                description = "ERROR rate 3x above baseline within 1 hour window",
                severity = "MEDIUM",
                severityColor = StatusColors.severityMedium,
            )
            RuleCard(
                ruleId = "R004",
                name = "OffHourAccessRule",
                description = "Admin account login during 00:00-05:00 KST",
                severity = "MEDIUM",
                severityColor = StatusColors.severityMedium,
            )
            RuleCard(
                ruleId = "R005",
                name = "GeoAnomalyRule",
                description = "Physically impossible distance traveled within 1 hour",
                severity = "HIGH",
                severityColor = StatusColors.severityHigh,
            )
            RuleCard(
                ruleId = "R006",
                name = "RareEventRule",
                description = "Previously unseen source + level + pattern in 30 days",
                severity = "LOW",
                severityColor = StatusColors.severityLow,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        PlaceholderNotice("Detection service integration will be available after log-detection-service is implemented.")
    }
}

@Composable
fun AlertScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
    ) {
        Text(
            text = "Alerts",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Real-time alert monitoring and management",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 알림 상태 카드
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AlertStatusCard("OPEN", "0", StatusColors.error)
            AlertStatusCard("ACKNOWLEDGED", "0", StatusColors.warning)
            AlertStatusCard("RESOLVED", "0", StatusColors.active)
            AlertStatusCard("SUPPRESSED", "0", StatusColors.inactive)
        }

        Spacer(modifier = Modifier.height(32.dp))
        PlaceholderNotice("Alert management will be available after log-alert-service is implemented.")
    }
}

@Composable
private fun RuleCard(
    ruleId: String,
    name: String,
    description: String,
    severity: String,
    severityColor: androidx.compose.ui.graphics.Color,
) {
    Card(
        modifier = Modifier.width(360.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = ruleId,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Severity: $severity",
                style = MaterialTheme.typography.labelLarge,
                color = severityColor,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertStatusCard(
    label: String,
    count: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Card(
        modifier = Modifier.width(180.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.headlineMedium,
                color = color,
            )
        }
    }
}

@Composable
private fun PlaceholderNotice(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}
