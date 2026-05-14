# 코틀린 전환 컨벤션

## 필수 적용 규칙

### ① !! 사용 금지
- !! 제거 → ?. 또는 ?: 로 변경
- null이면 기본값 반환: `?.length ?: 0`
- null이면 예외: `?: throw Exception()`

### ② Lombok 제거
- @Getter, @Setter, @Builder, @ToString 제거
- 코틀린 프로퍼티로 대체

### ③ DTO → data class
- 모든 DTO, 요청/응답 클래스는 data class로 전환
- val 사용 (불변)

### ④ Entity → data class 금지
- Entity는 일반 class 유지
- allOpen 플러그인으로 처리
- var 사용 가능

### ⑤ Optional → findByIdOrNull
- orElseThrow → findByIdOrNull ?: throw
- orElse → ?: 로 변경

### ⑥ Stream → 컬렉션 함수
- .stream().filter().collect() → .filter { }
- .stream().map().collect() → .map { }

### ⑦ ServiceException → sealed class
- 모든 예외는 sealed class로 정의
- global/exception 폴더에 정의

### ⑧ RsData → data class
- data class로 전환
- companion object로 팩토리 메서드 정의

## 코드 리뷰 기준
- !! 있으면 반드시 수정
- data class 적용 여부 확인
- nullable 처리 적절한지 확인
- 트랜잭션 롤백 의도치 않게 발생하는지 확인
