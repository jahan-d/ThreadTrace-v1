package com.threadtrace.lot;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LotLineageRepository
        extends JpaRepository<GarmentLot, Long>,
                LotLineageRepositoryCustom {
}
