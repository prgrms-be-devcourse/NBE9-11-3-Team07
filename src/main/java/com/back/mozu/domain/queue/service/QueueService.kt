package com.back.mozu.domain.queue.service

import com.back.mozu.domain.customer.service.CustomerService
import com.back.mozu.domain.queue.dto.QueueDto.AttemptRequest
import com.back.mozu.domain.queue.dto.QueueDto.AttemptResponse
import com.back.mozu.domain.queue.dto.QueueDto.StatusResponse
import com.back.mozu.domain.reservation.entity.Reservation
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import com.back.mozu.domain.reservation.service.ReservationAsyncProcessor
import com.back.mozu.global.redis.RedisUtil
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.data.repository.findByIdOrNull
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

// 메인 서비스
@Service
class QueueService(
    private val reservationRepository: ReservationRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val asyncProcessor: ReservationAsyncProcessor,
    private val customerService: CustomerService,
    private val redisUtil: RedisUtil,
    private val redisTemplate: RedisTemplate<String, String>,
) {
    // 예약을 데이터베이스에 저장하고 비동기 처리
    @Transactional
    fun enqueueAttempt(userId: UUID, request: AttemptRequest): AttemptResponse {
        val customer = customerService.findById(userId)
            ?: throw IllegalArgumentException("존재하지 않는 사용자입니다.")

        require(!customer.isPenaltyActive(LocalDateTime.now())) {
            "현재 예약이 제한된 사용자입니다."
        }

        // 예약 인원 검증
        require(request.guestCount >= 1) {
            "예약 인원은 1명 이상이어야 합니다."
        }

        val timeSlot = timeSlotRepository.findByDateAndTime(request.date, request.time)
            ?: throw IllegalArgumentException("존재하지 않는 시간대입니다.")

        val timeSlotId = requireNotNull(timeSlot.id) {
            "타임슬롯 ID가 생성되지 않았습니다."
        }

        // Redis 중복 진입 체크 (1차 방어) - Redis 살아있으면 DB 조회 전에 먼저 차단
        try {
            val existingRank = redisUtil.zRank(
                RedisUtil.queueKey(timeSlotId.toString()),
                userId.toString(),
            )

            require(existingRank == null) {
                "이미 대기열에 있습니다."
            }
        } catch (e: DataAccessException) {
            // Redis 장애 시 DB 체크로 fallback
            log.warn("Redis 연결 실패 - DB에서 중복 진입 체크로 전환: {}", e.message)
        }

        // CANCELED 상태가 아닌 기존 예약 존재 여부 검사 - Redis 장애 시에도 이 체크가 최종 방어선 역할을 함
        val isDuplicate = reservationRepository.existsByUserIdAndTimeSlotAndStatusNot(
            userId,
            timeSlot,
            ReservationStatus.CANCELED,
        )

        require(!isDuplicate) {
            "이미 처리 중이거나 완료된 예약이 있습니다."
        }

        val reservation = Reservation(
            userId = userId,
            timeSlot = timeSlot,
            guestCount = request.guestCount,
            status = ReservationStatus.PENDING,
        )

        // DB에 먼저 저장 - Redis보다 DB를 먼저 저장해야 Redis 장애 시에도 데이터 유실이 없음
        val savedReservation = reservationRepository.save(reservation)
        val reservationId = requireNotNull(savedReservation.id) {
            "예약 ID가 생성되지 않았습니다."
        }

        // 비동기 작업을 데이터베이스 저장이 끝난 이후에 시작
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    // Redis 대기열 추가 + TTL 설정 (DB 커밋 이후 실행 → 싱크 불일치 방지)
                    addToRedisQueue(
                        RedisUtil.queueKey(timeSlotId.toString()),
                        userId.toString(),
                        reservationId,
                    )

                    asyncProcessor.processReservation(
                        reservationId,
                        timeSlotId,
                        request.guestCount,
                    )
                }
            },
        )

        return AttemptResponse(reservationId)
    }

    // 현재 상태 응답 - 내 순번이 몇번인지 폴링
    @Transactional(readOnly = true)
    fun getAttemptStatus(attemptId: UUID): StatusResponse {
        val reservation = reservationRepository.findByIdOrNull(attemptId)
            ?: throw IllegalArgumentException("존재하지 않는 예약 시도입니다.")

        val status = requireNotNull(reservation.status) {
            "예약 상태가 존재하지 않습니다."
        }

        // CONFIRMED / CANCELED 이면 대기열 순번 필요 없음
        if (status != ReservationStatus.PENDING) {
            return StatusResponse(status, null, null)
        }

        val timeSlot = requireNotNull(reservation.timeSlot) {
            "예약의 타임슬롯이 존재하지 않습니다."
        }

        val timeSlotId = requireNotNull(timeSlot.id) {
            "타임슬롯 ID가 생성되지 않았습니다."
        }

        val reservationUserId = requireNotNull(reservation.userId) {
            "예약 사용자 ID가 존재하지 않습니다."
        }

        // Redis에서 현재 순번 조회
        val queueKey = RedisUtil.queueKey(timeSlotId.toString())
        val userIdStr = reservationUserId.toString()

        return try {
            var rank = redisUtil.zRank(queueKey, userIdStr)

            if (rank == null) {
                // Redis에 없으면 장애로 유실된 것 → DB 기준으로 복구 후 재조회
                log.warn("Redis 대기열에 유저 없음 - DB 기준 복구 시도: userId={}", userIdStr)
                recoverQueueFromDB(timeSlotId)
                rank = redisUtil.zRank(queueKey, userIdStr)
            }

            // rank는 0-based → 유저에게 보여줄 땐 1-based로 변환
            val displayRank = rank?.plus(1)

            // 예상 대기 시간 = 내 앞 순번 수 * 1건당 평균 처리 시간(초) / 60
            val estimatedWaitMinutes = rank?.let {
                (it * AVG_PROCESS_SECONDS) / 60
            }

            StatusResponse(status, displayRank, estimatedWaitMinutes)
        } catch (e: DataAccessException) {
            // Redis 완전 장애 시 순번 없이 상태만 반환
            log.error("Redis 연결 실패 - 순번 없이 상태만 반환: {}", e.message)
            StatusResponse(status, null, null)
        }
    }

    // Redis 장애 시 DB 기준으로 대기열 복구 (Source of Truth = DB)
    // DB의 PENDING 레코드를 createdAt 순으로 읽어서 Redis Sorted Set 재구성
    fun recoverQueueFromDB(timeSlotId: UUID) {
        log.info("Redis 대기열 복구 시작: timeSlotId={}", timeSlotId)

        val pendings = reservationRepository
            .findByTimeSlotIdAndStatusOrderByCreatedAt(timeSlotId, ReservationStatus.PENDING)

        if (pendings.isEmpty()) {
            log.info("복구할 대기열 없음: timeSlotId={}", timeSlotId)
            return
        }

        val queueKey = RedisUtil.queueKey(timeSlotId.toString())

        // 기존 키 완전 삭제 후 재구성 (완전 복구 방식)
        redisTemplate.delete(queueKey)

        pendings.forEachIndexed { index, reservation ->
            val reservationUserId = requireNotNull(reservation.userId) {
                "예약 사용자 ID가 존재하지 않습니다."
            }

            val reservationId = requireNotNull(reservation.id) {
                "예약 ID가 생성되지 않았습니다."
            }

            redisUtil.zAdd(queueKey, reservationUserId.toString(), index.toDouble())
            redisUtil.set(
                RedisUtil.waitingKey(reservationUserId.toString()),
                reservationId.toString(),
                QUEUE_TTL,
            )
        }

        log.info("Redis 대기열 복구 완료: {}건", pendings.size)
    }

    // Redis 대기열 추가 + TTL 설정
    private fun addToRedisQueue(queueKey: String, userIdStr: String, reservationId: UUID) {
        try {
            // Sorted Set에 추가 (score = 현재 시각 ms → 진입 순서 보장)
            redisUtil.zAdd(queueKey, userIdStr, System.currentTimeMillis().toDouble())

            // 복구용 데이터 저장 (브라우저 꺼도 순번 유지)
            redisUtil.set(
                RedisUtil.waitingKey(userIdStr),
                reservationId.toString(),
                QUEUE_TTL,
            )
        } catch (e: DataAccessException) {
            // Redis 장애 시 로그만 남김 → DB가 Source of Truth이므로 데이터 유실 없음
            log.error("Redis 대기열 추가 실패 (DB에는 저장됨): userId={}, error={}", userIdStr, e.message)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(QueueService::class.java)

        // 1건당 평균 처리 시간 (초) - 예상 대기 시간 계산에 사용
        private const val AVG_PROCESS_SECONDS = 3L

        // 대기열 TTL - 10분 안에 처리 안 되면 자동 만료
        private val QUEUE_TTL: Duration = Duration.ofMinutes(10)
    }
}