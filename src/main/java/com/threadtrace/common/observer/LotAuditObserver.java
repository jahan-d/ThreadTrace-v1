package com.threadtrace.common.observer;

import com.threadtrace.lot.event.LotCreatedEvent;
import com.threadtrace.transformation.event.LotTransformationCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Audit and notification observer that listens to domain application events
 * only after the enclosing database transaction successfully commits.
 */
@Component
public class LotAuditObserver {

    private static final Logger log = LoggerFactory.getLogger(LotAuditObserver.class);

    private final AtomicInteger createdEventCount = new AtomicInteger(0);
    private final AtomicInteger transformationCompletedEventCount = new AtomicInteger(0);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLotCreated(LotCreatedEvent event) {
        createdEventCount.incrementAndGet();
        log.info("[Observer: AFTER_COMMIT] Garment lot created: id={}, lotNumber={}, status={}, quantity={} {}",
                event.lotId(), event.lotNumber(), event.status(), event.quantity(), event.unit());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransformationCompleted(LotTransformationCompletedEvent event) {
        transformationCompletedEventCount.incrementAndGet();
        log.info("[Observer: AFTER_COMMIT] Lot transformation completed: id={}, type={}, inputs={}, outputs={}, waste={} {}",
                event.transformationId(), event.transformationType(), event.inputLotIds(),
                event.outputLotIds(), event.wasteQuantity(), event.wasteUnit());
    }

    public int getCreatedEventCount() {
        return createdEventCount.get();
    }

    public int getTransformationCompletedEventCount() {
        return transformationCompletedEventCount.get();
    }

    public void resetCounts() {
        createdEventCount.set(0);
        transformationCompletedEventCount.set(0);
    }
}
