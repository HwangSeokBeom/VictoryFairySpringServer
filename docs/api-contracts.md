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
- `POST /api/v1/diary/template-draft`
- `POST /api/v1/ticket/parse-ocr-text`

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

Scraped development data keeps the internal source value:

```json
{
  "source": "scraped-dev",
  "sourceLabel": "개발용 외부 수집 데이터",
  "sourceDisclosure": null
}
```

When `KBO_SOURCE_LABEL_MODE=review` or `production`, app-visible wording changes to:

```json
{
  "source": "scraped-dev",
  "sourceLabel": "참고용 경기 정보",
  "sourceDisclosure": "이 정보는 기록 입력을 돕기 위한 참고용 정보이며, 공식 기록은 KBO 공식 사이트에서 확인해 주세요."
}
```

Do not label `scraped-dev` rows as licensed/provider data. `officialLinks` is an object and remains empty when no KBO link was imported.

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
    "sourceDisclosure": null,
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
    "sourceDisclosure": null,
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

Groq is called from the Spring server only. The iOS app must never contain `GROQ_API_KEY` and must never call Groq directly. If any key was exposed in chat, local notes, logs, or screenshots, rotate it before enabling this feature.

The app should send minimized text data only. Do not send original photos, precise location, companion real names, or excessive raw notes by default.

Request:

```bash
curl -X POST http://localhost:8081/api/v1/ai/diary-draft \
  -H "Content-Type: application/json" \
  -H "X-Device-ID: 00000000-0000-4000-8000-000000000001" \
  -d '{
    "gameDate": "2026-04-16",
    "favoriteTeamName": "한화 이글스",
    "opponentTeamName": "삼성 라이온즈",
    "stadiumName": "대전 한화생명 볼파크",
    "result": "loss",
    "scoreText": "1:6 패",
    "moodTags": ["아쉬움", "열광적"],
    "highlightTags": ["응원 분위기"],
    "companionType": "friends",
    "tone": "warm",
    "extraNoteSanitized": "응원 분위기가 기억에 남았다.",
    "locale": "ko-KR"
  }'
```

Success:

```json
{
  "success": true,
  "data": {
    "draftText": "...",
    "summaryText": "...",
    "shareText": "...",
    "hashtags": ["#승리요정", "#KBO직관", "#대전한화생명볼파크"],
    "model": "llama-3.1-8b-instant",
    "source": "groq",
    "safetyNotice": "AI 초안은 저장 전 사용자가 직접 확인해 주세요."
  }
}
```

The AI response is schema-validated server-side. Provider failures return a normal failure envelope with `fallbackAvailable=true`; invalid model JSON returns `AI_DRAFT_INVALID_RESPONSE`. The local/dev in-memory rate limit is keyed by `X-Device-ID` or IP and returns `AI_DAILY_LIMIT_EXCEEDED`.

With the default local configuration:

```json
{
  "success": false,
  "error": {
    "code": "AI_FEATURE_DISABLED",
    "message": "AI 후기 초안 기능은 비활성화되어 있습니다."
  }
}
```

If `AI_DIARY_ENABLED=true` but no server key is configured:

```json
{
  "success": false,
  "error": {
    "code": "AI_CONFIG_MISSING",
    "message": "AI 설정이 완료되지 않았습니다."
  }
}
```

## Template Diary Draft

Deterministic fallback endpoint:

```bash
curl -X POST http://localhost:8081/api/v1/diary/template-draft \
  -H "Content-Type: application/json" \
  -d '{
    "gameDate": "2026-04-16",
    "favoriteTeamName": "한화 이글스",
    "opponentTeamName": "삼성 라이온즈",
    "stadiumName": "대전 한화생명 볼파크",
    "result": "loss",
    "scoreText": "1:6 패",
    "moodTags": ["아쉬움"],
    "highlightTags": ["응원 분위기"],
    "tone": "warm",
    "extraNote": "응원 분위기가 기억에 남았다."
  }'
```

It returns the same draft fields with `"source": "template"` and does not call an LLM.

## Ticket OCR Text Parse

The server never receives ticket images. The iOS app runs on-device OCR first, then sends recognized text only. There is no external OCR provider and no image storage.

```bash
curl -X POST http://localhost:8081/api/v1/ticket/parse-ocr-text \
  -H "Content-Type: application/json" \
  -d '{
    "ocrText": "2026.04.16\n한화 이글스 vs 삼성 라이온즈\n대전 한화생명 볼파크\n1루 204블록 12열 8번",
    "locale": "ko-KR"
  }'
```

Response:

```json
{
  "success": true,
  "data": {
    "candidates": [
      {
        "confidence": 0.94,
        "date": "2026-04-16",
        "homeTeamID": "hanwha-eagles",
        "awayTeamID": "samsung-lions",
        "favoriteTeamID": null,
        "opponentTeamID": null,
        "stadiumName": "대전 한화생명 볼파크",
        "seatText": "1루 204블록 12열 8번",
        "rawMatchedText": "...",
        "warnings": []
      }
    ],
    "message": "티켓에서 추정한 정보예요. 저장 전 꼭 확인해 주세요."
  }
}
```

Warnings can include `DATE_NOT_FOUND`, `TEAM_AMBIGUOUS`, `TEAM_ORDER_UNCERTAIN`, `STADIUM_NOT_FOUND`, and `SEAT_LOW_CONFIDENCE`. Candidates are suggestions only; the app must ask the user to confirm before saving.
