# VictoryFairySpringServer EC2 Deployment

Production branch strategy:

- `main`: production/release branch
- `dev`: ongoing development branch

Production domain:

- `https://victoryfairy.duckdns.org`

## 1. Install Runtime

Amazon Linux 2023:

```bash
sudo dnf install -y java-17-amazon-corretto git
java -version
```

## 2. Clone Repository

```bash
git clone https://github.com/hwangseokbeom/VictoryFairySpringServer.git
cd VictoryFairySpringServer
git checkout main
```

## 3. Create Server Environment File

Create `.env` on the EC2 instance only. Do not commit it.

```bash
SPRING_PROFILES_ACTIVE=production
SERVER_PORT=8081
PUBLIC_BASE_URL=https://victoryfairy.duckdns.org

# Prefer PostgreSQL for real production.
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/victoryfairy
SPRING_DATASOURCE_USERNAME=replace-with-db-user
SPRING_DATASOURCE_PASSWORD=replace-with-db-password
FLYWAY_BASELINE_ON_MIGRATE=false

GROQ_API_KEY=replace-with-server-only-key
GROQ_MODEL=llama-3.1-8b-instant
MATCH_OUTLOOK_AI_ENABLED=false

NEWS_PROVIDER=naver
NAVER_CLIENT_ID=replace-with-naver-client-id
NAVER_CLIENT_SECRET=replace-with-naver-client-secret
NAVER_NEWS_BASE_URL=https://openapi.naver.com/v1/search/news.json
NEWS_CACHE_TTL_SECONDS=1800

KBO_REFRESH_ENABLED=false
KBO_REFRESH_CRON='0 0 3,9,15,21 * * *'
KBO_REFRESH_SEASON=2026
KBO_REFRESH_ADMIN_TOKEN=replace-with-admin-token
KBO_REFRESH_TIMEOUT_SECONDS=180
KBO_REFRESH_LOCK_ENABLED=true
KBO_SCRAPED_DEV_ENABLED=false

COMMUNITY_ENABLED=false
COMMUNITY_POSTS_REQUIRE_PROFILE=true
COMMUNITY_BLOCK_ENABLED=true

PROFILE_IMAGE_UPLOAD_ENABLED=false
PROFILE_IMAGE_UPLOAD_DIR=/var/lib/victoryfairy/uploads/profile
PROFILE_IMAGE_MAX_BYTES=2097152
PROFILE_IMAGE_MAX_SIDE=512

APP_HOMEPAGE_URL=https://hwangseokbeom.github.io/VictoryFairy-legal/
TERMS_URL=https://hwangseokbeom.github.io/VictoryFairy-legal/terms.html
PRIVACY_POLICY_URL=https://hwangseokbeom.github.io/VictoryFairy-legal/privacy.html
SUPPORT_URL=https://hwangseokbeom.github.io/VictoryFairy-legal/support.html
ACCOUNT_DELETION_URL=https://hwangseokbeom.github.io/VictoryFairy-legal/delete-account.html
DISCLAIMER_URL=https://hwangseokbeom.github.io/VictoryFairy-legal/disclaimer.html
COMMUNITY_POLICY_URL=https://hwangseokbeom.github.io/VictoryFairy-legal/community-policy.html
```

The production profile requires PostgreSQL and has no H2 fallback. Flyway
applies versioned migrations and Hibernate uses `ddl-auto=validate`.

Keep `SPRING_PROFILES_ACTIVE=production`, `KBO_REFRESH_ENABLED=false`, and
`KBO_SCRAPED_DEV_ENABLED=false` on EC2 production. Do not install the
Playwright browser runtime on the production host for the approved
owner-curated workflow.

When the owner requests a data update, collect candidates outside production,
show the result to the owner, and manually enter/import only the approved rows.
The public application reads the stored rows and must not crawl during a user
request.

`POST /api/v1/admin/kbo/refresh` performs direct collection and persistence; it
is not the preview-before-approval flow and therefore remains unused under this
operating policy.

## 4. Build

```bash
./gradlew clean bootJar
```

## 5. Run Manually

```bash
set -a
source .env
set +a
java -jar build/libs/*.jar
```

Check health:

```bash
curl http://localhost:8081/health
curl http://localhost:8081/ready
curl http://localhost:8081/api/v1/legal-links
```

## 6. systemd Service

Create `/etc/systemd/system/victoryfairy.service`:

```ini
[Unit]
Description=VictoryFairy Spring Server
After=network.target

[Service]
User=ec2-user
WorkingDirectory=/home/ec2-user/VictoryFairySpringServer
EnvironmentFile=/home/ec2-user/VictoryFairySpringServer/.env
ExecStart=/usr/bin/java -jar /home/ec2-user/VictoryFairySpringServer/build/libs/victoryfairy-spring-server-1.0.0.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable victoryfairy
sudo systemctl start victoryfairy
```

Logs:

```bash
journalctl -u victoryfairy -f
```

## 7. Nginx Reverse Proxy

Install Nginx:

```bash
sudo dnf install -y nginx
```

Example `/etc/nginx/conf.d/victoryfairy.conf`:

```nginx
server {
    listen 80;
    server_name victoryfairy.duckdns.org;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name victoryfairy.duckdns.org;

    ssl_certificate /etc/letsencrypt/live/victoryfairy.duckdns.org/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/victoryfairy.duckdns.org/privkey.pem;

    client_max_body_size 2m;

    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Start Nginx:

```bash
sudo nginx -t
sudo systemctl enable nginx
sudo systemctl restart nginx
```

With Nginx, keep `SERVER_PORT=8081` and do not expose port `8081` publicly.

## 8. Security Group

Recommended inbound rules:

- SSH `22`: only your IP
- HTTP `80`: open for redirect and certificate renewal only
- HTTPS `443`: open
- App `8081`: closed publicly when using Nginx

## 9. DuckDNS

Point `victoryfairy.duckdns.org` to the EC2 public IP in DuckDNS. After DNS updates, verify:

```bash
curl https://victoryfairy.duckdns.org/health
curl https://victoryfairy.duckdns.org/ready
```

## 10. Production Safety Checklist

- Run with `SPRING_PROFILES_ACTIVE=production`.
- Keep `.env`, database files, uploads, logs, and build output out of git.
- Do not expose Groq or Naver credentials to clients.
- Keep `COMMUNITY_ENABLED=false` until moderation, profile, report, and block flows are ready for live use.
- Keep KBO dev collector endpoints disabled in production. The production profile sets `KBO_SCRAPED_DEV_ENABLED=false`.
- Keep `KBO_REFRESH_ENABLED=false`; update KBO rows only through the
  owner-approved manual curation workflow in `docs/kbo-data-policy.md`.
- Do not use `/api/v1/dev/kbo/collect-scraped-dev`, `/api/v1/dev/kbo/update-scraped-dev`, or `/api/v1/dev/kbo/import-scraped-dev-json` on EC2 production.
- Set `PUBLIC_BASE_URL=https://victoryfairy.duckdns.org` so generated URLs are stable behind Nginx.
