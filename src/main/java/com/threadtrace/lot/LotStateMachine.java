package com.threadtrace.lot;

import com.threadtrace.event.LotEventType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Component
public class LotStateMachine {

    public LotStatus determineNextStatus(
            LotStatus currentStatus,
            LotEventType eventType
    ) {

        if (currentStatus == LotStatus.ARCHIVED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Archived lots cannot receive new events"
            );
        }

        return switch (eventType) {

            case RECEIVED -> {
                requireStatus(
                    currentStatus,
                    Set.of(LotStatus.RAW_MATERIAL)
                );
                yield LotStatus.RAW_MATERIAL;
            }

            case CUT, SEW, WASH, DYE -> {
                requireStatus(
                    currentStatus,
                    Set.of(
                        LotStatus.RAW_MATERIAL,
                        LotStatus.IN_PRODUCTION
                    )
                );
                yield LotStatus.IN_PRODUCTION;
            }

            case FINISH -> {
                requireStatus(
                    currentStatus,
                    Set.of(LotStatus.IN_PRODUCTION)
                );
                yield LotStatus.QUALITY_CHECK;
            }

            case QUALITY_CHECK_PASSED -> {
                requireStatus(
                    currentStatus,
                    Set.of(LotStatus.QUALITY_CHECK)
                );
                yield LotStatus.COMPLETED;
            }

            case QUALITY_CHECK_FAILED -> {
                requireStatus(
                    currentStatus,
                    Set.of(LotStatus.QUALITY_CHECK)
                );
                yield LotStatus.IN_PRODUCTION;
            }

            case PACK, SHIP -> {
                requireStatus(
                    currentStatus,
                    Set.of(LotStatus.COMPLETED)
                );
                yield LotStatus.COMPLETED;
            }

            case ARCHIVE -> LotStatus.ARCHIVED;
        };
    }

    private void requireStatus(
            LotStatus currentStatus,
            Set<LotStatus> allowedStatuses
    ) {
        if (!allowedStatuses.contains(currentStatus)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "This event is not allowed from the current lot status: "
                    + currentStatus
            );
        }
    }
}
