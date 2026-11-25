package com.threadtrace.transformation;

import com.threadtrace.event.LotEventRequest;
import com.threadtrace.event.LotEventService;
import com.threadtrace.event.LotEventType;
import com.threadtrace.lot.GarmentLot;
import com.threadtrace.lot.GarmentLotRepository;
import com.threadtrace.lot.LotStatus;
import com.threadtrace.lot.QuantityUnit;
import com.threadtrace.supplier.SupplierRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LotTransformationService {

    private static final String SYSTEM_ACTOR = "system";

    /**
     * Unique 64-bit domain key for PostgreSQL advisory transaction lock to serialize
     * graph transformation mutations and prevent concurrent race-condition cycles.
     */
    private static final long LINEAGE_GRAPH_ADVISORY_LOCK_KEY = 4287941209871625412L;

    private final LotTransformationRepository transformationRepository;
    private final TransformationInputRepository inputRepository;
    private final TransformationOutputRepository outputRepository;
    private final GarmentLotRepository garmentLotRepository;
    private final SupplierRepository supplierRepository;
    private final LotEventService lotEventService;
    private final LotCycleValidationService cycleValidationService;

    public LotTransformationService(
            LotTransformationRepository transformationRepository,
            TransformationInputRepository inputRepository,
            TransformationOutputRepository outputRepository,
            GarmentLotRepository garmentLotRepository,
            SupplierRepository supplierRepository,
            LotEventService lotEventService,
            LotCycleValidationService cycleValidationService
    ) {
        this.transformationRepository = transformationRepository;
        this.inputRepository = inputRepository;
        this.outputRepository = outputRepository;
        this.garmentLotRepository = garmentLotRepository;
        this.supplierRepository = supplierRepository;
        this.lotEventService = lotEventService;
        this.cycleValidationService = cycleValidationService;
    }

    @Transactional
    public LotTransformationResponse createTransformation(
            LotTransformationRequest request
    ) {

        // 1. Acquire PostgreSQL transaction-scoped advisory lock to serialize graph-level writes
        transformationRepository.acquireTransactionAdvisoryLock(LINEAGE_GRAPH_ADVISORY_LOCK_KEY);

        // 2. Validate structure, lock input lots (FOR UPDATE), and validate inventory & mass balance
        Map<Long, GarmentLot> inputLots =
                validateTransformation(request);

        // 3. Validate Cycle Prevention (DAG Invariant)
        cycleValidationService.validateNoCycle(inputLots, request.outputs());

        // 4. Determine inherited status
        LotStatus inheritedStatus =
                inputLots.get(request.inputs().get(0).lotId()).getStatus();

        // 5. Save transformation record
        LotTransformation transformation =
                transformationRepository.save(new LotTransformation(
                        request.transformationType(),
                        request.occurredAt(),
                        SYSTEM_ACTOR,
                        request.notes(),
                        request.wasteQuantity(),
                        request.wasteUnit()
                ));

        List<LotTransformationResponse.InputLotSummary> inputSummaries =
                request.inputs().stream()
                        .map(input -> {
                            GarmentLot lot = inputLots.get(input.lotId());

                            return new LotTransformationResponse.InputLotSummary(
                                    lot.getId(),
                                    lot.getLotNumber(),
                                    input.quantity(),
                                    input.unit()
                            );
                        })
                        .toList();

        List<LotTransformationResponse.OutputLotSummary> outputSummaries =
                new java.util.ArrayList<>();

        // 6. Deduct input inventory and save input lineage
        for (LotTransformationRequest.InputLotRequest input : request.inputs()) {

            GarmentLot inputLot = inputLots.get(input.lotId());

            inputLot.setQuantity(
                    inputLot.getQuantity().subtract(input.quantity())
            );

            garmentLotRepository.save(inputLot);

            inputRepository.save(new TransformationInput(
                    transformation,
                    inputLot,
                    input.quantity(),
                    input.unit()
            ));

            lotEventService.appendEvent(
                    inputLot.getId(),
                    new LotEventRequest(
                            LotEventType.TRANSFORMATION_INPUT,
                            request.occurredAt(),
                            "Transformation #" + transformation.getId()
                                    + " (" + request.transformationType() + ") consumed "
                                    + input.quantity() + " " + input.unit()
                    )
            );
        }

        // 7. Create output lots and save output lineage
        for (LotTransformationRequest.OutputLotRequest output : request.outputs()) {

            var supplier = supplierRepository.findById(output.supplierId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Supplier not found: " + output.supplierId()
                    ));

            GarmentLot outputLot = new GarmentLot();

            outputLot.setLotNumber(output.lotNumber());
            outputLot.setSupplier(supplier);
            outputLot.setStatus(inheritedStatus);
            outputLot.setDescription(output.description());
            outputLot.setQuantity(output.quantity());
            outputLot.setUnit(output.unit());

            outputLot = garmentLotRepository.save(outputLot);

            outputSummaries.add(
                    new LotTransformationResponse.OutputLotSummary(
                            outputLot.getId(),
                            outputLot.getLotNumber(),
                            output.quantity(),
                            output.unit()
                    )
            );

            outputRepository.save(new TransformationOutput(
                    transformation,
                    outputLot,
                    output.quantity(),
                    output.unit()
            ));

            lotEventService.appendEvent(
                    outputLot.getId(),
                    new LotEventRequest(
                            LotEventType.TRANSFORMATION_OUTPUT,
                            request.occurredAt(),
                            "Transformation #" + transformation.getId()
                                    + " (" + request.transformationType() + ") created "
                                    + output.quantity() + " " + output.unit()
                    )
            );
        }

        return new LotTransformationResponse(
                transformation.getId(),
                transformation.getTransformationType(),
                transformation.getOccurredAt(),
                transformation.getRecordedAt(),
                transformation.getNotes(),
                transformation.getWasteQuantity(),
                transformation.getWasteUnit(),
                inputSummaries,
                outputSummaries
        );
    }

    @Transactional
    public Map<Long, GarmentLot> validateTransformation(
            LotTransformationRequest request
    ) {

        validateStructure(request);
        validateOutputLots(request);

        List<Long> inputIds = request.inputs()
                .stream()
                .map(LotTransformationRequest.InputLotRequest::lotId)
                .toList();

        Set<Long> uniqueInputIds = new HashSet<>(inputIds);

        if (uniqueInputIds.size() != inputIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Duplicate input lot IDs are not allowed"
            );
        }

        Map<Long, GarmentLot> inputLots = garmentLotRepository
                .findAllByIdInForUpdate(inputIds)
                .stream()
                .collect(Collectors.toMap(
                        GarmentLot::getId,
                        Function.identity()
                ));

        if (inputLots.size() != uniqueInputIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "One or more input lots were not found"
            );
        }

        for (Long lotId : inputIds) {
            GarmentLot lot = inputLots.get(lotId);

            if (lot.getStatus() == LotStatus.ARCHIVED) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Archived lots cannot participate in transformations"
                );
            }

            if (lot.getQuantity() == null || lot.getUnit() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Input lot inventory quantity and unit must be known"
                );
            }
        }

        LotStatus inheritedStatus = inputLots.get(inputIds.get(0)).getStatus();

        for (Long lotId : inputIds) {

            LotStatus currentStatus = inputLots.get(lotId).getStatus();

            if (currentStatus != inheritedStatus) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "All input lots must have the same status"
                );
            }
        }

        validateInventoryAndMassBalance(request, inputLots);

        return inputLots;
    }

    private void validateStructure(LotTransformationRequest request) {

        int inputCount = request.inputs().size();
        int outputCount = request.outputs().size();

        switch (request.transformationType()) {

            case SPLIT -> {
                if (inputCount != 1 || outputCount < 2) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "SPLIT requires exactly one input and at least two outputs"
                    );
                }
            }

            case MERGE -> {
                if (inputCount < 2 || outputCount != 1) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "MERGE requires at least two inputs and exactly one output"
                    );
                }
            }

            case PROCESS -> {
                if (inputCount < 1 || outputCount < 1) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "PROCESS requires at least one input and one output"
                    );
                }
            }
        }

        Set<String> outputLotNumbers = new HashSet<>();

        for (LotTransformationRequest.OutputLotRequest output : request.outputs()) {
            if (!outputLotNumbers.add(output.lotNumber())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate output lot numbers are not allowed"
                );
            }
        }
    }

    private void validateOutputLots(LotTransformationRequest request) {

        for (LotTransformationRequest.OutputLotRequest output : request.outputs()) {

            if (garmentLotRepository.existsByLotNumber(output.lotNumber())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Output lot number already exists: " + output.lotNumber()
                );
            }

            if (!supplierRepository.existsById(output.supplierId())) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier not found: " + output.supplierId()
                );
            }
        }
    }

    private void validateInventoryAndMassBalance(
            LotTransformationRequest request,
            Map<Long, GarmentLot> inputLots
    ) {
        BigDecimal totalInput = BigDecimal.ZERO;
        BigDecimal totalOutput = BigDecimal.ZERO;

        QuantityUnit commonUnit = request.wasteUnit();

        for (LotTransformationRequest.InputLotRequest input : request.inputs()) {

            GarmentLot lot = inputLots.get(input.lotId());

            if (!lot.getUnit().equals(input.unit())
                    || !input.unit().equals(commonUnit)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "All input lots must use the same inventory unit"
                );
            }

            if (lot.getQuantity().compareTo(input.quantity()) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Insufficient inventory for lot: " + lot.getLotNumber()
                );
            }

            totalInput = totalInput.add(input.quantity());
        }

        for (LotTransformationRequest.OutputLotRequest output : request.outputs()) {

            if (!output.unit().equals(commonUnit)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "All output lots must use the same inventory unit"
                );
            }

            totalOutput = totalOutput.add(output.quantity());
        }

        BigDecimal expectedInput = totalOutput.add(request.wasteQuantity());

        if (totalInput.compareTo(expectedInput) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mass balance violation: total input must equal total output plus waste"
            );
        }
    }
}
