# GPS Debug Tool 1.0

17 September 2026 · `versionCode` 1 · `com.millerchnz.gpsemulator`

First release of GPS Debug Tool. The mock-location pin from GPS Emulator is still here, and the app now also logs real location API values and Play Services geofence transitions.

Requires Android 8.0+ (API 26). Precise location, notifications, and system Location must be on before the rest of the app opens.

## Mock location

- Drop or drag a pin on the map, or paste `lat, lng` and tap Go.
- Feeds GPS, network, and fused providers about once a second while the mock is on.
- Keeps running in the background; swipe-away does not stop it. Use Stop in the app or the notification.
- Needs Developer options, with this app selected as the mock location app.

## Location logging

- Record fixes from Fused, GPS, or Network, from every second to every 30 minutes.
- Each row stores every value the platform reported (coordinates, accuracies, altitude, speed, bearing, mock flag, satellites, extras). Unreported fields stay empty rather than looking like zero.
- Runs as a location foreground service, including with the app closed, and starts again after reboot if logging was left on.
- Viewer: newest first, filter by time and provider, page older rows, export CSV/JSON, or clear.

## Geofences

- Draw circular fences on the map (radius 1–250 m) with enter, exit, and dwell.
- Registered with Play Services `GeofencingClient`; transitions are delivered with the app closed.
- Turning monitoring on asks whether you are already inside. Saving a fence or restarting the app does not, so those are not logged as fake crossings.
- Toast while the app is on screen, notification otherwise.
- Event viewer: filter by time, transition, and fence; export CSV/JSON.

Play Services geofences are unreliable with mocked locations and small radii. The event log keeps the raw error codes.

## HTTP GET webhooks

- Optional GET per log row and per geofence event. Reported fields go on the query string; missing values are omitted.
- Every request includes a stable per-install `device=` UUID (shown on the webhook card).
- Extra query parameters, optional auth header, live URL preview, and a test send.
- Failed requests are queued and retried on connectivity and on backoff. `http://` and `https://` are both accepted.

## Known limits

- Mocked fixes are flagged (`is_mock`). Apps that reject mock GPS will still see them as fake.
- Storage is capped at the newest 100,000 rows per log table.
- Existing installs keep the same application id; Developer options will list the new name.
