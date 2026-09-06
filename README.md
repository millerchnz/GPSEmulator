# GPS Emulator

Android developer tool that feeds a fake GPS position to other apps on the same phone. Drop a pin on the map (or paste coordinates) and apply it; the device reports that location until you stop.

This is for local development and testing. Mock locations are flagged by the system (`isMock` / `isFromMockProvider`), so apps that reject mocked GPS will still see it as fake.

## Requirements

- Android 8.0+ (API 26)
- Developer options unlocked on the device
- This app selected as the **mock location app**

## First launch

The map does not open until every check on the setup screen is green:

1. **Precise location** — grant fine location
2. **Notifications** — needed for the mock-location foreground service (Android 13+)
3. **Device location on** — system Location toggle
4. **Developer options** — tap Build number seven times in About phone
5. **Select mock location app** — Developer options → **Select mock location app** → **GPS Emulator**

Each card opens the matching settings screen. Coming back to the app re-checks automatically.

## Using it

- Tap the map or drag the pin to choose a point
- Paste `lat, lng` (for example `-36.8485, 174.7633`) and tap **Go**
- Adjust reported accuracy if needed
- **Apply mock location** starts feeding GPS, network, and fused providers about once a second
- A live **Enabled for** timer shows how long the mock has been running; the notification shows the same elapsed time
- **Stop mock location** (in the app or the notification) turns it off

The mock keeps running if you switch to another app. Swiping GPS Emulator out of recents does not stop it; use Stop.

## Build

```bash
./gradlew installDebug
```

Package: `com.millerchnz.gpsemulator`

SDK path is read from `local.properties` (`sdk.dir`), which is not committed.

## Stack

- Kotlin, Jetpack Compose, Material 3
- osmdroid (OpenStreetMap tiles; no Google Maps key)
- `LocationManager` test providers plus Play Services fused mock mode
- Foreground service (`location`) so the feed stays alive in the background
