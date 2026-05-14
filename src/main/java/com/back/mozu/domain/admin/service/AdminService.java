package com.back.mozu.domain.admin.service;

import com.back.mozu.domain.admin.dto.AdminDto;
import com.back.mozu.domain.admin.dto.AdminReservationDto;
import com.back.mozu.domain.customer.entity.Customer;
import com.back.mozu.domain.customer.repository.CustomerRepository;
import com.back.mozu.domain.reservation.entity.Reservation;
import com.back.mozu.domain.reservation.entity.ReservationStatus;
import com.back.mozu.domain.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final ReservationRepository reservationRepository;
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public Page<AdminReservationDto> getReservations(
            LocalDate date,
            LocalTime time,
            String status,
            Pageable pageable) {

        Page<Reservation> reservations = reservationRepository.findAllWithFilters(date, time, status, pageable);

        List<AdminReservationDto> dtoList = new ArrayList<>();
        List<Reservation> reservationList = reservations.getContent();

        Set<UUID> userIds = reservationList.stream()
                .map(Reservation::getUserId)
                .collect(Collectors.toSet());

        Map<UUID, Customer> customerMap = customerRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(Customer::getId, c -> c));

        for (Reservation reservation : reservationList) {
            Customer customer = customerMap.get(reservation.getUserId());
            dtoList.add(new AdminReservationDto(reservation, customer));
        }

        return new PageImpl<>(
                dtoList, pageable, reservations.getTotalElements()
        );
    }

    @Transactional
    public AdminDto.CancelReservationResponse cancelReservation(
            UUID reservationId,
            AdminDto.CancelReservationRequest request
    ) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));

        if (reservation.getStatus() == ReservationStatus.CANCELED) {
            throw new IllegalStateException("이미 취소된 예약입니다.");
        }

        reservation.cancelReservation("ADMIN_CANCEL");

        return new AdminDto.CancelReservationResponse(
                reservation.getId(),
                reservation.getStatus().name(),
                request.reason(),
                LocalDateTime.now()
        );
    }
}