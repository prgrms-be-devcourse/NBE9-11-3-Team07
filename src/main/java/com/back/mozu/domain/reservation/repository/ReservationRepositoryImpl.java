package com.back.mozu.domain.reservation.repository;

import com.back.mozu.domain.reservation.entity.Reservation;
import com.back.mozu.domain.reservation.entity.ReservationStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.back.mozu.domain.reservation.entity.QReservation.reservation;
import static com.back.mozu.domain.reservation.entity.QTimeSlot.timeSlot;

@RequiredArgsConstructor
public class ReservationRepositoryImpl implements ReservationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Reservation> findAllWithFilters(
            LocalDate date,
            LocalTime time,
            String status,
            Pageable pageable) {

        // 1. 데이터 조회
        // 힌트: queryFactory.selectFrom(), join().fetchJoin(), where(), offset(), limit(), fetch()
        List<Reservation> result = queryFactory
                .selectFrom(reservation)
                .join(reservation.timeSlot, timeSlot).fetchJoin()
                .where(dateEq(date), timeEq(time), statusEq(status))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 2. 전체 개수 조회 (페이지네이션용)
        // 힌트: queryFactory.select(reservation.count()).from(reservation).where().fetchOne()
        Long total = queryFactory
                .select(reservation.count())
                .from(reservation)
                .where(dateEq(date), timeEq(time), statusEq(status))
                .fetchOne();

        total = total != null ? total : 0L;

        // 3. Page로 감싸서 반환
        // 힌트: new PageImpl<>(result, pageable, total)
        return new PageImpl<>(
                result,
                pageable,
                total
        );
    }

    // date 조건 (null이면 무시)
    private BooleanExpression dateEq(LocalDate date) {
        return date != null ? timeSlot.date.eq(date) : null;
    }

    // time 조건 (null이면 무시)
    private BooleanExpression timeEq(LocalTime time) {
        return time != null ? timeSlot.time.eq(time) : null;
    }

    // 수정
    private BooleanExpression statusEq(String status) {
        return status != null ? reservation.status.eq(ReservationStatus.valueOf(status)) : null;
    }

}