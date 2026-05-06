# VictoryFairy Spring API Contracts

All responses use the Node-compatible envelope.

Success:

```json
{
  "success": true,
  "data": {}
}
```

Failure:

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "입력값을 확인해 주세요."
  }
}
```

## Endpoints

- `GET /health`
- `GET /api/v1/teams`
- `GET /api/v1/me/preferences`
- `PUT /api/v1/me/preferences`
- `GET /api/v1/kbo/games?date=YYYY-MM-DD&teamID=<teamID>`
- `GET /api/v1/kbo/standings?season=2026`
- `POST /api/v1/dev/kbo/seed-sample-game`
- `POST /api/v1/dev/kbo/collect-scraped-dev`
- `POST /api/v1/dev/kbo/update-scraped-dev`
- `POST /api/v1/dev/kbo/import-scraped-dev-json`
- `GET /api/v1/dev/kbo/update-scraped-dev/status`
- `POST /api/v1/attendance-logs`
- `GET /api/v1/attendance-logs`
- `GET /api/v1/attendance-logs/{id}`
- `PUT /api/v1/attendance-logs/{id}`
- `DELETE /api/v1/attendance-logs/{id}`
- `GET /api/v1/feed?season=2026&result=win|loss|draw|canceled`
- `GET /api/v1/calendar?year=2026&month=4`
- `GET /api/v1/statistics/summary?season=2026`
- `GET /api/v1/statistics/stadiums?season=2026`
- `GET /api/v1/statistics/opponents?season=2026`
- `POST /api/v1/ai/diary-draft`

Device-owned endpoints require:

```http
X-Device-ID: <uuid-or-stable-device-id>
```

Read-only seed/reference endpoints such as `/health`, `/api/v1/teams`, KBO games, and dev KBO update/status do not require device identity.

Dev KBO collector/update endpoints are local/test only. They are blocked for production Spring profiles and `NODE_ENV=production`. If `ADMIN_IMPORT_TOKEN` is configured, these endpoints require:

```http
X-Admin-Token: <local-secret>
```

## KBO Games Shape

`GET /api/v1/kbo/games?date=2026-04-16&teamID=hanwha-eagles` returns `attendanceSuggestion` from the requested `teamID` perspective. The same game queried with `teamID=samsung-lions` returns Samsung as the favorite-team perspective.

Scraped development data always returns:

```json
{
  "source": "scraped-dev",
  "sourceLabel": "개발용 외부 수집 데이터"
}
```

`officialLinks` is an object and remains empty when no KBO link was imported.

## KBO Standings Shape

`GET /api/v1/kbo/standings?season=2026` is computed from locally stored `kbo_games` rows collected through the scraped-dev pipeline. It does not call KBO, Naver, Daum, or any external source while serving the request.

Only rows with the requested `season`, `status=final`, and non-null `homeScore`/`awayScore` are included. Draws count in `games` and `draws`, but are excluded from the `winRate` denominator.

```json
{
  "success": true,
  "data": {
    "season": 2026,
    "source": "scraped-dev",
    "sourceLabel": "개발용 외부 수집 데이터",
    "updatedAt": "2026-05-06T20:58:09.000+09:00",
    "items": [
      {
        "rank": 1,
        "teamID": "samsung-lions",
        "teamName": "삼성 라이온즈",
        "shortName": "삼성",
        "games": 1,
        "wins": 1,
        "losses": 0,
        "draws": 0,
        "winRate": 1.0,
        "runsFor": 6,
        "runsAgainst": 1,
        "runDifferential": 5,
        "recentResults": ["W"]
      }
    ],
    "message": null
  }
}
```

For scraped-dev standings, `updatedAt` is based on the latest stored `KBOGame` update timestamp from final rows included in the standings, not an official publication timestamp. If no final rows exist, `updatedAt` falls back to the latest stored `KBOGame` update timestamp for the requested season when any season rows exist; otherwise it is `null`.

When no final scraped-dev game rows exist, the endpoint stays in scraped-dev mode and returns a normal local/dev empty state:

```json
{
  "success": true,
  "data": {
    "season": 2026,
    "source": "scraped-dev",
    "sourceLabel": "개발용 외부 수집 데이터",
    "updatedAt": null,
    "items": [],
    "message": "수집된 경기 결과가 아직 없습니다."
  }
}
```

## KBO scraped-dev Collection

Default local collection:

```bash
curl -X POST http://localhost:8081/api/v1/dev/kbo/collect-scraped-dev \
  -H "Content-Type: application/json" \
  -d '{"season":2026,"seriesType":"REGULAR_SEASON"}'
```

Main update endpoint defaults to the internal collector:

```json
{
  "mode": "internal-collector"
}
```

JSON import is fallback only:

```json
{
  "mode": "json-import"
}
```

Collection/update summary shape:

```json
{
  "collectedCount": 1,
  "inserted": 1,
  "updated": 0,
  "skipped": 0,
  "warnings": [],
  "statusCounts": {
    "final": 1,
    "scheduled": 0,
    "canceled": 0,
    "postponed": 0
  }
}
```

## AI Diary Draft

The AI diary endpoint is feature-flagged. With the default local configuration:

```json
{
  "success": false,
  "error": {
    "code": "AI_FEATURE_DISABLED",
    "message": "AI 후기 초안 기능은 아직 비활성화되어 있습니다."
  }
}
```

If enabled later, `GROQ_API_KEY` must remain server-side only. The iOS app must not call Groq directly.
