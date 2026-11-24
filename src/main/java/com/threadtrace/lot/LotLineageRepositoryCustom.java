package com.threadtrace.lot;

public interface LotLineageRepositoryCustom {

    LotLineageResponse findLineage(
            Long rootLotId,
            LineageDirection direction
    );

}
