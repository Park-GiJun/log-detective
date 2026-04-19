package com.gijun.logdetect.web

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gijun.logdetect.web.screen.AlertScreen
import com.gijun.logdetect.web.screen.DashboardScreen
import com.gijun.logdetect.web.screen.DetectionScreen
import com.gijun.logdetect.web.screen.GeneratorScreen
import com.gijun.logdetect.web.screen.ScenarioScreen
import com.gijun.logdetect.web.theme.AppTheme
import com.gijun.logdetect.web.theme.SidebarColors

enum class Screen(val label: String, val section: String) {
    DASHBOARD("Dashboard", "MAIN"),
    GENERATOR("Generator", "MAIN"),
    SCENARIOS("Scenarios", "MAIN"),
    DETECTION("Detection Rules", "ANALYSIS"),
    ALERTS("Alerts", "ANALYSIS"),
}

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

    AppTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(
                currentScreen = currentScreen,
                onScreenSelected = { currentScreen = it },
            )
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                when (currentScreen) {
                    Screen.DASHBOARD -> DashboardScreen()
                    Screen.GENERATOR -> GeneratorScreen()
                    Screen.SCENARIOS -> ScenarioScreen()
                    Screen.DETECTION -> DetectionScreen()
                    Screen.ALERTS -> AlertScreen()
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(SidebarColors.background),
    ) {
        // 로고
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Text(
                text = "LOG DETECTIVE",
                style = MaterialTheme.typography.titleLarge,
                color = SidebarColors.selectedIndicator,
            )
            Text(
                text = "Anomaly Monitor",
                style = MaterialTheme.typography.labelMedium,
                color = SidebarColors.textDim,
            )
        }

        HorizontalDivider(color = SidebarColors.divider)
        Spacer(modifier = Modifier.height(8.dp))

        // 메인 섹션
        SidebarSectionLabel("MAIN")
        Screen.entries.filter { it.section == "MAIN" }.forEach { screen ->
            SidebarItem(
                label = screen.label,
                selected = currentScreen == screen,
                onClick = { onScreenSelected(screen) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 분석 섹션
        SidebarSectionLabel("ANALYSIS")
        Screen.entries.filter { it.section == "ANALYSIS" }.forEach { screen ->
            SidebarItem(
                label = screen.label,
                selected = currentScreen == screen,
                onClick = { onScreenSelected(screen) },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 버전 정보
        HorizontalDivider(color = SidebarColors.divider)
        Text(
            text = "v0.1.0",
            style = MaterialTheme.typography.labelMedium,
            color = SidebarColors.textDim,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun SidebarSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = SidebarColors.textDim,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun SidebarItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (selected) SidebarColors.selectedBackground else SidebarColors.background
    val textColor = if (selected) SidebarColors.selectedIndicator else SidebarColors.text

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(bgColor)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 선택 표시 바
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .background(
                    if (selected) SidebarColors.selectedIndicator else SidebarColors.background,
                ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier = Modifier.padding(start = 17.dp),
        )
    }
}
