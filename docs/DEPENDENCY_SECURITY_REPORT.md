# VictoryFairy dependency security report

검증일: 2026-07-26

## 변경

- Spring Boot: `3.4.11` → `3.5.16`
- Jackson BOM: `2.21.5`
- Netty: `4.1.136.Final`
- PostgreSQL JDBC: `42.7.12`

Spring Boot `3.5.14`부터 기존 blocker였던 `CVE-2026-40973` 수정이 포함되며,
`3.5.16`은 3.5.x의 마지막 OSS 릴리스다.

## 검증

다음 명령으로 애플리케이션의 production runtime classpath를 해석했다.

```text
./gradlew clean test bootJar --no-daemon
./gradlew -I <temporary-read-only-init-script> \
  printResolvedRuntimeCoordinates --no-daemon
```

Gradle의 `productionRuntimeClasspath`에서 실제 선택된 Maven 좌표 104개를
수집한 뒤 OSV batch API로 조회했다.

```text
resolved coordinates: 104
vulnerability-package pairs: 0
unique advisories: 0
```

`clean test bootJar`도 성공했다. 테스트 컴파일 과정에서 기존 `@MockBean`
deprecated 경고가 남지만 보안 업데이트 실패나 런타임 오류는 아니다.

## 해석과 한계

- 결과는 2026-07-26 시점의 resolved 좌표와 OSV 데이터에 대한 검사다.
- 새로운 advisory가 공개될 수 있으므로 배포 직전과 정기 운영 점검에서 다시
  실행해야 한다.
- Spring Boot 3.5.x는 OSS 지원이 종료됐으므로 별도 후속 작업에서 4.x 전환
  가능성을 검토해야 한다.
- 현재 공개 EC2에는 이 artifact를 아직 배치하지 않았다.

현재 판정은 `DEPENDENCY_SECURITY_GATE_PASSED`,
`DEPLOYMENT_PENDING_APPROVAL`이다.
