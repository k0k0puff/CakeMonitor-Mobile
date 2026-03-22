package com.awesomecyborg.cakemonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.awesomecyborg.cakemonitor.ui.theme.CakeMonitorTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handle results if needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Check and request permissions
        checkPermissions()

        setContent {
            CakeMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StatusScreen(
                        onStartService = { startMonitoringService() },
                        onStopService = { stopMonitoringService() },
                        onOpenSetup = { openSetupScreen() }
                    )
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        // Request battery optimization exemption on first launch
        if (Config.isFirstLaunch(this)) {
            requestBatteryOptimizationExemption()
            Config.setFirstLaunchComplete(this)
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to general battery settings
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (e2: Exception) {
                        // Unable to open battery settings
                    }
                }
            }
        }
    }

    private fun startMonitoringService() {
        val intent = Intent(this, CameraMonitorService::class.java).apply {
            action = CameraMonitorService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, CameraMonitorService::class.java).apply {
            action = CameraMonitorService.ACTION_STOP
        }
        startService(intent)
    }

    private fun openSetupScreen() {
        startActivity(Intent(this, SetupActivity::class.java))
    }
}

@Composable
fun StatusScreen(
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenSetup: () -> Unit
) {
    var isRunning by remember { mutableStateOf(CameraMonitorService.isRunning) }
    var lastCapture by remember { mutableStateOf(CameraMonitorService.lastCaptureTime) }
    var lastResult by remember { mutableStateOf(CameraMonitorService.lastCallbackResult) }

    // Poll service status every second
    LaunchedEffect(Unit) {
        while (true) {
            isRunning = CameraMonitorService.isRunning
            lastCapture = CameraMonitorService.lastCaptureTime
            lastResult = CameraMonitorService.lastCallbackResult
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Cake Monitor",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isRunning) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusRow("Status", if (isRunning) "Running" else "Stopped")
                StatusRow("Location", Config.getLocation(androidx.compose.ui.platform.LocalContext.current))
                StatusRow("HTTP Port", Config.getPort(androidx.compose.ui.platform.LocalContext.current).toString())
                StatusRow("Last Photo", lastCapture ?: "Never")
                StatusRow("Last Callback", lastResult ?: "—")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Control Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isRunning) {
                Button(
                    onClick = {
                        onStopService()
                        isRunning = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Stop Service")
                }
            } else {
                Button(
                    onClick = {
                        onStartService()
                        isRunning = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Service")
                }
            }

            OutlinedButton(
                onClick = onOpenSetup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Setup Configuration")
            }
        }

        // Info Text
        Text(
            text = "This app runs in the background and captures photos when triggered via HTTP POST to /snap endpoint.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}