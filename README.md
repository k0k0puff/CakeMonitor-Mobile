# Cake Monitor Android App

Remote cake inventory monitoring via on-demand photo capture.

## Overview

The Cake Monitor app is a lightweight, always-on camera application for Android that runs on dedicated secondhand phones mounted at fixed locations (e.g., cake chillers or kitchen fridges). It listens for remote photo commands and captures images on demand, sending them to a central automation system that delivers them to the cafe owner via Telegram.

## Features

- **Foreground Service**: Runs continuously in background with camera access
- **HTTP Server**: Embedded NanoHTTPD server on configurable port (default: 8080)
- **Camera2 API**: Reliable background photo capture with auto-focus and auto-exposure
- **Callback System**: Async photo delivery via OkHttp with 3-retry logic
- **Auto-Start**: Boot receiver for automatic service restart after device reboot
- **Configuration**: Simple setup screen for location, device ID, port, and callback URL
- **Status Screen**: Real-time monitoring of service status, last capture time, and callback results

## Requirements

- **Platform**: Android 9+ (API 28+)
- **Target Device**: Dedicated Android phone with rear camera
- **Permissions**: Camera, Internet, Foreground Service, Boot Completed, Wake Lock, Battery Optimization Exemption
- **Dependencies**: NanoHTTPD, OkHttp, Camera2 API

## API Endpoints

### POST /snap
Triggers photo capture. Returns 200 immediately, captures photo asynchronously.

**Response**:
```json
{
  "status": "acknowledged"
}
```

If capture is already in progress:
```json
{
  "status": "busy"
}
```

### GET /health
Returns service status and location.

**Response**:
```json
{
  "status": "ok",
  "location": "Chiller"
}
```

## Callback Payload

After capturing a photo, the app sends a multipart/form-data POST to the configured callback URL with:

- `photo` (file): JPEG image
- `location` (string): Human-readable label (e.g., "Chiller")
- `timestamp` (string): Local time of capture (format: dd-MM-yyyy, hh:mm am/pm)
- `device_id` (string): Unique device identifier
- `status` (string): "ok" or "error"
- `error_message` (string, optional): Present only when status is "error"

## Setup Instructions

1. Install the APK on the target device
2. Open the app and tap "Setup Configuration"
3. Enter:
   - **Location Label**: e.g., "Chiller" or "Kitchen Fridge"
   - **Device ID**: e.g., "phone-a"
   - **HTTP Server Port**: default 8080
   - **Callback URL**: Your n8n webhook URL
   - **JPEG Quality**: 50-95 (default: 85)
4. Tap "Save"
5. Grant camera permission when prompted
6. Approve battery optimization exemption
7. Set device screen timeout to "Never" in display settings
8. Tap "Start Service" on the main screen
9. Verify the notification appears
10. Set up Cloudflare Tunnel to expose the HTTP server
11. Mount the phone pointing at the target area
12. Plug in the charger

## Testing

### Test HTTP Server
Use curl or Postman to test the endpoints:

```bash
# Health check
curl http://localhost:8080/health

# Trigger photo capture
curl -X POST http://localhost:8080/snap
```

### Test via Cloudflare Tunnel
After setting up the tunnel:

```bash
curl -X POST https://your-tunnel-url/snap
```

## Architecture

```
┌─────────────┐
│  Telegram   │ ← User sends command
│     Bot     │
└─────┬───────┘
      │
┌─────▼───────┐
│     n8n     │ ← Automation backend
│  Workflow   │
└─────┬───────┘
      │ POST /snap
┌─────▼───────────┐
│   Cloudflare    │
│     Tunnel      │
└─────┬───────────┘
      │
┌─────▼───────────┐
│   Android App   │
│  HTTP Server    │
├─────────────────┤
│ Camera2 Capture │
│  Photo Callback │
└─────────────────┘
```

## Technical Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **HTTP Server**: NanoHTTPD 2.3.1
- **HTTP Client**: OkHttp 4.12.0
- **Camera**: Android Camera2 API
- **Min SDK**: Android 9 (API 28)
- **Target SDK**: Android 14 (API 34)

## Critical Android Behaviors

- Android 9+ restricts camera access when app is in background → Foreground service with `FOREGROUND_SERVICE_CAMERA` type is required
- Battery optimization will kill the foreground service on most OEM devices → Must be explicitly disabled
- Screen timeout must be disabled or `FLAG_KEEP_SCREEN_ON` set to prevent camera restrictions
- NanoHTTPD runs on background thread and is safe within foreground service

## Error Handling

| Scenario | Behavior | Callback Sent? |
|----------|----------|----------------|
| Camera fails to open | Log error, abort capture | Yes (status: error) |
| Auto-focus timeout (>5s) | Proceed with capture (fixed-focus fallback) | Yes (status: ok) |
| Capture timeout (>10s) | Abort, treat as error | Yes (status: error) |
| Callback POST fails | Retry up to 3 times with 5s delay | Yes (after retries) |
| All retries exhausted | Log failure | No (already sent error) |
| Device reboots | Service restarts via boot receiver | No (awaits next trigger) |
| /snap during capture | Return HTTP 200 {status: busy} | No |

## License

Proprietary - AwesomeCyborg

## Version

1.0 - March 2026
