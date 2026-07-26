# VictoryFairy redeployment plan

## 현재 완료 지점

1. 새 계정 EC2/EIP/SSM/보안 그룹
2. private RDS 전용 database/role과 Flyway migration
3. systemd, Nginx, HTTPS, health/readiness
4. 리뷰 프로필 생성·조회
5. 신규 초기화 snapshot

## 남은 순서

1. 소유자가 데이터 갱신을 지시하면 production 밖의 격리된 환경에서 필요한
   KBO 경기 정보를 수집한다.
2. 수집 결과를 소유자에게 제시하고 일정·팀·구장·점수·상태를 검토한다.
3. 소유자가 반영을 승인한 행만 보호된 관리자 입력/가져오기 절차로 수동
   반영한다.
4. public API가 저장된 행만 `참고용 경기 정보`와 고지 문구로 제공하는지
   검증한다.
5. 리뷰 프로필로 실제 경기 출석 기록 흐름을 검증한다.
6. Spring Boot를 `CVE-2026-40973` 수정 버전으로 올리고 전체 회귀 검증한다.
7. SNS 이메일 구독 확인을 완료한다.
8. RDS 운영 class와 backup retention 제한을 배포 기록에 남긴다.
9. 서명된 iOS archive와 TestFlight 회귀 검증을 별도 승인 후 수행한다.

Playwright scraper와 자동 scheduler는 production에서 활성화하지 않는다.
사용자 API 요청은 외부 사이트를 크롤링하지 않으며, 소유자가 명시적으로 승인한
수동 반영 데이터만 읽는다. 임의 sample/fake 경기 데이터는 운영 DB에 넣지
않는다.
