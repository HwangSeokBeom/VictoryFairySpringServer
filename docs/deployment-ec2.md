# VictoryFairySpringServer EC2 Deployment

Production branch strategy:

- `main`: production/release branch
- `dev`: ongoing development branch

Production domain:

- `http://victoryfairy.duckdns.org`

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
PUBLIC_BASE_URL=http://victoryfairy.duckdns.org

# Prefer PostgreSQL for real production.
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/victoryfairy
SPRING_DATASOURCE_USERNAME=replace-with-db-user
SPRING_DATASOURCE_PASSWORD=replace-with-db-password

GROQ_API_KEY=replace-with-server-only-key
GROQ_MODEL=llama-3.1-8b-instant
MATCH_OUTLOOK_AI_ENABLED=false

NEWS_PROVIDER=naver
NAVER_CLIENT_ID=replace-with-naver-client-id
NAVER_CLIENT_SECRET=replace-with-naver-client-secret
NAVER_NEWS_BASE_URL=https://openapi.naver.com/v1/search/news.json
NEWS_CACHE_TTL_SECONDS=1800

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

`application-production.yml` has a temporary H2 fallback so the jar can boot before PostgreSQL is ready. Do not use that fallback for real production data; H2 files are not production-safe for this service.

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
- HTTP `80`: open
- HTTPS `443`: add later when TLS is configured
- App `8081`: closed publicly when using Nginx

## 9. DuckDNS

Point `victoryfairy.duckdns.org` to the EC2 public IP in DuckDNS. After DNS updates, verify:

```bash
curl http://victoryfairy.duckdns.org/health
```

## 10. Production Safety Checklist

- Run with `SPRING_PROFILES_ACTIVE=production`.
- Keep `.env`, database files, uploads, logs, and build output out of git.
- Do not expose Groq or Naver credentials to clients.
- Keep `COMMUNITY_ENABLED=false` until moderation, profile, report, and block flows are ready for live use.
- Keep KBO dev collector endpoints disabled in production. The production profile sets `KBO_SCRAPED_DEV_ENABLED=false`.
- Set `PUBLIC_BASE_URL=http://victoryfairy.duckdns.org` so generated URLs are stable behind Nginx.
