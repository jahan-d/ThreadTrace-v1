package com.threadtrace.transformation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lot-transformations")
@Tag(
        name = "Lot Transformations",
        description = "APIs for recording garment lot split, merge and process transformations"
)
public class LotTransformationController {

    private final LotTransformationService lotTransformationService;

    public LotTransformationController(
            LotTransformationService lotTransformationService
    ) {
        this.lotTransformationService = lotTransformationService;
    }

    @PostMapping
    @Operation(summary = "Create a lot transformation with input and output lineage")
    public ResponseEntity<LotTransformationResponse> createTransformation(
            @Valid @RequestBody LotTransformationRequest request
    ) {
        LotTransformationResponse response =
                lotTransformationService.createTransformation(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
