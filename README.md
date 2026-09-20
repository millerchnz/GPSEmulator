# GPS Debug Tool

Android developer tool for exploring the platform location APIs. It does three things:

- **Mock** — feed a fake GPS position to every other app on the phone
- **Logging** — record every value the location API reports, on an interval you choose
- **Geofences** — register circular fences with Play Services and log the transitions

This is for local development and testing. Mock locations are flagged by the system (`isMock` / `isFromMockProvider`), so apps that reject mocked GPS will still see it as fake — and the logger records that flag, so you can watch it flip.

## Requirements

- Android 8.0+ (API 26)
- Precise location, notifications and system Location switched on — the app is gated on these
- **Mocking only:** developer options unlocked, and this app selected as the **mock location app**
- **Geofencing:** background location ("Allow all the time"), or Play Services will not deliver transitions

## First launch

The setup screen blocks until precise location, notifications and device location are ready. Everything else is asked for where it is needed: the mock screen prompts for developer options, and the logging and geofence screens prompt for background location.

## Mock

- Tap the map or drag the pin to choose a point
- Paste `lat, lng` (for example `-36.8485, 174.7633`) and tap **Go**
- Adjust reported accuracy if needed
- **Apply mock location** starts feeding GPS, network, and fused providers about once a second
- A live **Enabled for** timer shows how long the mock has been running; the notification shows the same elapsed time
- **Stop mock location** (in the app or the notification) turns it off

The mock keeps running if you switch to another app. Swiping GPS Debug Tool out of recents does not stop it; use Stop.

## Logging

- Pick the provider: **Fused** (Play Services), **GPS** or **Network** (`LocationManager`). Fused also exposes the request priority.
- Pick the interval, from **every second to every 30 minutes**
- Recording runs in a `location` foreground service, so the longer intervals still fire with the app closed. After a reboot, logging starts again on its own if it was left on.
- Every row keeps the whole fix: coordinates, horizontal and vertical accuracy, altitude and MSL altitude, speed, bearing, their accuracies, fix time, fix age, elapsed realtime and its uncertainty, the mock flag, satellite count and the raw provider extras. A value the platform did not report is stored as null and shown as `—`, so "not reported" never looks like zero.
- **View collected data** lists the rows newest first; tap one for every field. Filter by time range and provider, load older pages, export the whole filtered set as CSV or JSON, or clear the table.
- Storage is capped at the newest 100,000 rows per table.

Because the mock feed writes to the GPS, network and fused providers, logging while a mock is active records the mock — with `is_mock` set to true.

## Geofences

- Add a fence by tapping the map, or with **Add geofence**; tap an existing circle to edit it
- Each fence carries its own name, centre and radius, and chooses which transitions to watch (enter, exit, dwell with a loitering delay) plus its notification responsiveness
- Fences are registered with the Play Services `GeofencingClient`; transitions arrive in a broadcast receiver, so they are reported with the app closed
- Turning monitoring **on** asks Play Services whether you are already inside (ENTER/DWELL). Saving a fence, or the app restarting, does not — those would otherwise look like real crossings
- Alerts appear as a **toast** while the app is on screen and as a **notification** when it is not, per transition type
- Every transition is logged with the fence, the transition type, the distance from the fence centre, and every value the triggering location reported
- **View collected events** filters by time range, transition and fence, loads older pages, and exports as CSV or JSON

Play Services geofences are known to be unreliable with mocked locations and small radii; the event log records the raw error codes it reports.

## HTTP GET webhook

Logging and geofencing each have their own optional webhook. Give it a base URL (for example `https://example.com/gpslog`) and every record is also sent as a GET with the values as query parameters:

```
https://example.com/gpslog?device=<uuid>&recorded_at=2026-09-17T04:31:07Z&lat=-36.8485000&lon=174.7633000&accuracy=4.80&speed=1.400&…
```

- Every request includes a stable `device=` UUID generated on first launch (shown on the webhook card) so phones can be distinguished on the map
- Values the API did not report are left out of the URL rather than sent empty
- **Extra parameters** appends static `key=value` pairs, and a header field can carry an API key or bearer token
- The screen previews the exact URL as you type, using your most recent real fix once there is one
- **Send test request** performs the GET and shows the status code and response body
- `http://` and `https://` are both accepted (local collectors are often plain HTTP)
- A request that fails is queued and retried when connectivity returns, and again on a backoff if the network never dropped; the settings screen shows the pending count

## Build

```bash
./gradlew installDebug
```

Package: `com.millerchnz.gpsemulator`

SDK path is read from `local.properties` (`sdk.dir`), which is not committed.

## Stack

- Kotlin, Jetpack Compose, Material 3, navigation-compose
- osmdroid (OpenStreetMap tiles; no Google Maps key)
- Room for the log tables, SharedPreferences for settings
- `LocationManager` test providers plus Play Services fused mock mode for the mock feed
- `FusedLocationProviderClient` / `LocationManager` for logging, `GeofencingClient` for fences
- Foreground services (`location`) so the mock feed and the logger stay alive in the background
- `HttpURLConnection` for the webhook, with the retry queue in Room

Room's annotation processor runs through KSP, which registers generated sources via the Kotlin source-set DSL. AGP's built-in Kotlin support rejects that by default, so `gradle.properties` sets `android.disallowKotlinSourceSets=false`.
