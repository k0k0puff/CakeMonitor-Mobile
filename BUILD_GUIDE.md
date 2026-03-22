# Cake Monitor - Build & Deployment Guide

## Project Structure

```
CakeMonitor/
├── app/
│   ├── build.gradle.kts          # Dependencies: NanoHTTPD, OkHttp
│   └── src/main/
│       ├── AndroidManifest.xml   # Permissions & component declarations
│       ├── java/com/awesomecyborg/cakemonitor/
│       │   ├── MainActivity.kt           # Status screen with service controls
│       │   ├── SetupActivity.kt          # Configuration screen
│       │   ├── CameraMonitorService.kt   # Foreground service
│       │   ├── HttpServer.kt             # NanoHTTPD server (/snap, /health)
│       │   ├── CameraManager.kt          # Camera2 API wrapper
│       │   ├── CallbackHandler.kt        # OkHttp callback with retry
│       │   ├── BootReceiver.kt           # Auto-start on boot
│       │   └── Config.kt                 # SharedPreferences helper
│       └── res/
│           └── values/strings.xml
└── README.md
```

## Component Overview

### 1. **Config.kt**
- Manages SharedPreferences for app configuration
- Stores: location, device ID, port, callback URL, JPEG quality

### 2. **CameraManager.kt**
- Camera2 API implementation for background photo capture
- Features:
  - Auto-focus and auto-exposure (5s timeout)
  - Capture timeout (10s)
  - JPEG output with configurable quality
  - Proper background thread management
  - Error handling with descriptive messages

### 3. **CallbackHandler.kt**
- OkHttp-based callback delivery
- Multipart/form-data POST with photo + metadata
- Retry logic: 3 attempts with 5s delay
- Supports both success and error callbacks

### 4. **HttpServer.kt**
- NanoHTTPD embedded HTTP server
- Endpoints:
  - `POST /snap` - Triggers photo capture (returns 200 immediately)
  - `GET /health` - Returns status and location
- Busy state handling to prevent concurrent captures

### 5. **CameraMonitorService.kt**
- Foreground service with camera type
- Manages HTTP server lifecycle
- Coordinates camera capture and callback delivery
- WakeLock management during operations
- Persistent notification with status updates

### 6. **BootReceiver.kt**
- BroadcastReceiver for `BOOT_COMPLETED`
- Auto-starts service if configuration exists

### 7. **MainActivity.kt**
- Status screen showing:
  - Service status (Running/Stopped)
  - Location, port, last capture time, last callback result
  - Start/Stop buttons
  - Setup configuration button
- Keeps screen on with `FLAG_KEEP_SCREEN_ON`
- Requests battery optimization exemption on first launch

### 8. **SetupActivity.kt**
- Configuration screen with fields:
  - Location Label
  - Device ID
  - HTTP Server Port
  - Callback URL
  - JPEG Quality
- "Test Connection" button to verify HTTP server
- Input validation

## Build Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 11 or higher
- Android SDK with API 28-34

### Build Steps

1. **Open Project in Android Studio**
   ```bash
   cd C:\Users\poopd\AndroidStudioProjects\CakeMonitor
   # Open in Android Studio
   ```

2. **Sync Gradle**
   - Android Studio will automatically sync dependencies
   - Wait for NanoHTTPD and OkHttp to download

3. **Build APK**
   ```bash
   # Via Android Studio: Build > Build Bundle(s) / APK(s) > Build APK(s)

   # OR via command line:
   ./gradlew assembleDebug
   ```

4. **Build Release APK (Signed)**
   ```bash
   ./gradlew assembleRelease
   ```
   - Output: `app/build/outputs/apk/debug/app-debug.apk`
   - Or: `app/build/outputs/apk/release/app-release.apk`

## Deployment Instructions

### Device Preparation

1. **Enable Developer Options**
   - Settings > About Phone > Tap "Build Number" 7 times
   - Settings > Developer Options > Enable "USB Debugging"

2. **Install APK**
   ```bash
   # Via ADB
   adb install app/build/outputs/apk/debug/app-debug.apk

   # OR copy APK to device and install manually
   ```

3. **Initial Configuration**
   - Open the Cake Monitor app
   - Grant camera permission when prompted
   - Approve battery optimization exemption
   - Tap "Setup Configuration"
   - Enter configuration values:
     - **Location**: "Chiller" or "Kitchen Fridge"
     - **Device ID**: "phone-a", "phone-b", etc.
     - **Port**: 8080 (default)
     - **Callback URL**: Your n8n webhook URL (e.g., `https://your-n8n.com/webhook/cake-photo`)
     - **JPEG Quality**: 85 (default)
   - Tap "Save"

4. **Disable Battery Optimization (Critical)**
   - Settings > Battery > Battery Optimization
   - Find "Cake Monitor"
   - Select "Don't optimize"

5. **Disable Screen Timeout**
   - Settings > Display > Screen Timeout
   - Set to "Never" or maximum value

6. **Start Service**
   - Return to Cake Monitor app
   - Tap "Start Service"
   - Verify notification appears: "Cake Monitor - [Location]"

### Network Configuration

1. **Install Cloudflare Tunnel (via Termux)**
   ```bash
   # Install Termux from F-Droid
   # In Termux:
   pkg update
   pkg install cloudflared

   # Start tunnel
   cloudflared tunnel --url http://localhost:8080

   # Note the public URL (e.g., https://abc-def-ghi.trycloudflare.com)
   ```

2. **Configure n8n Workflow**
   - Create Workflow 1: Telegram command → HTTP POST to Cloudflare URL
   - Create Workflow 2: Webhook to receive photo → Forward to Telegram
   - Update callback URL in app setup to Workflow 2 webhook URL

### Testing

1. **Local Test (Before Cloudflare)**
   ```bash
   # Health check
   curl http://localhost:8080/health
   # Expected: {"status":"ok","location":"Chiller"}

   # Trigger capture
   curl -X POST http://localhost:8080/snap
   # Expected: {"status":"acknowledged"}
   ```

2. **Remote Test (Via Cloudflare)**
   ```bash
   curl -X POST https://your-tunnel-url.trycloudflare.com/snap
   ```

3. **End-to-End Test**
   - Send command to Telegram bot (e.g., "chiller")
   - Verify photo is received in Telegram
   - Check app status screen for "Last Photo" timestamp
   - Check "Last Callback" shows "Success"

### Physical Setup

1. **Mount Phone**
   - Use phone stand or wall mount
   - Point rear camera at target area (cake chiller/fridge)
   - Ensure good lighting and clear view
   - Stable positioning to avoid vibrations

2. **Power Connection**
   - Plug phone into charger
   - Use quality cable and adapter
   - Verify phone charges normally

3. **Final Verification**
   - Service is running (check notification)
   - Screen is on (due to FLAG_KEEP_SCREEN_ON)
   - Wi-Fi is connected
   - Camera view is correct (test capture)

## Troubleshooting

### Service Stops After Screen Off
- **Cause**: Battery optimization
- **Fix**: Disable battery optimization for Cake Monitor app

### Camera Permission Denied
- **Cause**: User denied permission
- **Fix**: Settings > Apps > Cake Monitor > Permissions > Camera > Allow

### HTTP Server Not Reachable
- **Cause**: Firewall or network issue
- **Fix**:
  - Verify service is running
  - Check notification shows "Listening on port 8080"
  - Test with `curl http://localhost:8080/health` on device (via Termux)

### Photo Callback Fails
- **Cause**: Invalid callback URL or network error
- **Fix**:
  - Verify callback URL is correct (HTTPS)
  - Check n8n webhook is active
  - Review app logs via `adb logcat | grep CakeMonitor`

### Service Doesn't Auto-Start on Boot
- **Cause**: Missing configuration or permission
- **Fix**:
  - Verify configuration is saved (open Setup screen)
  - Check RECEIVE_BOOT_COMPLETED permission is granted

### Capture Times Out
- **Cause**: Camera hardware issue or insufficient lighting
- **Fix**:
  - Improve lighting conditions
  - Test camera with native camera app
  - Check `adb logcat` for camera errors

## Monitoring & Logs

### View Logs
```bash
# Real-time logs
adb logcat | grep -E "CameraMonitor|HttpServer|CameraManager|CallbackHandler"

# Save logs to file
adb logcat -d > cake_monitor_logs.txt
```

### Key Log Tags
- `CameraMonitorService` - Service lifecycle and status
- `HttpServer` - HTTP request handling
- `CameraManager` - Photo capture operations
- `CallbackHandler` - Photo delivery and retries
- `BootReceiver` - Auto-start events

## Maintenance

### Update Configuration
1. Open app
2. Tap "Setup Configuration"
3. Modify values
4. Tap "Save"
5. Restart service (Stop → Start)

### Update APK
1. Build new APK with updated code
2. Install over existing app: `adb install -r app-debug.apk`
3. Configuration is preserved (SharedPreferences)
4. Restart service if needed

### Factory Reset
1. Uninstall app: `adb uninstall com.awesomecyborg.cakemonitor`
2. Reinstall fresh APK
3. Reconfigure from scratch

## Performance Expectations

- **Capture Time**: 3-8 seconds (depends on camera hardware and lighting)
- **Callback Time**: 2-10 seconds (depends on network speed and photo size)
- **Total Response Time**: 5-15 seconds from /snap request to photo delivery
- **Memory Usage**: ~50-150 MB (varies during capture)
- **CPU Usage**: Low when idle, moderate during capture
- **Battery Impact**: Minimal (device should remain plugged in)

## Security Considerations

- HTTP server has no authentication (relies on Cloudflare Tunnel for access control)
- No sensitive data stored or transmitted
- Camera permission required but only used when triggered
- Callback URL should be HTTPS for encrypted photo transmission

## Future Enhancements (Out of Scope for v1.0)

- Authentication for HTTP endpoints
- HTTPS support for local server
- Flash control
- Multiple camera support
- Image compression settings
- Scheduled captures (in addition to on-demand)
- Local image history/gallery
- Push notification when capture fails
- Remote configuration updates
