# VictoryFairy redeployment plan

## 현재 완료 지점

1. 새 계정 EC2/EIP/SSM/보안 그룹
2. private RDS 전용 database/role과 Flyway migration
3. systemd, Nginx, HTTPS, health/readiness
4. 리뷰 프로필 생성·조회
5. 신규 초기화 snapshot

## 남은 순서

1. CI 또는 별도 임시 build host에서 Playwright Chromium runtime을 준비한다.
2. artifact checksum과 실행 사용자를 고정한 뒤 EC2에 배치한다.
3. production KBO refresh를 한 번 실행하고 팀·경기·순위 관계를 검증한다.
4. 리뷰 프로필로 실제 경기 출석 기록 흐름을 검증한다.
5. application, Nginx, JVM, DB alarm과 log retention을 설정한다.
6. RDS 운영 class와 backup retention을 승인한다.
7. 서명된 iOS archive와 TestFlight 회귀 검증을 별도 승인 후 수행한다.

KBO source가 검증될 때까지 임의 sample/fake 경기 데이터를 운영 DB에 넣지 않는다.
