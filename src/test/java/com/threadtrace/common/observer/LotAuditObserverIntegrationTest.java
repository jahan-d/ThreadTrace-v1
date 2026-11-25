package com.threadtrace.common.observer;

import com.threadtrace.lot.GarmentLotRequest;
import com.threadtrace.lot.GarmentLotService;
import com.threadtrace.lot.LotStatus;
import com.threadtrace.lot.QuantityUnit;
import com.threadtrace.supplier.Supplier;
import com.threadtrace.supplier.SupplierRepository;
import com.threadtrace.transformation.LotTransformationRequest;
import com.threadtrace.transformation.LotTransformationService;
import com.threadtrace.transformation.LotTransformationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class LotAuditObserverIntegrationTest {

    @Autowired
    private GarmentLotService garmentLotService;

    @Autowired
    private LotTransformationService transformationService;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private LotAuditObserver auditObserver;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long supplierId;

    @BeforeEach
    void setUp() {
        auditObserver.resetCounts();

        Supplier supplier = new Supplier();
        supplier.setName("Observer Test Supplier " + System.nanoTime());
        supplier.setCountry("Bangladesh");
        supplier = supplierRepository.saveAndFlush(supplier);
        supplierId = supplier.getId();
    }

    @Test
    @DisplayName("Observer should receive LotCreatedEvent after transaction successfully commits")
    void shouldTriggerObserverAfterLotCreatedCommit() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        GarmentLotRequest request = new GarmentLotRequest(
                "LOT-OBSERVER-" + System.nanoTime(),
                supplierId,
                LotStatus.RAW_MATERIAL,
                "Observer fabric",
                new BigDecimal("300.0000"),
                QuantityUnit.KG
        );

        txTemplate.execute(status -> garmentLotService.createLot(request));

        assertThat(auditObserver.getCreatedEventCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Observer must NOT receive event when transaction rolls back")
    void shouldNotTriggerObserverWhenLotCreationRollsBack() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        GarmentLotRequest request = new GarmentLotRequest(
                "LOT-ROLLBACK-" + System.nanoTime(),
                supplierId,
                LotStatus.RAW_MATERIAL,
                "Rollback fabric",
                new BigDecimal("100.0000"),
                QuantityUnit.KG
        );

        assertThatThrownBy(() ->
                txTemplate.execute(status -> {
                    garmentLotService.createLot(request);
                    throw new RuntimeException("Simulated failure to force transaction rollback");
                })
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Simulated failure");

        // The @TransactionalEventListener(phase = AFTER_COMMIT) must NOT have reacted
        assertThat(auditObserver.getCreatedEventCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Observer should receive LotTransformationCompletedEvent after transformation commits")
    void shouldTriggerObserverAfterTransformationCommit() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        String inputLotNumber = "LOT-TX-IN-" + System.nanoTime();
        String outputLotNumber = "LOT-TX-OUT-" + System.nanoTime();

        var inputLot = garmentLotService.createLot(new GarmentLotRequest(
                inputLotNumber,
                supplierId,
                LotStatus.RAW_MATERIAL,
                "Input fabric",
                new BigDecimal("200.0000"),
                QuantityUnit.KG
        ));

        // Reset count after initial lot creation
        auditObserver.resetCounts();

        LotTransformationRequest transformationRequest = new LotTransformationRequest(
                LotTransformationType.PROCESS,
                LocalDateTime.now(),
                "Spinning process",
                new BigDecimal("10.0000"),
                QuantityUnit.KG,
                List.of(new LotTransformationRequest.InputLotRequest(
                        inputLot.id(),
                        new BigDecimal("200.0000"),
                        QuantityUnit.KG
                )),
                List.of(new LotTransformationRequest.OutputLotRequest(
                        outputLotNumber,
                        supplierId,
                        "Output yarn",
                        new BigDecimal("190.0000"),
                        QuantityUnit.KG
                ))
        );

        txTemplate.execute(status -> transformationService.createTransformation(transformationRequest));

        assertThat(auditObserver.getTransformationCompletedEventCount()).isEqualTo(1);
    }
}
