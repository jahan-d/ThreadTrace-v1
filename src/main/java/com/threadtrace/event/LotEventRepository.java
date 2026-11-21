package com.threadtrace.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LotEventRepository
        extends JpaRepository<LotEvent, Long> {

    List<LotEvent> findByGarmentLot_IdOrderByOccurredAtAscRecordedAtAsc(
            Long garmentLotId
    );
}
