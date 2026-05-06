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
- `GET /api/v1/analysis/win-rate?season=2026`
- `GET /api/v1/news?teamID=samsung-lions&limit=20`
- `POST /api/v1/match-outlook`
- `GET /api/v1/community/posts`
- `POST /api/v1/community/posts`
- `POST /api/v1/ai/diary-draft`
- `POST /api/v1/diary/template-draft`
- `POST /api/v1/ticket/parse-ocr-text`

Device-owned endpoints require:

```http
X-Device-ID: <uuid-or-stable-device-id>
```

Read-only seed/reference endpoints such as `/health`, `/api/v1/teams`, KBO games, news scaffold, community read scaffold, and dev KBO update/status do not require device identity.

Personalized endpoints that read attendance history, including win-rate analysis and match outlook, require `X-Device-ID`.

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

## Win-Rate Analysis

`GET /api/v1/analysis/win-rate?season=2026` uses the requesting device's attendance logs only. It does not use league-wide standings.

Draws and canceled games are counted in totals. The `winRate` denominator is wins plus losses only. If wins plus losses is below 3, the response includes a small-sample warning.

```bash
curl "http://localhost:8081/api/v1/analysis/win-rate?season=2026" \
  -H "X-Device-ID: 00000000-0000-4000-8000-000000000001"
```

```json
{
  "success": true,
  "data": {
    "season": 2026,
    "summary": {
      "totalGames": 1,
      "wins": 0,
      "losses": 1,
      "draws": 0,
      "canceled": 0,
      "winRate": 0.0,
      "sampleWarning": "아직 표본이 적어 재미용으로만 봐주세요."
    },
    "opponentRankings": [
      {
        "teamID": "kia-tigers",
        "teamName": "KIA 타이거즈",
        "games": 1,
        "wins": 0,
        "losses": 1,
        "draws": 0,
        "winRate": 0.0
      }
    ],
    "stadiumRankings": [
      {
        "stadiumName": "잠실야구장",
        "games": 1,
        "wins": 0,
        "losses": 1,
        "draws": 0,
        "winRate": 0.0
      }
    ],
    "recentTrend": ["L"],
    "insights": [
      {
        "title": "최근 흐름",
        "body": "최근 직관은 패배였어요. 다음 기록에서 흐름을 바꿔봐요."
      }
    ]
  }
}
```

Opponent and stadium rankings sort by `winRate` descending, then `games` descending, then name ascending.

## News

`GET /api/v1/news?teamID=samsung-lions&limit=20` returns link-out baseball news cards. With `NEWS_PROVIDER=naver`, the server calls Naver Search News API using server-only environment variables. Do not put Naver credentials in iOS, source code, docs, logs, responses, errors, tests, or screenshots.

The API returns title, summary, date, source, link, and team IDs only. It does not return full article bodies, scrape article pages, expose provider debug fields, or claim official KBO data, official news, real-time broadcast, or any partnership. Full articles open externally.

```bash
curl "http://localhost:8081/api/v1/news?teamID=samsung-lions&limit=20"
```

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "naver-...",
        "title": "삼성 라이온즈 뉴스 제목",
        "summary": "뉴스 요약",
        "sourceName": "네이버 뉴스",
        "publishedAt": "2026-05-06T12:00:00+09:00",
        "url": "https://example.com/article",
        "teamIDs": ["samsung-lions"]
      }
    ],
    "message": null,
    "sourceDisclosure": "뉴스는 외부 매체로 이동해 확인해 주세요."
  }
}
```

Supported team query mapping:

- `samsung-lions` -> `삼성 라이온즈 야구`
- `kia-tigers` -> `KIA 타이거즈 야구`
- `hanwha-eagles` -> `한화 이글스 야구`
- `lg-twins` -> `LG 트윈스 야구`
- `doosan-bears` -> `두산 베어스 야구`
- `lotte-giants` -> `롯데 자이언츠 야구`
- `ssg-landers` -> `SSG 랜더스 야구`
- `kt-wiz` -> `KT 위즈 야구`
- `nc-dinos` -> `NC 다이노스 야구`
- `kiwoom-heroes` -> `키움 히어로즈 야구`

Missing or unknown `teamID` uses `KBO 야구`. `limit` defaults to 20 and is capped at 20.

Server environment:

```bash
NEWS_PROVIDER=naver
NAVER_CLIENT_ID=replace-with-naver-client-id
NAVER_CLIENT_SECRET=replace-with-naver-client-secret
NAVER_NEWS_BASE_URL=https://openapi.naver.com/v1/search/news.json
NEWS_CACHE_TTL_SECONDS=1800
```

If `NEWS_PROVIDER` is not `naver`, the server uses the local/dev sample provider. If Naver credentials are missing in local/dev, the server returns local/dev sample news. If Naver credentials are missing in production, the server returns `success=true`, `items=[]`, and `message="뉴스 제공 설정이 준비되지 않았습니다."`. If a Naver secret was exposed anywhere, rotate it before production use.

## Match Outlook

`POST /api/v1/match-outlook` returns safe `관전 포인트`/`경기 전망` style text. It is informational and entertainment-only, derived from the requester's attendance logs and optional local reference context.

```bash
curl -X POST http://localhost:8081/api/v1/match-outlook \
  -H "Content-Type: application/json" \
  -H "X-Device-ID: 00000000-0000-4000-8000-000000000001" \
  -d '{
    "favoriteTeamID": "samsung-lions",
    "opponentTeamID": "kia-tigers",
    "date": "2026-04-12",
    "stadiumName": "잠실야구장"
  }'
```

```json
{
  "success": true,
  "data": {
    "title": "오늘의 관전 포인트",
    "summary": "내 직관 기록과 참고용 경기 정보를 바탕으로 본 응원 포인트예요.",
    "points": [
      "최근 직관 기록 기준으로는 KIA전 표본이 아직 적어요.",
      "잠실야구장 기록을 더 쌓으면 구장별 흐름을 더 잘 볼 수 있어요."
    ],
    "confidenceLabel": "재미용",
    "disclaimer": "공식 예측이나 베팅 정보가 아닙니다."
  }
}
```

Do not expose guarantees, outcome-hit claims, or 금전성 승부 정보. User-facing Korean should prefer `경기 전망`, `관전 포인트`, and `응원 포인트`.

## Community Scaffold

`GET /api/v1/community/posts` is public and returns an empty state until community moderation and operations are ready.

```bash
curl http://localhost:8081/api/v1/community/posts
```

```json
{
  "success": true,
  "data": {
    "items": [],
    "message": "응원톡은 준비 중입니다.",
    "policyURL": "https://hwangseokbeom.github.io/VictoryFairy-legal/community-policy.html"
  }
}
```

`POST /api/v1/community/posts` exists but is disabled by default:

```bash
curl -X POST http://localhost:8081/api/v1/community/posts \
  -H "Content-Type: application/json" \
  -d '{"content":"삼성 화이팅"}'
```

```json
{
  "success": false,
  "error": {
    "code": "COMMUNITY_DISABLED",
    "message": "응원톡 작성은 아직 준비 중입니다."
  }
}
```

Set `COMMUNITY_ENABLED=true` only after moderation operations are ready. The scaffold enforces text-only posts, 300-character maximum, a basic prohibited-content guard, pending-review status, and report availability.

## Share Card Support

Share card rendering remains client-side. Attendance detail and feed DTOs include server-side data needed by the client:

- `date`, `gameDate`, `matchupText`, `scoreText`, `result`
- `stadiumName`, `shortMemo`, `diaryText`
- `favoriteTeamID`, `favoriteTeamName`, `opponentTeamID`, `opponentTeamName`
- `sourceLabel`, `sourceDisclosure` when the record is linked to reference context
- `photoLocalRefs` and `photoMetadata` placeholders; the server does not render or store card images

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
