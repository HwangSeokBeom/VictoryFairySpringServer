# KBO Data Policy

This server includes a local-only KBO `scraped-dev` pipeline for development and testing.

Required labels:

- `source`: `scraped-dev`
- `sourceLabel`: `개발용 외부 수집 데이터`

This data must not be presented as official KBO data, provider data, licensed data, or App Store/production-ready data.

## Allowed Use

- Local iOS development
- Backend contract testing
- UI state testing for schedules, results, attendance suggestions, statistics, feed, and calendar screens

## Not Allowed Without Further Review

- Production deployment using scraped KBO data
- App Store release claims based on scraped KBO data
- Calling scraped data official, provider-backed, licensed, or complete
- Committing API keys, cookies, credentials, or generated full-season data snapshots

## Internal Collector Behavior

VictoryFairySpringServer now includes an internal KBO scraped-dev collector for normal local updates. The separate `kbo-scraper` server is no longer required for the default development update flow.

The implementation was informed by the local `kbo-scraper` reference repository, but that repository did not include a clear permissive license in the checked files. For that reason, VictoryFairySpringServer reimplements only the minimal ideas needed here instead of copying large code blocks.

The collector:

- uses Playwright to load the KBO schedule page in local development,
- parses schedule/result rows into VictoryFairy team IDs and stadium names,
- stores rows directly in the local `kbo_games` table,
- always writes `source=scraped-dev` and `sourceLabel=개발용 외부 수집 데이터`,
- runs sequential monthly requests with a small delay and must not be used aggressively.

## Standings Behavior

`GET /api/v1/kbo/standings?season=2026` is also a scraped-dev endpoint. It computes standings from rows already stored in the local Spring DB and must not call KBO, Naver, Daum, or any other external service while serving the request.

The response always uses:

```json
{
  "source": "scraped-dev",
  "sourceLabel": "개발용 외부 수집 데이터"
}
```

The calculation includes only local `kbo_games` rows where:

- `season` matches the requested season,
- `status` is `final`,
- `homeScore` and `awayScore` are present.

Scheduled, canceled, postponed, and incomplete-score rows are excluded. Draws are included in `games` and `draws`, but excluded from the `winRate` denominator. If there are no final local rows, the endpoint returns:

```json
{
  "season": 2026,
  "source": "scraped-dev",
  "sourceLabel": "개발용 외부 수집 데이터",
  "updatedAt": null,
  "items": [],
  "message": "수집된 경기 결과가 아직 없습니다."
}
```

For scraped-dev standings, `updatedAt` is based on the latest stored `KBOGame` update timestamp, not an official publication timestamp. With final rows, it is the latest update timestamp among final rows included in the standings. Without final rows, it falls back to the latest stored update timestamp for any row in the requested season; if the season has no stored rows, it is `null`.

Do not describe this response as official current KBO standings. It is local development/test data built from scraped-dev game rows.

The default scheduler remains disabled:

```bash
KBO_SCRAPED_DEV_SCHEDULER_ENABLED=false
```

## JSON Fallback Import

The fallback JSON import still exists for recovery/testing:

```bash
KBO_SCRAPED_DEV_INPUT_JSON=/Users/hwangseokbeom/Documents/GitHub/VictoryFairyCoreServer/input/kbo-scraper-2026.json
```

The JSON root may be a raw array, or a wrapper with an array under `data`, `games`, `items`, `rows`, `matches`, `schedule`, or `results`.

If the file is missing or the wrapper does not contain an array, the API returns a clear failure envelope. The currently checked reference file may contain only an export summary wrapper; regenerate it from the scraper with actual game rows before using the import endpoint.

Unknown stadium names do not crash the import. The original stadium string is preserved, a warning is returned, and valid games are still imported.

Use:

```bash
curl -X POST http://localhost:8081/api/v1/dev/kbo/import-scraped-dev-json
```

or:

```bash
curl -X POST http://localhost:8081/api/v1/dev/kbo/update-scraped-dev \
  -H "Content-Type: application/json" \
  -d '{"mode":"json-import"}'
```

## Scheduler

The scheduler structure is present and disabled by default:

```bash
KBO_SCRAPED_DEV_SCHEDULER_ENABLED=false
KBO_SCRAPED_DEV_SCHEDULE_CRON="0 23 * * *"
KBO_SCRAPED_DEV_SEASON=2026
KBO_SCRAPED_DEV_MIN_INTERVAL_HOURS=20
```

When enabled outside production, the scheduler calls the internal collector, prevents overlapping jobs, and enforces the minimum interval. It records status in:

```bash
data/kbo/kbo_scraped_dev_update_state.json
```

The scheduler refuses to run when a production profile is active.

## Dev Endpoint Safety

Collector/update endpoints are blocked when a `prod` or `production` Spring profile is active, or when `NODE_ENV=production`.

Optional local token protection is supported:

```bash
ADMIN_IMPORT_TOKEN=<local-secret>
```

When configured, call dev collector/update endpoints with:

```http
X-Admin-Token: <local-secret>
```
