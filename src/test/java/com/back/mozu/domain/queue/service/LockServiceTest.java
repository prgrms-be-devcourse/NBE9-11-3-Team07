package com.back.mozu.domain.queue.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LockServiceTest {

    @Autowired
    private LockService lockService;

    @Test
    @DisplayName("락 획득 성공 및 해제")
    void acquireAndReleaseLock() {

        // 테스트 데이터 생성
        String timeSlotId = "test-slot-1";
        String token1 = UUID.randomUUID().toString();
        String token2 = UUID.randomUUID().toString();

        // 첫 번째 사용자가 락 획득 시도 -> 성공
        boolean isAcquired = lockService.acquireLock(timeSlotId, token1);
        assertThat(isAcquired).isTrue();

        // 첫 번째 사용자가 락 해제
        lockService.releaseLock(timeSlotId, token1);

        // 두 번째 사용자가 락 획득 시도 -> 성공
        boolean isAcquiredAgain = lockService.acquireLock(timeSlotId, token2);
        assertThat(isAcquiredAgain).isTrue();

        // 두 번째 사용자가 락 해제
        lockService.releaseLock(timeSlotId, token2);
    }

    @Test
    @DisplayName("다른 사용자가 보유한 락 획득 시도 시 예외 발생")
    void mutualExclusionTest() {

        // 테스트 데이터 생성
        String timeSlotId = "test-slot-2";
        String Token = UUID.randomUUID().toString();
        String otherToken = UUID.randomUUID().toString();

        // 첫 번째 사용자가 락 획득 시도 -> 성공
        boolean Result = lockService.acquireLock(timeSlotId, Token);
        assertThat(Result).isTrue();

        // 두 번째 사용자가 락 획득 시도 -> 실패
        boolean otherResult = lockService.acquireLock(timeSlotId, otherToken);

        // 두 번째 사용자의 락 획득 시도 실패 반환
        assertThat(otherResult).isFalse();

        // 첫 번째 사용자가 락 해제
        lockService.releaseLock(timeSlotId, Token);
    }

    @Test
    @DisplayName("다른 사용자의 락 해제 시도시 예외 발생")
    void safeReleaseTest() {
        
        // 테스트 데이터 생성
        String timeSlotId = "test-slot-3";
        String Token = UUID.randomUUID().toString();
        String otherToken = UUID.randomUUID().toString();

        // 첫 번째 사용자가 락 획득 시도 -> 성공
        lockService.acquireLock(timeSlotId, Token);

        // 두 번째 사용자가 가짜 토큰으로 락 해제 시도
        lockService.releaseLock(timeSlotId, otherToken);

        // Lua 스크립트가 방어
        // 첫 번째 사용자는 여전히 락을 가지므로 새로 획득 시도 시 실패
        boolean otherAcquireResult = lockService.acquireLock(timeSlotId, otherToken);
        assertThat(otherAcquireResult).isFalse();

        // 첫 번째 사용자가 락 해제
        lockService.releaseLock(timeSlotId, Token);

        // 락 해제 시 다른 사용자가 획득 가능
        assertThat(lockService.acquireLock(timeSlotId, otherToken)).isTrue();

        // 두 번째 사용자가 락 해제
        lockService.releaseLock(timeSlotId, otherToken);
    }
}