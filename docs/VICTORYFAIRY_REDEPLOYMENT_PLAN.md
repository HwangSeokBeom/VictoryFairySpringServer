# VictoryFairy redeployment plan

## 현재 완료 지점

1. 새 계정 EC2/EIP/SSM/보안 그룹
2. private RDS 전용 database/role과 Flyway migration
3. systemd, Nginx, HTTPS, health/readiness
4. 리뷰 프로필 생성·조회
5. 신규 초기화 snapshot
6. Playwright Chromium 설치와 2 GiB swap 보호
7. KST 04:30 일일 KBO refresh 활성화
8. 2026 시즌 675건 최초 refresh와 public 참고용 응답 검증

## 남은 순서

1. 리뷰 프로필로 실제 경기 출석 기록 흐름을 검증한다.
2. Spring Boot를 `CVE-2026-40973` 수정 버전으로 올리고 전체 회귀 검증한다.
3. SNS 이메일 구독 확인을 완료한다.
4. RDS 운영 class와 backup retention 제한을 배포 기록에 남긴다.
5. 서명된 iOS archive와 TestFlight 회귀 검증을 별도 승인 후 수행한다.

Playwright scraper는 하루 한 번의 scheduler 또는 보호된 관리자 재실행에서만
동작한다. 사용자 API 요청은 외부 사이트를 크롤링하지 않고 저장된 데이터만
읽는다. 임의 sample/fake 경기 데이터는 운영 DB에 넣지 않는다.
