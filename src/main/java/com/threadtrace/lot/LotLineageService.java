package com.threadtrace.lot;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LotLineageService {

    private final LotLineageRepository lotLineageRepository;

    public LotLineageService(LotLineageRepository lotLineageRepository) {
        this.lotLineageRepository = lotLineageRepository;
    }

    @Transactional(readOnly = true)
    public LotLineageResponse getLineage(Long rootLotId, LineageDirection direction) {

        if (rootLotId == null || !lotLineageRepository.existsById(rootLotId)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Garment lot not found"
            );
        }

        if (direction == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Lineage direction is required"
            );
        }

        return lotLineageRepository.findLineage(rootLotId, direction);
    }
}
