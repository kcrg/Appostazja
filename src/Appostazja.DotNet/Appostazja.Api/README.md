# ApostasyMap.Api

Small .NET 11 Minimal API that turns the GeoJSON from `https://mapaapostazji.pl/data/map-data.json` into a mobile-friendly REST API backed by SQLite and an in-memory read cache.

## What it does

- Targets `net11.0` and has NativeAOT enabled by default.
- Keeps `OptimizationPreference=Speed` while enabling safe size-oriented feature switches.
- Uses `CreateSlimBuilder` and source-generated `System.Text.Json` metadata.
- Stores a flattened representation in SQLite as the persistent cache.
- Warms an immutable in-memory snapshot from SQLite at startup; normal API reads do not hit SQLite.
- On first boot, tries to populate an empty database before opening the HTTP listener; a source failure is non-fatal.
- Schedules a normal refresh every day at 03:15 in `Europe/Warsaw`.
- Treats data as fresh for 20 hours by default.
- Uses upstream `ETag` / `Last-Modified` validators for normal refreshes.
- Computes SHA-256 of the downloaded JSON and rewrites SQLite only when the payload changed.
- Replaces the whole SQLite dataset in one transaction and then atomically swaps the in-memory snapshot.
- Exposes dataset `ETag` / `Last-Modified` to clients so unchanged responses can become `304 Not Modified`.
- Supports optional bounding-box filtering for map viewports.

## Force refresh protection

`GET /api/v1/places?forceRefresh=true` bypasses the local freshness check and performs a full source download before returning places.

There is no API key and no separate admin endpoint. To stay gentle to `mapaapostazji.pl`, forced refreshes are protected globally:

- one `SemaphoreSlim` serializes all refresh work,
- forced refresh has a configurable global cooldown (6 hours by default),
- the cooldown is also calculated from the last successful source check persisted in SQLite, so a restart does not immediately enable another forced download,
- a failed forced attempt is remembered in-process and also starts the cooldown,
- a throttled force request simply returns current cached data instead of touching the upstream,
- `forceRefresh=true` is accepted only for the whole-dataset request, not viewport/bounding-box requests.

The response includes `X-Refresh-Outcome` when `forceRefresh=true`. A throttled request also includes `X-Force-Refresh-Allowed-At`.

## API

### `GET /api/v1/places`

Returns all places from the in-memory snapshot.

Optional force refresh:

```text
GET /api/v1/places?forceRefresh=true
```

Optional viewport query:

```text
GET /api/v1/places?minLat=49.8&maxLat=50.2&minLon=21.8&maxLon=22.2
```

Example item:

```json
{
  "id": "swietego-jozefa_rzeszow-2",
  "name": "Kościół pw. Świętego Józefa",
  "address": "...",
  "latitude": 50.0524769,
  "longitude": 22.0039286,
  "score": 1.0,
  "ratings": {
    "positive": 2,
    "neutral": 0,
    "negative": 0,
    "total": 2
  }
}
```

### `GET /api/v1/places/{id}`

Returns a single place from memory.

### `GET /api/v1/dataset`

Returns cache/source metadata, current SHA-256 version and freshness state.

## Configuration

Useful environment overrides:

```text
Database__Path
Source__FreshForHours
Source__ForceRefreshCooldownMinutes
Source__NightlyLocalHour
Source__NightlyLocalMinute
Source__TimeZoneId
Source__RequestTimeoutSeconds
```

Default forced-refresh cooldown is 360 minutes. The accepted range is 5 minutes to 7 days.

## Run

```bash
dotnet restore
dotnet run
```

NativeAOT publish:

```bash
dotnet publish -c Release -r linux-x64 --self-contained true
```

Example:

```bash
ASPNETCORE_URLS=http://127.0.0.1:5000 \
Database__Path='/var/lib/apostasy-map/apostasy-map.db' \
./bin/Release/net11.0/linux-x64/publish/Appostazja.Api
```

SQLite is bundled through `SQLite3MC.PCLRaw.bundle`, so the published app does not depend on the host SQLite version.
