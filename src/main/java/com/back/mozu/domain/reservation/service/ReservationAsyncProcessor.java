package com.back.mozu.domain.reservation.service;
import com.back.mozu.domain.queue.service.LockService;  // 추가
import com.back.mozu.domain.reservation.entity.Reservation;
import com.back.mozu.domain.reservation.entity.TimeSlot;
import com.back.mozu.domain.reservation.repository.ReservationRepository;
import com.back.mozu.domain.reservation.repository.TimeSlotRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.UUID;
// 재고 관리
@Service
@RequiredArgsConstructor
public class ReservationAsyncProcessor {
    private final ReservationRepository reservationRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final LockService lockService;  // 추가
    // 새 버전 - 예약 처리
    @Async
    @Transactional
    public void processReservation(UUID reservationId, UUID timeSlotId, int guestCount) {
        // 1. 예약 시도 기록과 타임슬롯을 조회
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약 기록을 찾을 수 없습니다."));
        TimeSlot timeSlot = timeSlotRepository.findById(timeSlotId)
                .orElseThrow(() -> new IllegalArgumentException("타임슬롯을 찾을 수 없습니다."));
        // 분산 락 토큰 (이 요청 고유 식별자)  // 추가
        String lockToken = reservationId.toString();  // 추가
        boolean lockAcquired = false;  // 추가
        try {
            // 2. 분산 락 획득 시도  // 추가
            lockAcquired = lockService.acquireLock(timeSlotId.toString(), lockToken);  // 추가
            if (!lockAcquired) {  // 추가
                // 락 획득 실패 → 다른 요청이 처리 중  // 추가
                reservation.cancelReservation("LOCK_ACQUIRE_FAIL");  // 추가
                return;  // 추가
            }  // 추가
            // 3. 재고 점유 (낙관적 락 발동)
            // occupy 내부에서 stock < guestCount 체크와 차감을 동시에 함
            timeSlot.occupy(guestCount);
            // 4. 예약 상태 변경 (PENDING -> CONFIRMED)
            reservation.confirmReservation();
        } catch (ObjectOptimisticLockingFailureException e) {
            // [CASE A] 다른 유저가 찰나의 순간에 먼저 가져감 (낙관적 락 충돌)
            // 재고는 실제로 안 깎였으므로 예약만 취소 처리
            reservation.cancelReservation("OPTIMISTIC_LOCK_FAIL");
        } catch (IllegalArgumentException | IllegalStateException e) {
            // [CASE B] 재고 부족이거나 이미 취소된 건인 경우
            reservation.cancelReservation("RESERVATION_FAILED");
            // 혹시 모르니 여기서도 재고는 건드리지 않음 (이미 occupy에서 실패했으므로)
        } catch (Exception e) {
            // [CASE C] 그 외 예상치 못한 시스템 에러 (DB 연결 끊김 등)
            reservation.cancelReservation("SYSTEM_ERROR");
            // 만약 occupy는 성공했는데 여기서 터졌다면? 안전하게 재고를 돌려줌
            timeSlot.release(guestCount);
        } finally {
            // 5. 락 반드시 해제 (성공/실패 무관, 데드락 방지)  // 추가
            if (lockAcquired) {  // 추가
                lockService.releaseLock(timeSlotId.toString(), lockToken);  // 추가
            }  // 추가
        }
    }
}