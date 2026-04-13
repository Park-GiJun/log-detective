package com.gijun.logdetect.web

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

fun main() {
    ComposeViewport(document.body!!) {
        App()
    }
}

@Composable
fun App() {
    MaterialTheme {
        Text("log-detective — compose-web skeleton")
    }
}
