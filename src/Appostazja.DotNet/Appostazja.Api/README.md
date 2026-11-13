# ApostasyMap.Api

Small .NET 11 Minimal API that publishes reports and parish locations from [Mapa Apostazji](https://mapaapostazji.pl/).

- [Responses CSV](https://mapaapostazji.pl/data/responses.csv) supplies stages, difficulty ratings, optional dates and comments, and parish websites.
- [GeoJSON](https://mapaapostazji.pl/data/map-data.json) supplies coordinates and stable IDs. The importer joins on normalized parish name and address. For multiple pins at the same address, it keeps the most reported pin and resolves the other IDs as aliases.
- Ratings count all three stages. `score` is `(easy - hard) / total`, or zero when there are no reports. CSV rows without a corresponding coordinate are counted in a warning and can join on a later refresh.
- Public report responses expose stage, rating, date and comment.

## Privacy

The upstream CSV also contains submission timestamps and demographic columns (`timestamp`, `person_*`). Right after download the importer keeps only the seven columns it publishes, so only that sanitized CSV is stored in SQLite and used for the version hash. E-mail addresses and phone-like numbers (Polish 9-digit numbers in the usual groupings, optionally with `+48`, and 11-digit PESEL) in comments and in the website field are replaced with `[e-mail ukryty]` / `[numer ukryty]`. Names written in free text cannot be detected reliably and are **not** masked.

When it starts, the API rewrites an older database that still holds the full CSV, vacuums it and marks the data stale so the next refresh rebuilds the published reports.

## Import robustness

Structural problems (missing column, invalid UTF-8, a payload that is not a FeatureCollection, a source larger than `Source:MaxResponseMegabytes`) fail the import. Individual bad CSV rows (unknown rating/stage, wrong field count, missing name) and bad GeoJSON features (invalid coordinates, duplicate id) are skipped and counted.

An import is rejected and the previous dataset keeps being served when:

- more than `Import:MaxSkippedRowPercent` (default 5%) of all rows and features were skipped, or
- the place count drops by more than `Import:MaxPlaceDropPercent` (default 30%).

Every attempt that reaches the sources is recorded and shown in `GET /api/v1/dataset` under `lastImport`: `lastAttemptAtUtc`, `outcome` (`updated`, `unchanged`, `not-modified`, `failed`, `invalid-data`), `error`, `skippedRows` and `unmatchedReports`.

## Health

- `GET /health` and `GET /health/live`: the process is answering.
- `GET /health/ready`: `200` when data exists and the sources were checked successfully within `Import:ReadyMaxAgeHours` (default 48 h), otherwise `503` with a reason that includes the last import error.

Use `/health/ready` for monitoring and alerting, not for taking the instance out of a load balancer. Stale data is still better than no data.

## OpenAPI

The OpenAPI document is served at `GET /openapi/v1.json`.

## Tests

```bash
dotnet test ../Appostazja.slnx
```

The tests cover the parser, sanitizing, import validation and status recording (temporary SQLite and a fake HTTP source), database upgrades, HTTP caching rules and readiness.

## What it does

- Targets `net11.0` and has NativeAOT enabled by default.
- Keeps `OptimizationPreference=Speed` while enabling safe size-oriented feature switches.
- Uses `CreateSlimBuilder` and source-generated `System.Text.Json` metadata.
- Stores a flattened representation in SQLite as the persistent cache.
- Warms an immutable in-memory snapshot from SQLite at startup; normal API reads do not hit SQLite.
- Starts serving the last good snapshot from SQLite immediately. On an empty database, map reads return `503` until the background worker finishes the first import.
- Schedules a normal refresh every day at 03:15 in `Europe/Warsaw`.
- Treats data as fresh for 20 hours by default.
- Sends conditional requests for both inputs and retains the cached bytes when a source returns `304`. Failed scheduled checks retry after an hour.
- Uses a SHA-256 version based on both inputs and the import format. An unchanged version updates only cache metadata.
- Replaces the whole SQLite dataset in one transaction and then atomically swaps the in-memory snapshot.
- Exposes dataset `ETag` / `Last-Modified` to clients so unchanged responses can become `304 Not Modified`.
- Supports optional bounding-box filtering for map viewports.

## Force refresh protection

`GET /api/v1/places?forceRefresh=true` bypasses the local freshness check and conditionally revalidates both feeds before returning places.

There is no API key and no separate admin endpoint. To stay gentle to `mapaapostazji.pl`, forced refreshes are protected globally:

- one `SemaphoreSlim` serializes all refresh work,
- forced refresh has a configurable global cooldown (6 hours by default),
- the cooldown uses the last successful source check persisted in SQLite and the last in-process attempt, including failures,
- a throttled force request simply returns current cached data instead of touching the upstream,
- `forceRefresh=true` is accepted only for the whole-dataset request, not viewport/bounding-box requests.

The response includes `X-Refresh-Outcome` when `forceRefresh=true`. A throttled request also includes `X-Force-Refresh-Allowed-At`.

## API

### `GET /api/v1/places`

Returns all places from the in-memory snapshot.
The unfiltered response is also available as precompressed gzip via `Accept-Encoding: gzip`.

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

Returns a single place from memory. An old pin ID resolves to its main place. Unknown IDs return `404` with an `ApiError` body.

### `GET /api/v1/places/{id}/reports`

Returns the optional parish website and its reports from the same snapshot. Each report has a stage (`before_apostasy`, `apostasy`, `after_apostasy`), a rating (`easy`, `medium`, `hard`), an optional ISO date and optional comment. Reports are ordered newest submission first.

### `GET /api/v1/dataset`

Returns cache/source metadata, current SHA-256 version and freshness state.

## Configuration

Useful environment overrides:

```text
Database__Path
Source__Url
Source__CoordinatesUrl
Source__FreshForHours
Source__ForceRefreshCooldownMinutes
Source__NightlyLocalHour
Source__NightlyLocalMinute
Source__TimeZoneId
Source__RequestTimeoutSeconds
Source__MaxResponseMegabytes
Import__MaxSkippedRowPercent
Import__MaxPlaceDropPercent
Import__ReadyMaxAgeHours
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
