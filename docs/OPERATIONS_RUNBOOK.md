# VictoryFairy 운영 Runbook

검증 기준일: 2026-07-26
리전: `ap-northeast-2`
EC2: `i-0c3390d9d06a7a016`
도메인: `https://victoryfairy.duckdns.org`
내부 포트: `8081`
systemd 서비스: `victoryfairy`

SSH 22번 포트는 열지 않는다. 집과 회사 모두 AWS 인증 후 SSM Session
Manager로 접속한다.

## 1. 접속

```bash
aws sts get-caller-identity

aws ssm start-session \
  --region ap-northeast-2 \
  --target i-0c3390d9d06a7a016
```

배치 경로:

- app: `/opt/victoryfairy/app.jar`
- scripts: `/opt/victoryfairy/scripts`
- environment: `/etc/victoryfairy/victoryfairy.env`
- data: `/var/lib/victoryfairy`
- log: `/var/log/victoryfairy/app.log`

서비스 사용자는 `victoryfairy`이며 interactive login 계정으로 사용하지 않는다.

## 2. 매일 확인

외부:

```bash
curl -fsS https://victoryfairy.duckdns.org/health | jq
curl -fsS https://victoryfairy.duckdns.org/ready | jq
curl -fsS 'https://victoryfairy.duckdns.org/api/v1/kbo/standings?season=2026' | jq
```

서버:

```bash
sudo systemctl is-active victoryfairy nginx
sudo systemctl is-enabled victoryfairy nginx
sudo systemctl status victoryfairy --no-pager
curl -fsS http://127.0.0.1:8081/health | jq
curl -fsS http://127.0.0.1:8081/ready | jq
free -h
swapon --show
```

정상 기준:

- `victoryfairy`, Nginx가 active/enabled
- localhost/public `/health`, `/ready` 200
- KBO 응답의 `source=reference`
- `sourceLabel=참고용 경기 정보`
- disclosure 존재
- 일일 refresh cron: KST 04:30
- scraped-dev scheduler 비활성

## 3. 로그

```bash
sudo tail -n 300 /var/log/victoryfairy/app.log
sudo journalctl -u victoryfairy -n 200 --no-pager
sudo tail -n 200 /var/log/nginx/access.log
sudo tail -n 200 /var/log/nginx/error.log
```

KBO refresh:

```bash
sudo grep -Ei 'KBO|refresh|Playwright|crawler|scheduler' \
  /var/log/victoryfairy/app.log | tail -n 200
```

CloudWatch Logs:

- `/project-services/victoryfairy/system`
- `/project-services/victoryfairy/nginx`
- 보관 기간: 14일

관리자 token, DB URL, 외부 provider 비밀값을 출력하지 않는다.

## 4. KBO 일일 갱신

현재 운영 계약:

- `KBO_REFRESH_ENABLED=true`
- `KBO_REFRESH_CRON='0 30 4 * * *'`
- `KBO_SCRAPED_DEV_ENABLED=false`
- `KBO_SCRAPED_DEV_SCHEDULER_ENABLED=false`
- Playwright browser: `/opt/victoryfairy/playwright`

사용자 API 요청은 크롤러를 실행하지 않고 DB의 저장 행만 읽는다. 자동 갱신이
실패하면 로그와 메모리/swap을 먼저 확인한다. 보호된 관리자 refresh는
Secrets Manager의 token을 노출하지 않는 승인된 SSM Run Command 절차로만
재실행한다. token을 셸 기록, process argument, 로그에 넣지 않는다.

최초 운영 검증 기준:

- 2026 시즌 675건 수집·삽입
- warning 0
- 종료 470, 예정 180, 취소 25
- public 경기·순위 응답이 `참고용 경기 정보`

## 5. 안전한 재시작

```bash
sudo /opt/victoryfairy/scripts/verify_production_contract.sh
sudo systemctl restart victoryfairy
sleep 5
sudo systemctl status victoryfairy --no-pager
curl -fsS http://127.0.0.1:8081/ready | jq
curl -fsS https://victoryfairy.duckdns.org/ready | jq
```

Nginx:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

contract 검증이나 localhost readiness가 실패하면 public 전환을 진행하지 않는다.

## 6. 환경설정과 비밀값

운영 파일:

- `/etc/victoryfairy/victoryfairy.env`
- root 관리, application service에서만 읽기

Secrets Manager:

- `production/victoryfairy/database`
- `production/victoryfairy/runtime`
- `production/victoryfairy/review-profile`

환경 변경은 Secrets Manager에 먼저 반영하고 승인된 동기화 절차로 env 파일을
갱신한다. `KBO_REFRESH_ADMIN_TOKEN`과 DB URL을 출력하지 않는다.

## 7. 배포

운영 서버에서 source를 임의 변경하지 않는다.

1. clean 브랜치에서 Gradle test와 production contract 검증을 완료한다.
2. dependency/OSV 결과를 검토하고 HIGH 취약점을 해결한다.
3. disposable PostgreSQL에서 Flyway migration을 rehearsal한다.
4. exact commit과 이전 `app.jar` rollback artifact를 기록한다.
5. RDS snapshot 상태를 확인한다.
6. 새 JAR를 별도 이름으로 업로드하고 checksum을 확인한다.
7. `verify_production_contract.sh` 통과 후 원자적으로 app.jar 대상을 전환한다.
8. systemd restart 후 localhost/public health와 KBO 저장 조회를 확인한다.

운영 cutover 전 Spring Boot `CVE-2026-40973` 대응 버전과 전체 회귀 검증이
필수다.

## 8. 장애 대응

앱 시작 실패:

```bash
sudo systemctl status victoryfairy --no-pager
sudo tail -n 300 /var/log/victoryfairy/app.log
sudo /opt/victoryfairy/scripts/verify_production_contract.sh
```

KBO refresh 실패:

1. Playwright/Chromium 오류 확인
2. `free -h`, `swapon --show`, disk 확인
3. 중복 scheduler 실행 여부 확인
4. 마지막 성공 데이터는 계속 제공
5. 원인 해결 후 보호된 관리자 refresh 1회
6. public source label/disclosure와 행 수 확인

DB 복구는 암호화 RDS snapshot에서 새 인스턴스를 복원해 검증한 후 전환한다.
기존 DB나 Docker volume을 삭제하지 않는다.

## 9. 알림과 인증서

```bash
aws sns list-subscriptions-by-topic \
  --region ap-northeast-2 \
  --topic-arn arn:aws:sns:ap-northeast-2:486208157237:project-services-ops-alerts

sudo certbot certificates
sudo systemctl status certbot-renew.timer --no-pager
```

SNS 알림을 받으면 health/readiness, systemd, application log, Nginx, RDS alarm,
KBO 마지막 성공 시각 순으로 확인한다.
