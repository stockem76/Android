package com.obdinsight.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.obdinsight.app.ai.SecurePrefs

@Composable
fun SettingsScreen(securePrefs: SecurePrefs) {
    var apiKey by remember { mutableStateOf(securePrefs.apiKey ?: "") }
    var vehicle by remember { mutableStateOf(securePrefs.vehicleContext) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("Anthropic API key (BYOK)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Used only for the AI analysis feature: identifying unrecognized PIDs/DIDs by " +
                    "reasoning and web search. Stored encrypted on this device via the Android " +
                    "Keystore, and sent only in direct HTTPS calls this device makes to " +
                    "api.anthropic.com - never to any other server. Get a key at " +
                    "console.anthropic.com.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; saved = false },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Divider(Modifier.padding(vertical = 16.dp))

            Text("Vehicle context", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sent to Claude as background context so its answers are specific to your " +
                    "vehicle/ECU rather than generic.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = vehicle,
                onValueChange = { vehicle = it; saved = false },
                label = { Text("Vehicle description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            Button(
                onClick = {
                    securePrefs.apiKey = apiKey.trim()
                    securePrefs.vehicleContext = vehicle.trim().ifBlank { SecurePrefs.DEFAULT_VEHICLE }
                    saved = true
                },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Save") }

            if (saved) {
                Text(
                    "Saved.",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
