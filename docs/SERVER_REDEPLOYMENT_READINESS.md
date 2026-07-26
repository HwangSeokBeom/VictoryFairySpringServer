# VictoryFairy server redeployment readiness

검증 기준일: 2026-07-26
상태: `SECURITY_UPDATE_VERIFIED_DEPLOYMENT_PENDING`

## 운영 문서

SSM 접속, systemd/Nginx 로그, 안전한 재시작, KBO 일일 갱신 점검, 장애 대응 및
후속 배포 절차는 [OPERATIONS_RUNBOOK.md](./OPERATIONS_RUNBOOK.md)에 정리했다.
검증된 의존성 업데이트와 OSV 결과는
[DEPENDENCY_SECURITY_REPORT.md](./DEPENDENCY_SECURITY_REPORT.md), RDS 단계별
내구성 결정은 [RDS_DURABILITY_DECISION.md](./RDS_DURABILITY_DECISION.md)에
정리했다.

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
3. CloudWatch alarm 이메일 구독은 2026-07-26 확인 완료됐다. AWS가 구체적인
   subscription ARN을 반환하므로 실제 알림 전달 경로가 활성 상태다.
4. readiness 브랜치에서 Spring Boot `3.5.16`으로 올리고 Spring 관리 BOM을
   통해 Jackson `2.21.5`, Netty `4.1.136.Final`, PostgreSQL JDBC `42.7.12`를
   고정했다. `clean test bootJar`가 통과했으며 resolved production runtime
   104개 좌표를 OSV batch API로 재검사한 결과 vulnerability-package 연관
   항목은 0건이었다. 현재 공개 EC2에는 이 artifact를 아직 배치하지 않았다.

서버 기본 기능, 리뷰 프로필, 참고용 KBO 경기·순위와 일일 자동 refresh, SNS
알림 구독은 공개 환경에서 동작한다. 보안 업데이트 코드는 검증됐지만 배포는
별도 승인 범위이므로 현재 cutover 기준은
`GO_AFTER_APPROVED_SECURITY_ARTIFACT_DEPLOYMENT`이다.
