package com.back.mozu.domain.reservation.repository

import com.back.mozu.domain.reservation.entity.*
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import java.time.LocalTime

class ReservationRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : ReservationRepositoryCustom {

    override fun findAllWithFilters(
        date: LocalDate?,
        time: LocalTime?,
        status: String?,
        pageable: Pageable
    ): Page<Reservation> {
        val result = queryFactory
            .selectFrom(QReservation.reservation)
            .join(QReservation.reservation.timeSlot, QTimeSlot.timeSlot).fetchJoin()
            .where(dateEq(date), timeEq(time), statusEq(status))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        val total = queryFactory
            .select(QReservation.reservation.count())
            .from(QReservation.reservation)
            .where(dateEq(date), timeEq(time), statusEq(status))
            .fetchOne() ?: 0L

        return PageImpl(result, pageable, total)
    }

    private fun dateEq(date: LocalDate?): BooleanExpression? =
        date?.let { QTimeSlot.timeSlot.date.eq(it) }

    private fun timeEq(time: LocalTime?): BooleanExpression? =
        time?.let { QTimeSlot.timeSlot.time.eq(it) }

    private fun statusEq(status: String?): BooleanExpression? =
        status?.let { QReservation.reservation.status.eq(ReservationStatus.valueOf(it)) }
}
