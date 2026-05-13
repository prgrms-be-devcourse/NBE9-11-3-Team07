# 🍽️ Moju 실시간 예약 시스템

> 대규모 트래픽 환경(5,000~10,000 TPS)에서 좌석 중복 예약 없이 안정적으로 예약을 처리하기 위한  
> **대기열 기반 실시간 예약 시스템**

---

# 📌 프로젝트 개요

한국의 파인 다이닝 레스토랑 **Moju**는  매월 1일 오전 11시에 다음 달 전체 좌석 예약을 오픈하며,  
오픈 직후 약 **5,000 ~ 10,000 TPS** 트래픽이 집중되고 **30초 이내 전석 매진**되는 고부하 환경을 가진다.

기존 외부 예약 플랫폼 의존으로 인한:

- 높은 수수료 비용 증가
- 플랫폼 장애 리스크
- 예약 정책 종속 문제

를 해결하기 위해 **자체 실시간 예약 시스템 구축**을 목표로 프로젝트를 진행하였다.

---

# 🎯 프로젝트 목표

본 프로젝트는 다음과 같은 핵심 기술 문제 해결을 목표로 한다.

- 대규모 동시 요청 환경에서 **중복 예약 방지**
- 예약 및 재고 처리의 **데이터 정합성 보장**
- 대기열 기반 **부하 분산 처리**
- Redis 기반 **분산 락 적용**
- DB 조회 성능 최적화
- k6 기반 부하 테스트 수행

---

# 🧱 시스템 아키텍처
<img width="648" height="588" alt="Image" src="https://github.com/user-attachments/assets/a8003b55-4526-4383-a33b-85c99597775b" />



## 주요 구성

| 구성 | 기술 |
|------|------|
| Frontend | Next.js |
| Backend | Spring Boot |
| Database | MySQL |
| Cache / Lock | Redis |
| Monitoring | Prometheus, Grafana |
| Load Test | k6 |
| Test Environment | Docker |

---

# 👨‍👩‍👧‍👦 팀원 소개

| 이름 | 역할 |
| 윤현정 | 팀장, 기획서 작성, API개발, 테스트 환경 구축 |
|------|------|
| 강경서 | 폴더 구조 및 깃 컨벤션, API 개발,프론트 개발 및 API연동, 점유/반환 로직 고도화|
| 강준식 | API 스펙 작성,API 개발, 점유/반환 로직고도화 |
| 오상민 | ERD 작성,API 개발, 대기열 시스템 구현 |
| 정종욱 | API 스펙 작성, API 개발, 대기열 관리 |

---

# 🧠 핵심 설계 고민

---

<details>
<summary>1️⃣ Source of Truth 결정</summary>

## 문제

- 대기열 → Redis
- 재고 관리 → DB

서로 다른 기준을 사용할 경우  
재고 불일치 발생 가능

### 문제 예시
취소 발생

DB 재고 → 복구
Redis 재고 → 유지

→ 잘못된 재고 판단
→ Overbooking 발생

## 해결


DB = Source of Truth
Redis = 보조 계층


## 설계 원칙

- Redis는 보조 수단
- DB는 최종 기준
- 모든 상태 변경은 DB 기준

</details>

---

<details>
<summary>2️⃣ Redis Lock vs Optimistic Lock 비교</summary>

## 테스트

- 동시 요청: 10,000명
- 재고: 1개
- 도구: k6

## 결과

| 항목 | Redis Lock | Optimistic Lock |
|------|-------------|----------------|
| 실행 시간 | 7.2초 | 31.1초 |
| TPS | 1,390 | 321 |
| 평균 응답 | 141ms | 611ms |

## 결론


Redis Lock → 4.3배 성능 우세
최종 채택
</details>

---

<details>
<summary>3️⃣ 예약 취소 정책 설계</summary>

## 구현

- 24시간 이내 취소 시 패널티
- penaltyUntil 필드 추가

## 처리 흐름


취소 발생
→ penalty 저장
→ 이후 예약 시 차단
## 결과

- 정책 정상 동작
- 재고 정합성 유지

</details>

---

<details>
<summary>4️⃣ 예약 수정 원자성 처리</summary>

## 문제

취소 후 즉시 재예약 공격 가능

## 해결


CANCEL_PENDING 상태 도입

효과:

- 재고 즉시 반환 방지
- 매크로 공격 차단

</details>

---

<details>
<summary>5️⃣ 대기열 엣지 케이스 처리</summary>

## 시나리오

- 선행 요청: 12명
- 후행 요청: 5명
- 재고: 10개

## 결과

- Success Rate: 100%
- Avg Latency: 140ms
- 후행 요청 정상 처리

</details>

---

<details>
<summary>6️⃣ DB 조회 성능 최적화</summary>

## 문제

Full Scan 발생

## 해결

```sql
CREATE INDEX idx_reservations_user_id
ON reservations(user_id);
```

### 결과

- 조회 속도 **414배 개선**
- TPS **57배 증가**

</details>

---

# 📡 주요 API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/reservations` | 예약 생성 |
| GET | `/api/v1/my/reservations` | 예약 조회 |
| PATCH | `/api/v1/my/reservations/{id}` | 예약 변경 |
| POST | `/api/v1/my/reservations/{id}/cancel` | 예약 취소 |
| GET | `/api/v1/waiting-room/me` | 대기열 조회 |

---

# 🧪 테스트 환경

## 사용 도구

- **k6**
- **Prometheus**
- **Grafana**
- **Docker**
  → 부하 테스트 환경(k6, Prometheus, Grafana)을
    동일한 환경에서 실행하기 위해 사용

---

# 🚀 실행 방법

## 1️⃣ 테스트 환경 실행 (Docker)

부하 테스트 및 모니터링 환경을 실행합니다.

```bash
docker-compose up -d --build      
```

실행되는 서비스:

k6
Prometheus
Grafana
InfluxDB


# 📁 디렉토리 구조

본 프로젝트는 도메인 중심 패키지 구조로 구성되어 있으며,  
각 도메인은 독립적인 Controller, Service, Repository 계층을 가진다.

```bash
src/
 ┣ main/
 ┃ ┣ java/
 ┃ ┃ ┗ com/reservation/
 ┃ ┃
 ┃ ┃ ┣ auth/           # 인증 도메인
 ┃ ┃ ┣ reservation/    # 예약 도메인
 ┃ ┃ ┣ queue/          # 대기열 처리
 ┃ ┃ ┣ admin/          # 관리자 기능
 ┃ ┃ ┣ setting/        # 운영 정책 및 휴무일 관리
 ┃ ┃ ┣ global/         # 공통 설정 및 인프라
 ┃ ┃
 ┃ ┣ resources/
 ┃ ┃ ┣ application.yml
 ┃ ┃ ┣ static/
 ┃ ┃ ┗ templates/
 ┃
 ┣ test/
 ┃ ┗ java/
 ┃
 ┣ docs/               # 아키텍처 및 테스트 이미지
 ┗ docker/             # 테스트 환경 Docker 설정
```
 
# 📈 성능 결과 요약

- Redis Lock 적용 → **4.3배 성능 개선**
- DB Index 적용 → **414배 조회 성능 개선**
- 대기열 처리 → **100% 성공률 유지**

---

# 🔮 향후 개선 방향

- Redis Cluster 구성
- Kafka 기반 이벤트 처리
- 실제 운영 환경 배포
- 장애 복구 전략 강화

---

# 🧾 License

MIT License

