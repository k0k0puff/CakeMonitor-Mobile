package com.awesomecyborg.cakemonitor

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.awesomecyborg.cakemonitor.ui.theme.CakeMonitorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CakeMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SetupScreen(
                        initialLocation = Config.getLocation(this).takeIf { it != "Unknown" } ?: "",
                        initialDeviceId = Config.getDeviceId(this).takeIf { it != "device-1" } ?: "",
                        initialPort = Config.getPort(this).toString(),
                        initialCallbackUrl = Config.getCallbackUrl(this),
                        initialJpegQuality = Config.getJpegQuality(this).toString(),
                        onSave = { location, deviceId, port, callbackUrl, jpegQuality ->
                            Config.setLocation(this, location)
                            Config.setDeviceId(this, deviceId)
                            Config.setPort(this, port)
                            Config.setCallbackUrl(this, callbackUrl)
                            Config.setJpegQuality(this, jpegQuality)

                            Toast.makeText(this, "Configuration saved", Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        onCancel = {
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    initialLocation: String = "",
    initialDeviceId: String = "",
    initialPort: String = "8080",
    initialCallbackUrl: String = "",
    initialJpegQuality: String = "85",
    onSave: (String, String, Int, String, Int) -> Unit,
    onCancel: () -> Unit
) {
    var location by remember { mutableStateOf(initialLocation) }
    var deviceId by remember { mutableStateOf(initialDeviceId) }
    var port by remember { mutableStateOf(initialPort) }
    var callbackUrl by remember { mutableStateOf(initialCallbackUrl) }
    var jpegQuality by remember { mutableStateOf(initialJpegQuality) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Camera Monitor Setup",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Location Label") },
            placeholder = { Text("e.g., Chiller or Kitchen Fridge") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = deviceId,
            onValueChange = { deviceId = it },
            label = { Text("Device ID") },
            placeholder = { Text("e.g., phone-a") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("HTTP Server Port") },
            placeholder = { Text("8080") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = callbackUrl,
            onValueChange = { callbackUrl = it },
            label = { Text("Callback URL") },
            placeholder = { Text("https://your-n8n-webhook-url") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = jpegQuality,
            onValueChange = { jpegQuality = it },
            label = { Text("JPEG Quality (50-95)") },
            placeholder = { Text("85") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Test Connection Button
        Button(
            onClick = {
                val portNum = port.toIntOrNull() ?: 8080
                isTesting = true
                testResult = null

                scope.launch {
                    val result = testConnection(portNum)
                    testResult = result
                    isTesting = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTesting
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isTesting) "Testing..." else "Test Connection")
        }

        // Test Result
        testResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.startsWith("Success")) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Text(
                    text = result,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    val portNum = port.toIntOrNull() ?: 8080
                    val qualityNum = jpegQuality.toIntOrNull()?.coerceIn(50, 95) ?: 85

                    onSave(location, deviceId, portNum, callbackUrl, qualityNum)
                },
                modifier = Modifier.weight(1f),
                enabled = location.isNotBlank() && deviceId.isNotBlank() && callbackUrl.isNotBlank()
            ) {
                Text("Save")
            }
        }
    }
}

private suspend fun testConnection(port: Int): String = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("http://localhost:$port/health")
            .get()
            .build()

        val response = client.newCall(request).execute()
        response.use {
            if (it.isSuccessful) {
                val body = it.body?.string() ?: ""
                "Success: $body"
            } else {
                "Failed: HTTP ${it.code}"
            }
        }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
