# VictoryFairy server: AWS account migration pre-cutover

## Canonical runtime

- Server: Spring Boot/Kotlin on Java 17
- Process manager: systemd service `victoryfairy` (not PM2)
- Internal application port: 8081
- Public transport: Nginx HTTPS on 443; HTTP 80 redirects to HTTPS
- Database: PostgreSQL
- Schema management: Flyway
- Health: `GET /health`
- Dependency readiness: `GET /ready`

The repository does not implement Redis, WebSocket, or FCM. They are therefore
not migration prerequisites for this product.

## Production environment names

Values belong in the new account's secret store or host environment and must
not be committed:

```text
SPRING_PROFILES_ACTIVE
SERVER_PORT
PUBLIC_BASE_URL
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
FLYWAY_BASELINE_ON_MIGRATE
GROQ_API_KEY
GROQ_MODEL
MATCH_OUTLOOK_AI_ENABLED
NEWS_PROVIDER
NAVER_CLIENT_ID
NAVER_CLIENT_SECRET
NAVER_NEWS_BASE_URL
NEWS_CACHE_TTL_SECONDS
KBO_SOURCE_LABEL_MODE
KBO_REFRESH_ENABLED
KBO_REFRESH_CRON
KBO_REFRESH_SEASON
KBO_REFRESH_ADMIN_TOKEN
KBO_REFRESH_TIMEOUT_SECONDS
KBO_REFRESH_LOCK_ENABLED
KBO_SCRAPED_DEV_ENABLED
KBO_SCRAPED_DEV_SCHEDULER_ENABLED
COMMUNITY_ENABLED
COMMUNITY_POSTS_REQUIRE_PROFILE
COMMUNITY_BLOCK_ENABLED
PROFILE_IMAGE_UPLOAD_ENABLED
PROFILE_IMAGE_UPLOAD_DIR
PROFILE_IMAGE_MAX_BYTES
PROFILE_IMAGE_MAX_SIDE
CORS_ALLOWED_ORIGIN_PATTERNS
```

Production must use profile `production`, port 8081, an HTTPS public URL, and a
PostgreSQL JDBC URL. There is no production H2 fallback. Hibernate validates
the Flyway-managed schema rather than updating it.

## Existing database restore safety

`V1__baseline.sql` represents the schema generated from the current JPA model
on a clean PostgreSQL 16 database. It is safe for a new empty database.

Do not enable `FLYWAY_BASELINE_ON_MIGRATE` on a restored database until:

1. a backup has been restored into a disposable database,
2. its schema has been compared with V1,
3. row counts and critical records have been recorded,
4. Hibernate validation succeeds, and
5. rollback by restoring the untouched backup has been rehearsed.

The production default is `false` so an unknown non-empty schema fails closed.
The H2 conversion rehearsal refuses non-loopback PostgreSQL URLs and refuses a
non-empty target. It prints table row counts only, never row contents.

## Local pre-cutover gates

```bash
./gradlew test
./gradlew bootJar
scripts/rehearse_postgres_migration.sh
scripts/rehearse_h2_to_postgres.sh /path/to/recovery-copy.mv.db
scripts/scan_sensitive_patterns.sh
git diff --check
```

Validate an environment without printing its values:

```bash
set -a
source /path/to/private/victoryfairy.env
set +a
scripts/verify_production_contract.sh
```

## New-account staging order

1. Inventory and checksum the old database, uploads, environment-variable
   names, Nginx config, systemd unit, and application artifact.
2. Create the replacement network, instance, storage, least-privilege security
   groups, and secret storage.
3. Restore the backup into an isolated PostgreSQL instance.
4. Run schema comparison and Flyway/Hibernate validation.
5. Start the replacement server on a temporary non-public endpoint.
6. Verify `/health`, `/ready`, API contracts, KBO production mode, and graceful
   shutdown.
7. Configure TLS and verify the temporary endpoint.
8. Freeze writes, take a final backup, restore final data, and re-run checks.
9. Change DNS only after explicit approval and a rollback checkpoint.

This repository preparation does not perform steps 1 or 2 against AWS and does
not authorize DNS, database, or process changes.
