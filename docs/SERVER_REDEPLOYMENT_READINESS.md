# VictoryFairy server redeployment readiness

검증 기준일: 2026-07-26
상태: `PUBLIC_RUNTIME_READY_WITH_DATA_BLOCKER`

## 배치된 런타임

- AWS region: `ap-northeast-2`
- EC2: `i-0c3390d9d06a7a016`
- Elastic IP: `3.34.229.77`
- public host: `victoryfairy.duckdns.org`
- source commit: `c5edfdd155fa5558747fb39d57c49170e338c459`
- Java: 17
- Spring Boot internal port: `8081`
- process manager: systemd service `victoryfairy`
- PostgreSQL: private shared RDS의 전용 `victoryfairy` database와 전용 role

Nginx는 `127.0.0.1:8081`만 사용하며 8081과 5432는 외부에 노출하지 않는다.

## 완료된 실행 검증

- Flyway migration 1/1
- systemd enable/start 및 재부팅 후 복구
- localhost/public `/health`와 `/ready`
- HTTP to HTTPS redirect
- TLS certificate와 자동 갱신 timer
- 리뷰 프로필 생성·조회
- KBO 팀 목록 10개
- 서비스 IAM role의 자기 secret 접근 및 타 서비스 secret 거부
- 외부 22, 8081, 5432 차단
- CloudWatch Agent가 application/Nginx 로그와 memory/root-disk 지표를 수집
- EC2 status/CPU와 shared RDS CPU/storage/connection alarm 생성

## 데이터와 백업

- 기존 운영 데이터: `NO_BACKUP_FOUND`, 사용자 복구 포기 승인
- 신규 빈 database 초기화와 리뷰 프로필 1개 생성
- 암호화 snapshot `project-services-postgres-initialized-20260726` 사용 가능
- 신규 초기 상태: `RECOVERABLE`

비밀값은 다음 이름의 Secrets Manager 항목에만 존재한다.

- `production/victoryfairy/database`
- `production/victoryfairy/runtime`
- `production/victoryfairy/review-profile`

## 현재 차단 항목

1. 경기와 순위 데이터가 0건이다.
2. `docs/kbo-data-policy.md`는 scraped KBO 데이터의 production/App Store
   사용을 별도 권리·라이선스 검토 없이 금지한다. 따라서 Playwright 설치와
   production refresh는 진행하지 않고 scheduler를 비활성 상태로 유지한다.
3. 공식 또는 사용 허가가 확인된 데이터 공급 계약을 먼저 확정해야 한다.
4. `COMMUNITY_ENABLED=false`, profile upload와 AI 기능도 비활성 상태다.
5. RDS `db.t4g.micro`, single-AZ, backup retention 1일은 리뷰/초기 검증용
   구성이다. Free Tier 제한으로 retention 7일 변경은 거부되었다.
6. CloudWatch alarm에는 아직 통지 대상이 연결되지 않았다.

서버 기본 기능과 리뷰 프로필은 공개 상태에서 동작하지만 핵심 경기 데이터가
없으므로 App Store 리뷰 기준 `NO-GO`이다.
