# VictoryFairy server redeployment readiness

검증 기준일: 2026-07-26
상태: `PUBLIC_RUNTIME_READY_WITH_DAILY_KBO_REFRESH`

## 운영 문서

SSM 접속, systemd/Nginx 로그, 안전한 재시작, KBO 일일 갱신 점검, 장애 대응 및
후속 배포 절차는 [OPERATIONS_RUNBOOK.md](./OPERATIONS_RUNBOOK.md)에 정리했다.

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
- 2026-07-26 외부 HTTPS에서 리뷰 프로필과 canonical `/health`, `/ready`
  재검증
- HTTP to HTTPS redirect
- TLS certificate와 자동 갱신 timer
- 리뷰 프로필 생성·조회
- KBO 팀 목록 10개
- production Playwright Chromium 설치와 서비스 계정 실행 경로 검증
- KST 04:30 일일 KBO refresh 활성화
- 보호된 최초 refresh 성공: 2026 시즌 675건 수집·삽입, warning 0건
- 저장 결과: 종료 470건, 예정 180건, 취소 25건
- public 경기 API 5건과 순위 10팀을 `참고용 경기 정보`로 검증
- KBO 사용자 API는 외부 수집기를 호출하지 않고 저장된 행만 조회
- 서비스 IAM role의 자기 secret 접근 및 타 서비스 secret 거부
- 외부 22, 8081, 5432 차단
- CloudWatch Agent가 application/Nginx 로그와 memory/root-disk 지표를 수집
- EC2 status/CPU와 shared RDS CPU/storage/connection alarm 생성
- 15개 공통 alarm의 `ALARM`/`OK` action을 SNS topic
  `project-services-ops-alerts`에 연결

`/actuator/health`는 Actuator dependency를 사용하지 않는 현재 서버의 계약
경로가 아니다. 외부 모니터링은 `/health`와 database probe를 포함한 `/ready`만
사용해야 한다.

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

1. `COMMUNITY_ENABLED=false`, profile upload와 AI 기능도 비활성 상태다.
2. RDS `db.t4g.micro`, single-AZ, backup retention 1일은 리뷰/초기 검증용
   구성이다. Free Tier 제한으로 retention 7일 변경은 거부되었다.
3. CloudWatch alarm 이메일 구독은 요청됐지만 아직 `PendingConfirmation`
   상태다. 운영 담당자가 SNS 확인 이메일의 링크를 눌러야 한다.
4. resolved `runtimeClasspath`를 OSV로 점검한 결과 15개 Maven package에
   68개 vulnerability-package 연관 항목이 확인됐다. 이 수치는 동일 advisory가
   여러 Netty module에 겹쳐 나타나는 것을 포함한다. 특히 Spring Boot
   `3.4.11`은 `CVE-2026-40973` (`HIGH`)의 영향 대상이고 3.4 계열에는 fix가
   제공되지 않는다. 공식적으로 유지되는 3.5 계열의 수정 버전 `3.5.14` 이상으로
   올리고 전체 회귀 검증하기 전에는 운영 cutover를 승인하지 않는다.

서버 기본 기능, 리뷰 프로필, 참고용 KBO 경기·순위와 일일 자동 refresh는 공개
환경에서 동작한다. Spring Boot 보안 업데이트와 SNS 구독 확인이 남아 있으므로
현재 운영 cutover 기준은 `NO-GO`이다.
