# Local Development

## Requirements

- JDK 17
- Kotlin/Spring Boot build via the included Gradle wrapper
- Playwright browser runtime for the internal KBO scraped-dev collector

The server defaults to port `8081`. The separate `kbo-scraper` server is no longer required for normal local KBO updates.

Install Playwright browsers once if the collector cannot launch Chromium:

```bash
./gradlew installPlaywrightChromium
```

Most local runs only need `./gradlew bootRun`; run the install task when the first collector request reports a missing browser.

## Run In Terminal

```bash
cd /Users/hwangseokbeom/Documents/GitHub/VictoryFairySpringServer
./gradlew bootRun
```

Health check:

```bash
curl http://localhost:8081/health
```

H2 console:

```text
http://localhost:8081/h2-console
JDBC URL: jdbc:h2:file:./data/victoryfairy-dev;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
User: sa
Password:
```

## Run In IntelliJ

1. Open `/Users/hwangseokbeom/Documents/GitHub/VictoryFairySpringServer`.
2. Import as a Gradle project.
3. Use JDK 17.
4. Run `com.victoryfairy.server.VictoryFairyApplication`.
5. Confirm `http://localhost:8081/health`.

## iOS Base URL

For local simulator development, point the iOS app base URL from the Node server to:

```text
http://localhost:8081
```

No iOS code changes were made in this task. The intent is a minimal baseURL switch while preserving the Node-style response envelope and app-facing DTO fields where practical.

## Seed And Verify Sample Game

```bash
curl -X POST http://localhost:8081/api/v1/dev/kbo/seed-sample-game
```

Hanwha perspective:

```bash
curl "http://localhost:8081/api/v1/kbo/games?date=2026-04-16&teamID=hanwha-eagles"
```

Expected key values:

- `source=scraped-dev`
- `sourceLabel=개발용 외부 수집 데이터`
- `matchupText=한화 vs 삼성`
- `scoreText=1:6 패`
- `shortMemo=삼성이 6:1로 승리했던 경기`

Samsung perspective:

```bash
curl "http://localhost:8081/api/v1/kbo/games?date=2026-04-16&teamID=samsung-lions"
```

Expected key values:

- `matchupText=삼성 vs 한화`
- `scoreText=6:1 승`

## Collect KBO scraped-dev Data Internally

Default local collection no longer reads the external JSON file and does not require the old `kbo-scraper` server:

```bash
curl -X POST http://localhost:8081/api/v1/dev/kbo/collect-scraped-dev \
  -H "Content-Type: application/json" \
  -d '{"season":2026,"seriesType":"REGULAR_SEASON"}'
```

The main update endpoint also defaults to the internal collector:

```bash
curl -X POST http://localhost:8081/api/v1/dev/kbo/update-scraped-dev \
  -H "Content-Type: application/json" \
  -d '{"mode":"internal-collector"}'
```

Verify:

```bash
curl "http://localhost:8081/api/v1/kbo/games?date=2026-04-16&teamID=hanwha-eagles"
```

Expected key values remain:

- `source=scraped-dev`
- `sourceLabel=개발용 외부 수집 데이터`
- `attendanceSuggestion` is from the requested `teamID` perspective

These endpoints are local/test only. They are blocked for production Spring profiles and `NODE_ENV=production`. If `ADMIN_IMPORT_TOKEN` is set, include:

```bash
-H "X-Admin-Token: $ADMIN_IMPORT_TOKEN"
```

## Fallback KBO scraped-dev JSON Import

Set the local source file:

```bash
export KBO_SCRAPED_DEV_INPUT_JSON=/Users/hwangseokbeom/Documents/GitHub/VictoryFairyCoreServer/input/kbo-scraper-2026.json
```

Run the fallback import endpoint:

```bash
curl -X POST http://localhost:8081/api/v1/dev/kbo/import-scraped-dev-json
```

Or use fallback mode through the update endpoint:

```bash
curl -X POST http://localhost:8081/api/v1/dev/kbo/update-scraped-dev \
  -H "Content-Type: application/json" \
  -d '{"mode":"json-import"}'
```

Check status:

```bash
curl http://localhost:8081/api/v1/dev/kbo/update-scraped-dev/status
```

JSON import is fallback only. The normal local update path is the internal collector.

## Device-Owned APIs

Use `X-Device-ID` for preferences, attendance logs, feed, calendar, and statistics:

```bash
curl -H "X-Device-ID: 00000000-0000-4000-8000-000000000001" \
  "http://localhost:8081/api/v1/statistics/summary?season=2026"
```

## Tests

```bash
./gradlew test
```
