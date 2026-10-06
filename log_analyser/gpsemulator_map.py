#!/usr/bin/env python3
"""Parse nginx access logs for GPS Debug Tool webhooks and plot them.

The GPS Debug Tool app GETs (location logging and geofence events share the path):

    https://airael.app/gpslog?device=<uuid>&recorded_at=…&recorded_at_ms=…&lat=…&lon=…
        &accuracy=…&speed=…&provider=…&requested_provider=…&is_mock=…
        &transition=ENTER&fence_name=Home&fence_lat=…&fence_lon=…&fence_radius=…

The app always sends ``device=<uuid>`` (a per-install id, shown on the webhook
card). Older lines without it fall back to the nginx client IP.

nginx records the request even if the path 404s. This script ingests those
lines into SQLite and writes a self-contained Leaflet HTML map.

The report shows nothing until a device is picked. The dropdowns mirror the
page's own query so links can jump straight to a selection:

    ?device=<id>&day=<YYYY-MM-DD>&kind=<all|fix|geofence|ENTER|EXIT|DWELL|ERROR>

Day defaults to today in New Zealand (``Pacific/Auckland``, NZST or NZDT).
``day=all`` shows every sample for the device. Times in the table and popups
are local NZ, not UTC.

Location fixes are the red track. Geofence transitions are coloured markers
(and fence-radius circles) and are not joined into the polyline.

Companion to gpslog_map.py (airael debug pings use ``coords=`` / ``ev=``).

Usage:
    python gpsemulator_map.py /var/log/nginx/access.log
    python gpsemulator_map.py access.log access.log.1.gz -o emulator.html --db emulator.sqlite
    python gpsemulator_map.py --html-only --db emulator.sqlite -o emulator.html

Cron (re-read the live log and the file it just rotated; line hashes skip duplicates):
    */15 * * * * python3 /opt/GPSEmulator/log_analyser/gpsemulator_map.py \\
        /var/log/nginx/access.log /var/log/nginx/access.log.1 \\
        --db /var/lib/gpsemulator/samples.sqlite \\
        -o /var/www/gpsemulator/map.html
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import re
import sqlite3
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import parse_qs, urlparse
from zoneinfo import ZoneInfo

LOG_LINE = re.compile(
    r"^(?P<ip>\S+)\s+\S+\s+\S+\s+\[(?P<time>[^\]]+)\]\s+"
    r'"(?P<request>[^"]*)"\s+(?P<status>\S+)\s+(?P<size>\S+)'
)

NGINX_TIME = "%d/%b/%Y:%H:%M:%S %z"
GPSLOG_PATH = "/gpslog"
DISPLAY_TZ = ZoneInfo("Pacific/Auckland")

SCHEMA = """
CREATE TABLE IF NOT EXISTS samples (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device TEXT NOT NULL,
    lat REAL NOT NULL,
    lon REAL NOT NULL,
    sampled_at TEXT NOT NULL,
    sampled_epoch INTEGER NOT NULL,
    day TEXT NOT NULL,
    kind TEXT NOT NULL,
    transition TEXT,
    accuracy_m REAL,
    speed_mps REAL,
    bearing_deg REAL,
    altitude_m REAL,
    provider TEXT,
    requested_provider TEXT,
    is_mock INTEGER,
    satellites INTEGER,
    fix_age_ms INTEGER,
    fence_id TEXT,
    fence_name TEXT,
    fence_lat REAL,
    fence_lon REAL,
    fence_radius_m REAL,
    distance_from_center_m REAL,
    error TEXT,
    extras TEXT,
    status INTEGER,
    source_file TEXT,
    line_hash TEXT NOT NULL UNIQUE
);
CREATE INDEX IF NOT EXISTS idx_emu_device_day ON samples(device, day);
CREATE INDEX IF NOT EXISTS idx_emu_epoch ON samples(sampled_epoch);
"""

ADDED_COLUMNS = (
    ("kind", "TEXT"),
    ("transition", "TEXT"),
    ("bearing_deg", "REAL"),
    ("altitude_m", "REAL"),
    ("requested_provider", "TEXT"),
    ("is_mock", "INTEGER"),
    ("satellites", "INTEGER"),
    ("fix_age_ms", "INTEGER"),
    ("fence_id", "TEXT"),
    ("fence_name", "TEXT"),
    ("fence_lat", "REAL"),
    ("fence_lon", "REAL"),
    ("fence_radius_m", "REAL"),
    ("distance_from_center_m", "REAL"),
    ("error", "TEXT"),
    ("extras", "TEXT"),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Plot GPS Debug Tool /gpslog nginx samples on a map and store them in SQLite."
    )
    parser.add_argument(
        "logfiles",
        nargs="*",
        type=Path,
        help="Nginx access log(s) (plain text or .gz). Repeatable.",
    )
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        default=None,
        help="HTML report path (default: gpsemulator_map.html next to the database)",
    )
    parser.add_argument(
        "--db",
        type=Path,
        default=Path("gpsemulator_samples.sqlite"),
        help="SQLite database path (created if missing; incremental via line hash)",
    )
    parser.add_argument(
        "--html-only",
        action="store_true",
        help="Skip log ingest; rebuild the HTML from the existing database",
    )
    return parser.parse_args()


def open_log(path: Path):
    if path.suffix == ".gz":
        return gzip.open(path, "rt", encoding="utf-8", errors="replace")
    return path.open("rt", encoding="utf-8", errors="replace")


def line_hash(line: str) -> str:
    return hashlib.sha256(line.encode("utf-8", errors="replace")).hexdigest()


def parse_measure(raw: str | None) -> float | None:
    if raw is None:
        return None
    try:
        value = float(raw.strip())
    except ValueError:
        return None
    if value != value or value in (float("inf"), float("-inf")):
        return None
    return value


def parse_int(raw: str | None) -> int | None:
    if raw is None or raw.strip() == "":
        return None
    try:
        return int(float(raw.strip()))
    except ValueError:
        return None


def parse_bool(raw: str | None) -> int | None:
    if raw is None:
        return None
    value = raw.strip().lower()
    if value in ("true", "1", "yes"):
        return 1
    if value in ("false", "0", "no"):
        return 0
    return None


def parse_position(fields: dict[str, str]) -> tuple[float, float] | None:
    lat = parse_measure(fields.get("lat"))
    lon = parse_measure(fields.get("lon"))
    if lat is not None and lon is not None and -90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0:
        return lat, lon
    fence_lat = parse_measure(fields.get("fence_lat"))
    fence_lon = parse_measure(fields.get("fence_lon"))
    if (
        fence_lat is not None
        and fence_lon is not None
        and -90.0 <= fence_lat <= 90.0
        and -180.0 <= fence_lon <= 180.0
    ):
        return fence_lat, fence_lon
    return None


def aware(when: datetime) -> datetime:
    if when.tzinfo is None:
        return when.replace(tzinfo=timezone.utc)
    return when


def local_parts(when: datetime) -> tuple[str, str]:
    """Clock time and calendar day in New Zealand (NZST / NZDT)."""
    local = aware(when).astimezone(DISPLAY_TZ)
    return local.strftime("%Y-%m-%d %H:%M:%S %Z"), local.date().isoformat()


def parse_recorded_at(fields: dict[str, str], nginx_when: datetime) -> datetime:
    ms = parse_int(fields.get("recorded_at_ms"))
    if ms is not None and ms > 0:
        return datetime.fromtimestamp(ms / 1000.0, tz=timezone.utc)
    iso = (fields.get("recorded_at") or "").strip()
    if iso:
        try:
            parsed = datetime.fromisoformat(iso.replace("Z", "+00:00"))
            if parsed.tzinfo is None:
                parsed = parsed.replace(tzinfo=timezone.utc)
            return parsed
        except ValueError:
            pass
    return nginx_when


def parse_gpslog_request(request: str) -> dict[str, str] | None:
    parts = request.split()
    if len(parts) < 2:
        return None
    parsed = urlparse(parts[1])
    if parsed.path.rstrip("/") != GPSLOG_PATH:
        return None
    query = parse_qs(parsed.query, keep_blank_values=False)
    return {key: values[0].strip() for key, values in query.items() if values}


def parse_nginx_time(value: str) -> datetime | None:
    try:
        return datetime.strptime(value, NGINX_TIME)
    except ValueError:
        return None


def extract_samples(path: Path) -> list[dict]:
    rows: list[dict] = []
    unmatched = 0
    unplottable = 0
    with open_log(path) as handle:
        for line in handle:
            raw = line.rstrip("\n")
            if not raw:
                continue
            match = LOG_LINE.match(raw)
            if not match:
                unmatched += 1
                continue
            fields = parse_gpslog_request(match.group("request"))
            if fields is None:
                continue
            # Skip the older airael debug format (coords=/ev=) — gpslog_map.py owns those.
            if "coords" in fields and "lat" not in fields:
                continue
            point = parse_position(fields)
            if point is None:
                unplottable += 1
                continue
            when_nginx = parse_nginx_time(match.group("time"))
            if when_nginx is None:
                continue
            when = parse_recorded_at(fields, when_nginx)
            try:
                status = int(match.group("status"))
            except ValueError:
                status = None
            lat, lon = point
            transition = fields.get("transition") or None
            device = (fields.get("device") or "").strip() or match.group("ip")
            sampled_at, day = local_parts(when)
            rows.append(
                {
                    "device": device,
                    "lat": lat,
                    "lon": lon,
                    "sampled_at": sampled_at,
                    "sampled_epoch": int(aware(when).timestamp()),
                    "day": day,
                    "kind": "geofence" if transition else "fix",
                    "transition": transition,
                    "accuracy_m": parse_measure(fields.get("accuracy")),
                    "speed_mps": parse_measure(fields.get("speed")),
                    "bearing_deg": parse_measure(fields.get("bearing")),
                    "altitude_m": parse_measure(fields.get("altitude")),
                    "provider": fields.get("provider") or None,
                    "requested_provider": fields.get("requested_provider") or None,
                    "is_mock": parse_bool(fields.get("is_mock")),
                    "satellites": parse_int(fields.get("satellites")),
                    "fix_age_ms": parse_int(fields.get("fix_age_ms")),
                    "fence_id": fields.get("fence_id") or None,
                    "fence_name": fields.get("fence_name") or None,
                    "fence_lat": parse_measure(fields.get("fence_lat")),
                    "fence_lon": parse_measure(fields.get("fence_lon")),
                    "fence_radius_m": parse_measure(fields.get("fence_radius")),
                    "distance_from_center_m": parse_measure(fields.get("distance_from_center")),
                    "error": fields.get("error") or None,
                    "extras": fields.get("extras") or None,
                    "status": status,
                    "source_file": path.name,
                    "line_hash": line_hash(raw),
                }
            )
    if unmatched:
        print(f"{path}: skipped {unmatched} unparseable line(s).", file=sys.stderr)
    if unplottable:
        print(
            f"{path}: skipped {unplottable} emulator ping(s) without lat/lon (or a fence centre).",
            file=sys.stderr,
        )
    return rows


def connect_db(path: Path) -> sqlite3.Connection:
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.executescript(SCHEMA)
    add_missing_columns(conn)
    return conn


def add_missing_columns(conn: sqlite3.Connection) -> None:
    existing = {row["name"] for row in conn.execute("PRAGMA table_info(samples)")}
    for name, decl in ADDED_COLUMNS:
        if name not in existing:
            conn.execute(f"ALTER TABLE samples ADD COLUMN {name} {decl}")
    conn.commit()


def ingest(conn: sqlite3.Connection, rows: list[dict]) -> tuple[int, int]:
    inserted = 0
    skipped = 0
    sql = """
        INSERT OR IGNORE INTO samples (
            device, lat, lon, sampled_at, sampled_epoch, day,
            kind, transition, accuracy_m, speed_mps, bearing_deg, altitude_m,
            provider, requested_provider, is_mock, satellites, fix_age_ms,
            fence_id, fence_name, fence_lat, fence_lon, fence_radius_m,
            distance_from_center_m, error, extras,
            status, source_file, line_hash
        ) VALUES (
            :device, :lat, :lon, :sampled_at, :sampled_epoch, :day,
            :kind, :transition, :accuracy_m, :speed_mps, :bearing_deg, :altitude_m,
            :provider, :requested_provider, :is_mock, :satellites, :fix_age_ms,
            :fence_id, :fence_name, :fence_lat, :fence_lon, :fence_radius_m,
            :distance_from_center_m, :error, :extras,
            :status, :source_file, :line_hash
        )
    """
    for row in rows:
        cursor = conn.execute(sql, row)
        if cursor.rowcount:
            inserted += 1
        else:
            skipped += 1
    conn.commit()
    return inserted, skipped


def refresh_display_times(conn: sqlite3.Connection) -> None:
    """Rewrite stored clock times and calendar days into Pacific/Auckland.

    ``sampled_epoch`` stays Unix UTC. Existing rows ingested as UTC dates are
    corrected so --html-only picks up the local day without re-reading logs.
    """
    rows = conn.execute("SELECT id, sampled_epoch FROM samples").fetchall()
    for row in rows:
        sampled_at, day = local_parts(
            datetime.fromtimestamp(int(row["sampled_epoch"]), tz=timezone.utc)
        )
        conn.execute(
            "UPDATE samples SET sampled_at = ?, day = ? WHERE id = ?",
            (sampled_at, day, row["id"]),
        )
    conn.commit()


def load_samples(conn: sqlite3.Connection) -> list[dict]:
    cursor = conn.execute(
        """
        SELECT device, lat, lon, sampled_at, sampled_epoch, day,
               kind, transition, accuracy_m, speed_mps, bearing_deg, altitude_m,
               provider, requested_provider, is_mock, satellites, fix_age_ms,
               fence_id, fence_name, fence_lat, fence_lon, fence_radius_m,
               distance_from_center_m, error, extras
        FROM samples
        ORDER BY sampled_epoch ASC, id ASC
        """
    )
    keys = [
        "device", "lat", "lon", "sampled_at", "sampled_epoch", "day",
        "kind", "transition", "accuracy_m", "speed_mps", "bearing_deg", "altitude_m",
        "provider", "requested_provider", "is_mock", "satellites", "fix_age_ms",
        "fence_id", "fence_name", "fence_lat", "fence_lon", "fence_radius_m",
        "distance_from_center_m", "error", "extras",
    ]
    return [{key: row[key] for key in keys} for row in cursor]


def js_literal(value) -> str:
    """JSON for an inline <script>; ping fields are attacker-controlled free text."""
    return (
        json.dumps(value, separators=(",", ":"))
        .replace("<", "\\u003c")
        .replace(">", "\\u003e")
        .replace("&", "\\u0026")
    )


def render_html(samples: list[dict], db_path: Path) -> str:
    devices = sorted({row["device"] for row in samples})
    payload = js_literal(samples)
    title = "GPS Debug Tool samples"

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{title}</title>
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
  <style>
    :root {{
      --bg: #0f1419;
      --panel: #1a2332;
      --text: #e7ecf3;
      --muted: #93a0b5;
      --accent: #3d9cf0;
      --border: #2a3648;
      --row-hover: #243044;
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      font-family: "Segoe UI", system-ui, sans-serif;
      background: var(--bg);
      color: var(--text);
    }}
    header {{
      padding: 1.25rem 1.5rem 0.75rem;
    }}
    h1 {{
      margin: 0 0 0.35rem;
      font-size: 1.4rem;
      font-weight: 600;
    }}
    .summary {{
      color: var(--muted);
      font-size: 0.95rem;
    }}
    .filters {{
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem 1.25rem;
      align-items: center;
      padding: 0 1.5rem 1rem;
    }}
    label {{
      color: var(--muted);
      font-size: 0.9rem;
      display: flex;
      gap: 0.45rem;
      align-items: center;
    }}
    select {{
      padding: 0.4rem 0.55rem;
      border: 1px solid var(--border);
      border-radius: 6px;
      background: var(--panel);
      color: var(--text);
      min-width: 12rem;
    }}
    #map {{
      height: 62vh;
      min-height: 360px;
      border-top: 1px solid var(--border);
      border-bottom: 1px solid var(--border);
    }}
    .table-wrap {{
      padding: 1.25rem 1.5rem 2rem;
      overflow-x: auto;
    }}
    table {{
      width: 100%;
      border-collapse: collapse;
      background: var(--panel);
      font-size: 0.9rem;
    }}
    th, td {{
      padding: 0.55rem 0.7rem;
      border-bottom: 1px solid var(--border);
      text-align: left;
      vertical-align: top;
    }}
    th {{
      position: sticky;
      top: 0;
      background: #223049;
      white-space: nowrap;
    }}
    tbody tr:hover {{ background: var(--row-hover); }}
    tbody tr {{ cursor: pointer; }}
    tbody tr.empty {{ cursor: default; }}
    tbody tr.empty:hover {{ background: transparent; }}
    code {{
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      font-size: 0.85em;
    }}
    .muted {{ color: var(--muted); }}
    .leaflet-popup-content {{ color: #1a1a1a; }}
    .track-arrow {{
      background: none;
      border: none;
    }}
    .track-arrow i {{
      display: block;
      width: 0;
      height: 0;
      border-left: 7px solid transparent;
      border-right: 7px solid transparent;
      border-bottom: 16px solid;
      transform-origin: 50% 70%;
    }}
    .legend {{
      display: flex;
      flex-wrap: wrap;
      gap: 0.45rem 0.9rem;
      padding: 0 1.5rem 1rem;
      color: var(--muted);
      font-size: 0.8rem;
    }}
    .legend span {{
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
    }}
    .swatch {{
      width: 0.7rem;
      height: 0.7rem;
      border-radius: 50%;
      border: 1px solid #0006;
      display: inline-block;
    }}
    .event-dot {{
      display: inline-block;
      width: 0.6rem;
      height: 0.6rem;
      border-radius: 50%;
      margin-right: 0.4rem;
      vertical-align: middle;
      border: 1px solid #0006;
    }}
  </style>
</head>
<body>
  <header>
    <h1>{title}</h1>
    <p class="summary" id="summary">Loading…</p>
  </header>
  <div class="filters">
    <label>Device
      <select id="device"></select>
    </label>
    <label>Day
      <select id="day"></select>
    </label>
    <label>Events
      <select id="kind"></select>
    </label>
  </div>
  <div class="legend" id="legend"></div>
  <div id="map"></div>
  <div class="table-wrap">
    <table>
      <thead>
        <tr>
          <th>#</th>
          <th>Time (NZ)</th>
          <th>Event</th>
          <th>Coordinates</th>
          <th>Accuracy</th>
          <th>Speed</th>
          <th>Provider</th>
          <th>Fence</th>
        </tr>
      </thead>
      <tbody id="rows"></tbody>
    </table>
  </div>
  <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
  <script>
    const samples = {payload};
    const dbName = {js_literal(db_path.name)};
    const allDevices = {js_literal(devices)};
    const DEFAULT_STYLE = {{ stroke: "#334155", fill: "#94a3b8", radius: 6, track: false }};
    const EVENT_STYLE = {{
      fix:   {{ stroke: "#7a1410", fill: "#b42318", radius: 6, track: true }},
      ENTER: {{ stroke: "#14532d", fill: "#22c55e", radius: 8, track: false }},
      DWELL: {{ stroke: "#134e4a", fill: "#14b8a6", radius: 8, track: false }},
      EXIT:  {{ stroke: "#7c2d12", fill: "#f97316", radius: 8, track: false }},
      ERROR: {{ stroke: "#701a75", fill: "#e879f9", radius: 8, track: false }}
    }};
    const KIND_OPTIONS = [
      ["all", "All events"],
      ["fix", "Fixes (track)"],
      ["geofence", "Geofence"],
      ["ENTER", "Entered"],
      ["EXIT", "Exited"],
      ["DWELL", "Dwelling"],
      ["ERROR", "Error"]
    ];
    const LEGEND = [
      ["fix", "Fix"],
      ["ENTER", "Enter"],
      ["DWELL", "Dwell"],
      ["EXIT", "Exit"],
      ["ERROR", "Error"]
    ];

    const deviceSelect = document.getElementById("device");
    const daySelect = document.getElementById("day");
    const kindSelect = document.getElementById("kind");
    const summary = document.getElementById("summary");
    const tbody = document.getElementById("rows");
    const params = new URLSearchParams(location.search);
    const requestedDevice = params.get("device") || "";
    const requestedDay = params.get("day");
    const requestedKind = params.get("kind") || "all";

    function styleFor(row) {{
      if (row.kind === "fix" || !row.transition) return EVENT_STYLE.fix;
      return EVENT_STYLE[row.transition] || DEFAULT_STYLE;
    }}

    function eventLabel(row) {{
      if (row.kind === "geofence") return row.transition || "geofence";
      return "fix";
    }}

    function matchesKind(row, kind) {{
      if (kind === "all") return true;
      if (kind === "fix") return row.kind === "fix";
      if (kind === "geofence") return row.kind === "geofence";
      return row.transition === kind;
    }}

    function todayIso() {{
      return new Intl.DateTimeFormat("en-CA", {{
        timeZone: "Pacific/Auckland",
        year: "numeric",
        month: "2-digit",
        day: "2-digit"
      }}).format(new Date());
    }}

    function esc(value) {{
      const holder = document.createElement("div");
      holder.textContent = value === null || value === undefined ? "" : String(value);
      return holder.innerHTML;
    }}

    function num(value) {{
      return typeof value === "number" && isFinite(value) ? value : null;
    }}

    function fmtAccuracy(value) {{
      const acc = num(value);
      if (acc === null) return "";
      return "±" + acc.toFixed(acc < 10 ? 1 : 0) + " m";
    }}

    function fmtSpeed(value) {{
      const spd = num(value);
      return spd === null ? "" : spd.toFixed(1) + " m/s";
    }}

    function fmtMock(value) {{
      if (value === 1 || value === true) return "mock";
      if (value === 0 || value === false) return "real";
      return "";
    }}

    function popupHtml(row) {{
      const lines = [
        "<strong>" + esc(eventLabel(row)) + "</strong>",
        esc(row.sampled_at),
        row.lat.toFixed(6) + ", " + row.lon.toFixed(6)
      ];
      const acc = fmtAccuracy(row.accuracy_m);
      if (acc) lines.push("Accuracy " + esc(acc));
      const spd = num(row.speed_mps);
      if (spd !== null) {{
        lines.push("Speed " + spd.toFixed(1) + " m/s (" + (spd * 3.6).toFixed(1) + " km/h)");
      }}
      const brg = num(row.bearing_deg);
      if (brg !== null) lines.push("Bearing " + brg.toFixed(0) + "°");
      const alt = num(row.altitude_m);
      if (alt !== null) lines.push("Altitude " + alt.toFixed(1) + " m");
      if (row.provider) lines.push("Provider " + esc(row.provider));
      if (row.requested_provider) lines.push("Requested " + esc(row.requested_provider));
      const mock = fmtMock(row.is_mock);
      if (mock) lines.push(mock === "mock" ? "Mocked fix" : "Not mocked");
      if (row.satellites != null) lines.push("Satellites " + esc(row.satellites));
      if (row.fix_age_ms != null) lines.push("Fix age " + esc(row.fix_age_ms) + " ms");
      if (row.fence_name || row.fence_id) {{
        lines.push("Fence " + esc(row.fence_name || row.fence_id));
      }}
      const radius = num(row.fence_radius_m);
      if (radius !== null) lines.push("Radius " + radius.toFixed(0) + " m");
      const dist = num(row.distance_from_center_m);
      if (dist !== null) lines.push("From centre " + dist.toFixed(0) + " m");
      if (row.error) lines.push("Error " + esc(row.error));
      if (row.extras) lines.push("Extras " + esc(row.extras));
      lines.push("<code>" + esc(row.device) + "</code>");
      return lines.join("<br>");
    }}

    function tooltipHtml(row) {{
      const bits = [eventLabel(row)];
      const acc = fmtAccuracy(row.accuracy_m);
      if (acc) bits.push(acc);
      const spd = fmtSpeed(row.speed_mps);
      if (spd) bits.push(spd);
      if (row.provider) bits.push(row.provider);
      if (row.fence_name) bits.push(row.fence_name);
      return esc(bits.join(" · "));
    }}

    function bearingDeg(from, to) {{
      const lat1 = from[0] * Math.PI / 180;
      const lat2 = to[0] * Math.PI / 180;
      const dLon = (to[1] - from[1]) * Math.PI / 180;
      const y = Math.sin(dLon) * Math.cos(lat2);
      const x = Math.cos(lat1) * Math.sin(lat2) -
        Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
      return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
    }}

    function addTrack(group, points, color, arrowColor) {{
      if (points.length < 2) return;
      L.polyline(points, {{ color, weight: 4, opacity: 0.9 }}).addTo(group);
      for (let i = 1; i < points.length; i++) {{
        const from = points[i - 1];
        const to = points[i];
        if (from[0] === to[0] && from[1] === to[1]) continue;
        const pos = [
          (from[0] + to[0]) / 2,
          (from[1] + to[1]) / 2
        ];
        const deg = bearingDeg(from, to);
        L.marker(pos, {{
          interactive: false,
          keyboard: false,
          icon: L.divIcon({{
            className: "track-arrow",
            iconSize: [20, 20],
            iconAnchor: [10, 10],
            html: "<i style=\\"border-bottom-color:" + arrowColor +
              ";transform:rotate(" + deg.toFixed(1) + "deg)\\"></i>"
          }})
        }}).addTo(group);
      }}
    }}

    function fillSelect(select, values, allLabel, preferred) {{
      select.innerHTML = "";
      const all = document.createElement("option");
      all.value = "all";
      all.textContent = allLabel;
      select.appendChild(all);
      for (const value of values) {{
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        select.appendChild(option);
      }}
      if (preferred && [...select.options].some((opt) => opt.value === preferred)) {{
        select.value = preferred;
      }}
    }}

    function fillDeviceSelect() {{
      deviceSelect.innerHTML = "";
      const placeholder = document.createElement("option");
      placeholder.value = "";
      placeholder.textContent = "Select a device";
      deviceSelect.appendChild(placeholder);
      for (const value of allDevices) {{
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        deviceSelect.appendChild(option);
      }}
      if (allDevices.includes(requestedDevice)) {{
        deviceSelect.value = requestedDevice;
      }}
    }}

    function selectedDevice() {{
      return deviceSelect.value;
    }}

    function selectedKind() {{
      return kindSelect.value || "all";
    }}

    function visibleSamples() {{
      const device = selectedDevice();
      if (!device) return [];
      const day = daySelect.value;
      const kind = selectedKind();
      return samples.filter((row) =>
        row.device === device &&
        (day === "all" || row.day === day) &&
        matchesKind(row, kind)
      );
    }}

    function daysForDevice(device) {{
      const set = new Set();
      if (!device) return [];
      for (const row of samples) {{
        if (row.device === device) set.add(row.day);
      }}
      return [...set].sort().reverse();
    }}

    function preferredDay() {{
      if (requestedDay === "all") return "all";
      return requestedDay || todayIso();
    }}

    function reloadWithSelection() {{
      const url = new URL(location.href);
      const device = deviceSelect.value;
      const day = daySelect.value;
      const kind = kindSelect.value;
      if (device) {{
        url.searchParams.set("device", device);
      }} else {{
        url.searchParams.delete("device");
      }}
      if (day) {{
        url.searchParams.set("day", day);
      }} else {{
        url.searchParams.delete("day");
      }}
      if (kind && kind !== "all") {{
        url.searchParams.set("kind", kind);
      }} else {{
        url.searchParams.delete("kind");
      }}
      location.assign(url);
    }}

    const map = L.map("map", {{ worldCopyJump: true }}).setView([-41.29, 174.78], 12);
    L.tileLayer("https://{{s}}.tile.openstreetmap.org/{{z}}/{{x}}/{{y}}.png", {{
      maxZoom: 19,
      attribution: "&copy; OpenStreetMap contributors"
    }}).addTo(map);

    let layer = L.layerGroup().addTo(map);
    const markerByIndex = [];

    function drawFences(visible, target) {{
      const seen = new Set();
      visible.forEach((row) => {{
        const lat = num(row.fence_lat);
        const lon = num(row.fence_lon);
        const radius = num(row.fence_radius_m);
        if (lat === null || lon === null || radius === null || radius <= 0) return;
        const key = (row.fence_id || "") + "|" + lat + "|" + lon + "|" + radius;
        if (seen.has(key)) return;
        seen.add(key);
        const style = styleFor(row);
        L.circle([lat, lon], {{
          radius: radius,
          color: style.stroke,
          weight: 2,
          opacity: 0.8,
          fillColor: style.fill,
          fillOpacity: 0.08
        }}).bindTooltip(esc(row.fence_name || row.fence_id || "fence") +
          " · r " + radius.toFixed(0) + " m").addTo(target);
      }});
    }}

    function render() {{
      const device = selectedDevice();
      const visible = visibleSamples();
      layer.clearLayers();
      markerByIndex.length = 0;
      const group = L.featureGroup();
      const accuracyGroup = L.featureGroup();
      const fenceGroup = L.featureGroup();
      const path = [];
      const markers = [];
      drawFences(visible, fenceGroup);
      visible.forEach((row, index) => {{
        const style = styleFor(row);
        if (style.track) {{
          const acc = num(row.accuracy_m);
          if (acc !== null && acc > 0) {{
            L.circle([row.lat, row.lon], {{
              radius: acc,
              interactive: false,
              color: style.stroke,
              weight: 1,
              opacity: 0.55,
              dashArray: "4 3",
              fillColor: style.fill,
              fillOpacity: 0.12
            }}).addTo(accuracyGroup);
          }}
          path.push([row.lat, row.lon]);
        }}
        markers.push([row, style, index]);
      }});
      fenceGroup.addTo(layer);
      accuracyGroup.addTo(layer);
      addTrack(group, path, EVENT_STYLE.fix.fill, EVENT_STYLE.fix.stroke);
      markers.forEach(([row, style, index]) => {{
        const marker = L.circleMarker([row.lat, row.lon], {{
          radius: style.radius,
          color: style.stroke,
          weight: 2,
          fillColor: style.fill,
          fillOpacity: 1
        }}).bindPopup(popupHtml(row)).bindTooltip(tooltipHtml(row));
        marker.addTo(group);
        markerByIndex[index] = marker;
      }});
      group.addTo(layer);
      const boundsTarget = L.featureGroup([group, fenceGroup]);
      if (visible.length) {{
        map.fitBounds(boundsTarget.getBounds().pad(0.2), {{ maxZoom: 16 }});
      }}
      if (!device) {{
        summary.textContent =
          "Select a device to view samples · " +
          samples.length.toLocaleString() + " in " + dbName + " · " +
          allDevices.length.toLocaleString() + " device(s)";
        tbody.innerHTML =
          "<tr class=\\"empty\\"><td colspan=\\"8\\" class=\\"muted\\">" +
          "Select a device to view samples.</td></tr>";
        return;
      }}
      const kindLabel = KIND_OPTIONS.find((opt) => opt[0] === selectedKind());
      summary.textContent =
        visible.length.toLocaleString() + " event(s) shown · " +
        samples.length.toLocaleString() + " in " + dbName + " · " +
        device + " · " +
        (daySelect.value === "all" ? "all days" : daySelect.value) + " · " +
        (kindLabel ? kindLabel[1] : "All events");
      tbody.innerHTML = visible.slice().reverse().map((row, displayIndex) => {{
        const index = visible.length - 1 - displayIndex;
        const style = styleFor(row);
        const fenceBits = [];
        if (row.fence_name || row.fence_id) fenceBits.push(row.fence_name || row.fence_id);
        const dist = num(row.distance_from_center_m);
        if (dist !== null) fenceBits.push(dist.toFixed(0) + " m");
        return "<tr data-index=\\"" + index + "\\">" +
        "<td>" + (displayIndex + 1) + "</td>" +
        "<td>" + esc(row.sampled_at) + "</td>" +
        "<td><span class=\\"event-dot\\" style=\\"background:" + style.fill +
          ";border-color:" + style.stroke + "\\"></span>" +
          esc(eventLabel(row)) + "</td>" +
        "<td class=\\"muted\\">" + row.lat.toFixed(6) + ", " + row.lon.toFixed(6) + "</td>" +
        "<td>" + fmtAccuracy(row.accuracy_m) + "</td>" +
        "<td>" + fmtSpeed(row.speed_mps) + "</td>" +
        "<td>" + esc(row.provider || row.requested_provider || "") +
          (fmtMock(row.is_mock) === "mock" ? " · mock" : "") + "</td>" +
        "<td class=\\"muted\\">" + esc(fenceBits.join(" · ")) + "</td>" +
        "</tr>";
      }}).join("");
    }}

    function fillKindSelect() {{
      kindSelect.innerHTML = "";
      for (const [value, label] of KIND_OPTIONS) {{
        const option = document.createElement("option");
        option.value = value;
        option.textContent = label;
        kindSelect.appendChild(option);
      }}
      if (KIND_OPTIONS.some((opt) => opt[0] === requestedKind)) {{
        kindSelect.value = requestedKind;
      }}
    }}

    function fillLegend() {{
      const el = document.getElementById("legend");
      el.innerHTML = LEGEND.map(([event, label]) => {{
        const style = EVENT_STYLE[event] || DEFAULT_STYLE;
        return "<span><i class=\\"swatch\\" style=\\"background:" + style.fill +
          ";border-color:" + style.stroke + "\\"></i>" + esc(label) + "</span>";
      }}).join("");
    }}

    function refreshDayOptions() {{
      const device = selectedDevice();
      const days = daysForDevice(device);
      const preferred = preferredDay();
      const values = days.slice();
      if (preferred !== "all" && preferred && !values.includes(preferred)) {{
        values.push(preferred);
        values.sort().reverse();
      }}
      fillSelect(daySelect, values, "All days", preferred);
      daySelect.disabled = !device;
    }}

    fillDeviceSelect();
    fillKindSelect();
    fillLegend();
    refreshDayOptions();
    deviceSelect.addEventListener("change", reloadWithSelection);
    daySelect.addEventListener("change", reloadWithSelection);
    kindSelect.addEventListener("change", reloadWithSelection);
    tbody.addEventListener("click", (event) => {{
      const row = event.target.closest("tr");
      if (!row) return;
      const marker = markerByIndex[Number(row.dataset.index)];
      if (!marker) return;
      map.setView(marker.getLatLng(), Math.max(map.getZoom(), 16));
      marker.openPopup();
    }});
    render();
  </script>
</body>
</html>
"""


def main() -> int:
    args = parse_args()
    if not args.html_only and not args.logfiles:
        print("Pass at least one logfile, or use --html-only with --db.", file=sys.stderr)
        return 1
    if args.html_only and args.logfiles:
        print("Warning: --html-only ignores logfile arguments.", file=sys.stderr)

    for path in args.logfiles:
        if not path.exists():
            print(f"Log file not found: {path}", file=sys.stderr)
            return 1

    db_path = args.db
    output = args.output or db_path.with_name("gpsemulator_map.html")
    conn = connect_db(db_path)
    try:
        if not args.html_only:
            total_found = 0
            total_inserted = 0
            total_skipped = 0
            for path in args.logfiles:
                print(f"Reading {path}…")
                rows = extract_samples(path)
                total_found += len(rows)
                inserted, skipped = ingest(conn, rows)
                total_inserted += inserted
                total_skipped += skipped
                print(
                    f"  {len(rows):,} emulator hit(s); "
                    f"{inserted:,} new, {skipped:,} already in the database."
                )
            print(
                f"Ingested {total_found:,} parsed sample(s) "
                f"({total_inserted:,} inserted, {total_skipped:,} duplicates)."
            )

        refresh_display_times(conn)
        samples = load_samples(conn)
        if not samples:
            print("No GPS Debug Tool samples in the database.", file=sys.stderr)
            return 1
        html_doc = render_html(samples, db_path)
        output.write_text(html_doc, encoding="utf-8")
        print(f"Wrote {output.resolve()} ({len(samples):,} sample(s) from {db_path.resolve()})")
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
