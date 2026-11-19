package com.threadtrace.lot;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GarmentLotRepository extends JpaRepository<GarmentLot, Long> {

    boolean existsByLotNumber(String lotNumber);

    boolean existsByLotNumberAndIdNot(String lotNumber, Long id);

}
