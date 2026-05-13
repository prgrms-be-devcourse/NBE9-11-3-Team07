package com.back.mozu.domain.reservation.service;

import com.back.mozu.domain.reservation.dto.WaitingRoomResponseDto;
import com.back.mozu.domain.reservation.entity.Reservation;
import com.back.mozu.domain.reservation.entity.ReservationStatus;
import com.back.mozu.domain.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WaitingRoomService {
    private final ReservationRepository reservationRepository;

    public WaitingRoomResponseDto getMyWaiting(UUID userId){
        Optional<Reservation> reservation = reservationRepository.findByUserIdAndStatus(
                userId, ReservationStatus.PENDING  // String → Enum으로 변경
        );
        return reservation.map(r -> WaitingRoomResponseDto.builder()
                .reservationId(r.getId())
                .status(r.getStatus().name())  // Enum → String으로 변환
                .queueNumber(null)
                .estimatedWaitMinutes(null)
                .build()
        ).orElse(null);
    }
}
