# 🍶 MOZU - 파인다이닝 실시간 예약 서비스

> 매월 1일 11시, 5,000~10,000 TPS 트래픽을 처리하는 파인다이닝 예약 시스템

**팀원:** 강경서 | 강준식 | 오상민 | 정종욱

---

## 📌 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [기술 스택](#기술-스택)
3. [아키텍처](#아키텍처)
4. [주요 기능 및 고도화](#주요-기능-및-고도화)
5. [Kotlin 마이그레이션](#kotlin-마이그레이션)
6. [GitHub Actions](#github-actions)
7. [테스트 결과](#테스트-결과)
8. [개선 사항](#개선-사항)

---

## 프로젝트 개요

2차 Java 프로젝트를 기반으로 다음 세 가지를 3차에서 진행했습니다.

| 구분 | 내용 |
|------|------|
| Kotlin 마이그레이션 | 2차 Java 코드 전체를 Kotlin으로 전환, 실질적 코드 개선 경험 |
| 2차 프로젝트 개선 | 강사님/FT님 피드백 반영 + Redis Sentinel, Redisson 고도화 |
| GitHub Actions | Gemini AI 코드 리뷰 + Slack 연동 자동화 |

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Kotlin |
| Framework | Spring Boot |
| Database | MySQL |
| Cache | Redis (Sentinel 구성 - Master/Replica/Sentinel-3) |
| 분산락 | Redisson (Pub/Sub, Watchdog, Fail-Fast) |
| 모니터링 | Prometheus, Grafana, InfluxDB |
| 부하 테스트 | k6 |
| 인증 | Google OAuth2 (SSO), JWT |
| CI/CD | Git, GitHub Actions, Docker, Docker Compose, Dockerfile |
| AI 코드 리뷰 | Gemini |
| 알림 | Slack |

---

## 아키텍처

```
일반 유저 / 관리자
    ↓
Google OAuth2 (SSO) / ID·PW
    ↓
JWT 발급 (Spring Boot)
    ↓
Spring Boot :8080
├── Redisson 분산락 · 낙관적락(@Version)
└── 동시성 제어 (분산락 1차 · 낙관적락 2차 · Fail-Fast)
    ↓
Redis Master :6379 · Sentinel-1
Redis Replica :6380 · Sentinel-2
Sentinel-3 :26381 · 과반수 투표
    ↓ (Master 장애 → Sentinel 2/3 투표 → Replica 자동 승격)
MySQL :3306 (예약 · 유저 데이터)

모니터링: Prometheus :9090 → Grafana :3001
부하 테스트: InfluxDB :8086 ← k6
```

---

## 주요 기능 및 고도화

### 1. 피드백 반영 개선

#### 재고 중복 반환 방지 (isOccupied 플래그)
- **문제:** 2차에서 재고 중복 반환 방어 로직 없음
- **해결:** DB 엔티티에 `isOccupied` 상태 값 추가, `false`인 경우에만 예약 진행 후 즉시 `true` 변경

#### @Transactional 프록시 문제 해결
- **문제:** 내부 호출 → 프록시 못 거침 → 트랜잭션 미동작 → 재고 반환 성공 + 예약 취소 실패 → 데이터 불일치
- **해결:** `ReleaseStockService`를 별도 서비스로 분리 → 외부 호출로 전환 → 프록시 정상 동작 → 원자적 처리 보장

#### 추가 개선 사항

| 항목 | 개선 내용 |
|------|-----------|
| 타임존 버그 | `ZoneOffset.UTC` → `ZoneId.systemDefault()` |
| Hibernate 예외 | 내부 예외 → 커스텀 예외로 교체 |
| 스레드풀 크기 | TPS 기반 계산 근거 문서화 |
| @Async 예외 | `log.error()` 추가 |
| AdminService 재고 누락 | `release()` 추가 |

---

### 2. 고도화 - Redis Sentinel

#### 도입 배경

| 기존 단일 Redis 문제 | 해결: Sentinel 3대 구성 |
|---------------------|------------------------|
| SPOF - Master 장애 시 분산락 오작동 | Sentinel이 Master 상태 24시간 감시 |
| 낙관적락으로 버티지만 수동 복구 전까지 성능 저하 | 장애 감지 시 과반수(2/3) 투표로 Replica 자동 승격 |
| | 수동 개입 없이 서비스 지속 |

#### 로컬 검증 결과 (페일오버 테스트)

- 동시 유저 100명, 2분, 총 요청 21,300건 중 Master 강제 종료
- `+sdown` → `+odown` → `+switch-master` 1.23초 만에 완료

| 항목 | 결과 |
|------|------|
| 총 요청 수 | 21,300건 |
| 성공률 | 100% |
| 실패율 | 0% |
| 평균 응답시간 | 63.66ms |
| 최대 응답시간 | 10.11s (페일오버 구간) |

#### 페일오버 후 분산락 정합성 확인

- 재고 1개, 동시 유저 100명
- 페일오버 후 새 Master에서도 분산락 정상 동작

| 상태 | 수 |
|------|-----|
| CONFIRMED | 1 |
| CANCELED | 99 |

#### EC2 배포 페일오버

- EC2 서버 자체 중지 (redis-master + Sentinel-1 동시 장애)
- 1.2초 만에 자동 페일오버 확인

---

### 3. 고도화 - Redisson 분산락

#### 도입 배경

| 기존 문제 | 해결 방안 |
|----------|----------|
| Redis 자원 고갈 | Redisson 분산락 + Pub/Sub 방식 |
| 트랜잭션 처리 시간이 락 유지 시간 초과 | Watchdog으로 TTL 자동 갱신 |
| 무조건적 대기 | Fail-Fast 처리 로직 구현 |

#### 주요 로직

**Pub/Sub 통신 방식**
- 락을 획득하지 못한 스레드는 Redis에 조회 요청을 반복하지 않고 구독 상태로 대기
- 락 해제 시 Redis 알림을 수신한 후 작업 재개

**Watchdog**
- 락을 소유한 스레드의 작업이 종료될 때까지 락의 유지 시간을 자동 연장
- `tryLock(5, -1, TimeUnit.SECONDS)` - leaseTime `-1`로 Watchdog 활성화

**Fail-Fast**
- 재고가 0이면 Redis에 `isOccupied:timeSlotId = true` 저장
- 이후 요청은 락 획득 전 `isOccupied` 조회 → `true`면 즉시 예외 반환

#### Watchdog 테스트 결과

- TTL(3초)보다 긴 DB 작업(5초) 강제 삽입으로 락 만료 상황 의도적 재현

| 항목 | 결과 |
|------|------|
| 동시 요청 수 | 10명 |
| 예약 성공(202) | 10건 |
| 예약 충돌(409) | 0건 |
| 평균 응답시간 | 24.9ms |

---

## Kotlin 마이그레이션

### 전환 배경

- 단순 언어 교체가 아닌 **실질적 코드 개선 경험**이 목표
- Null Safety / Data Class / 간결한 문법 / 컬렉션 함수 활용
- 대상: 2차 프로젝트 Java 코드 전체

### 팀 컨벤션

| 항목 | 규칙 | 이유 |
|------|------|------|
| !! 사용 | 금지 → `?.` 또는 `?:` 사용 | null이면 앱 터짐 |
| DTO | data class 적용 | Lombok 제거, equals/hashCode 자동 |
| Entity | data class 금지 | JPA 기본 생성자 필요, 연관관계 무한루프 |
| Optional | `findByIdOrNull ?: throw` | Kotlin다운 null 처리 |
| Stream | 컬렉션 함수로 교체 | 코드 간결화 |

### 전환 전략

선행 전환 순서: **global → test → entity**

| 대상 | 이유 |
|------|------|
| Global | SecurityConfig 등 모든 도메인에 영향 |
| Test | MockK 적용 위해 선행 전환 |
| Entity | 모든 도메인이 참조 → 먼저 전환해야 타입 불일치 최소화 |

### 기술적 이슈 해결

| 문제 | 원인 | 해결 |
|------|------|------|
| Lombok @Getter 인식 못함 | Kapt가 먼저 컴파일 | kapt에 Lombok 등록 |
| Java에서 기본값 파라미터 인식 못함 | Kotlin 기본값은 Java 미지원 | `@JvmOverloads` |
| Java에서 builder() 호출 불가 | companion object는 Java에서 다르게 보임 | `@JvmStatic` |
| Optional vs nullable 타입 불일치 | Java Optional ↔ Kotlin nullable 충돌 | `findByIdOrNull`로 전환 |

### SonarQube 결과 (2차 Java vs 3차 Kotlin)

| 항목 | 2차 (Java) | 3차 (Kotlin) | 개선 |
|------|-----------|-------------|------|
| Reliability | 1 | 0 | ✅ 잠재적 버그 완전 제거 |
| Maintainability | 38 | 17 | ✅ 55% 감소 |
| Duplications | 1.10% | 0.00% | ✅ 중복코드 완전 제거 |
| Quality Gate | Not computed | Passed | ✅ |

---

## GitHub Actions

### Gemini AI 코드 리뷰

**도입 이유**
- PR마다 수동 리뷰 누락 가능성
- 팀 컨벤션 위반을 사람이 매번 체크하기 어려움

**커스텀 설정**

| 항목 | 내용 |
|------|------|
| 리뷰어 페르소나 | 시니어 백엔드 개발자 역할 부여 |
| 팀 컨벤션 자동 검증 | DTO data class, Entity data class 금지, !! 금지, Optional 금지 |
| 심각도 분류 | 🔴 필수 / 🟡 권장 / 🟢 참고 |

**실제 발견 오류**

| 항목 | 심각도 | 내용 |
|------|--------|------|
| DB 비밀번호 하드코딩 | 🔴 High | 환경변수로 수정 |
| JWT 시크릿 키 노출 | 🔴 High | 환경변수로 수정 |
| 타임존 버그 | 🔴 High | systemDefault()로 수정 |
| 재고 중복 반환 | 🔴 High | isOccupied 플래그 추가 |
| @Transactional 내부 호출 | 🟡 Medium | 서비스 분리 |

> ※ 타임존, 재고 중복 반환, @Transactional 이슈는 강사님/FT님 피드백과도 일치

### Slack 연동 흐름

```
PR 오픈
→ CODEOWNERS + 랜덤 리뷰어 자동 지정
→ Slack 알림
→ Gemini 리뷰 및 팀원 리뷰
→ 머지
→ Slack 알림
```

---

## 테스트 결과

### E2E 테스트

| 구분 | 2차 | 3차 |
|------|-----|-----|
| PASS | 48건 (66.7%) | 48건 (66.7%) |
| FAIL | 16건 (22.2%) | 16건 (22.2%) |
| BLOCKED | 8건 (11.1%) | 8건 (11.1%) |
| 총합계 | 72건 | 72건 |

### QA 이슈 처리

| 우선순위 | 2차 | 3차 | 상태 |
|---------|-----|-----|------|
| P0 | 7개 | 7개 | ✅ 모두 처리 완료 |
| P1 | 9개 | 5개 | ✅ 4개 추가 처리 완료 |

---

## 개선 사항

| 항목 | 현재 | 개선 방향 |
|------|------|-----------|
| 실시간 통신 | Polling (주기적 요청) | SSE (Server-Sent Events) 전환 → 서버 부하 감소 |
| Redis 구성 | Sentinel (단일 Master) | Redis Cluster → 고트래픽 환경 수평 확장 |
