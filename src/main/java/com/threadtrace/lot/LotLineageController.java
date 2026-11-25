package com.threadtrace.lot;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/garment-lots")
@Validated
@Tag(
    name = "Garment Lot Lineage",
    description = "APIs for traversing garment lot supply-chain lineage"
)
public class LotLineageController {

    private final LotLineageService lotLineageService;

    public LotLineageController(LotLineageService lotLineageService) {
        this.lotLineageService = lotLineageService;
    }

    @GetMapping("/{id}/lineage")
    @Operation(summary = "Get garment lot lineage")
    public ResponseEntity<LotLineageResponse> getLineage(
            @PathVariable Long id,
            @RequestParam(defaultValue = "BOTH") LineageDirection direction) {

        LotLineageResponse response = lotLineageService.getLineage(id, direction);

        return ResponseEntity.ok(response);
    }
}
