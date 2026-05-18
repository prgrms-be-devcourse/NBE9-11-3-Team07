package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.reservation.entity.Reservation
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.repository.ReservationRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.ZoneId

@Primary
@Component
class DynamicReleaseScheduler(
    private val taskScheduler: TaskScheduler,
    private val reservationRepository: ReservationRepository,
    private val releaseStockService: ReleaseStockService,
) : ReleaseScheduler, ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        reservationRepository.findAllByStatus(ReservationStatus.CANCEL_PENDING)
            .forEach { schedule(it) }
    }

    override fun schedule(reservation: Reservation) {
        val id = reservation.id
            ?: throw NoSuchElementException("예약 ID가 없습니다.")
        val releaseAt = reservation.releaseAt
            ?: throw NoSuchElementException("releaseAt이 없습니다.")

        taskScheduler.schedule(
            { releaseStockService.releaseStock(id) },
            releaseAt.atZone(SEOUL_ZONE_ID).toInstant()
        )
    }

    companion object {
        private val SEOUL_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
