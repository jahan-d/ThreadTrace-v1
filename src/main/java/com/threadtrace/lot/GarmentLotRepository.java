package com.threadtrace.lot;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GarmentLotRepository extends JpaRepository<GarmentLot, Long> {

    boolean existsByLotNumber(String lotNumber);

    boolean existsByLotNumberAndIdNot(String lotNumber, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lot FROM GarmentLot lot WHERE lot.id IN :ids ORDER BY lot.id")
    List<GarmentLot> findAllByIdInForUpdate(@Param("ids") List<Long> ids);
}
