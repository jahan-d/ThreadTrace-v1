package com.threadtrace.dpp;

import com.threadtrace.lot.LotStatus;
import com.threadtrace.lot.QuantityUnit;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(
        name = "DppResponse",
        description = "Digital Product Passport (DPP) public model for garment supply-chain traceability"
)
public record DppResponse(

        @Schema(description = "Business lot number", example = "LOT-2026-0001")
        String lotNumber,

        @Schema(description = "Current lifecycle status")
        LotStatus status,

        @Schema(description = "Product description", example = "Organic Cotton T-Shirt Batch")
        String description,

        @Schema(description = "Current available quantity", example = "500.0000")
        BigDecimal quantity,

        @Schema(description = "Unit of measure", example = "PIECES")
        QuantityUnit unit,

        @Schema(description = "Public supplier information (privacy-safe, excluding email/phone)")
        PublicSupplierInfo supplier,

        @Schema(description = "Audit trail of lifecycle events")
        List<PublicEventInfo> eventHistory,

        @Schema(description = "Upstream provenance lot nodes")
        List<PublicProvenanceNode> upstreamLots,

        @Schema(description = "Upstream transformation processes that produced or shaped this lot")
        List<PublicTransformationNode> transformations,

        @Schema(description = "Environmental footprint evaluation")
        FootprintInfo environmentalFootprint,

        @Schema(description = "Available third-party certifications/evidence")
        List<String> certifications,

        @Schema(description = "Direct URL to fetch this Digital Product Passport", example = "http://localhost:8080/api/dpp/LOT-2026-0001")
        String passportUrl,

        @Schema(description = "Direct URL to fetch the QR Code image for this passport", example = "http://localhost:8080/api/dpp/LOT-2026-0001/qr")
        String qrCodeUrl

) {

    @Schema(description = "Publicly viewable supplier details")
    public record PublicSupplierInfo(
            String name,
            String country,
            String address
    ) {}

    @Schema(description = "Public timeline event")
    public record PublicEventInfo(
            String eventType,
            String description,
            LocalDateTime occurredAt
    ) {}

    @Schema(description = "Public provenance lot node")
    public record PublicProvenanceNode(
            Long id,
            String lotNumber,
            LotStatus status,
            BigDecimal quantity,
            QuantityUnit unit
    ) {}

    @Schema(description = "Public transformation node")
    public record PublicTransformationNode(
            Long id,
            String transformationType,
            LocalDateTime occurredAt,
            BigDecimal wasteQuantity,
            QuantityUnit wasteUnit
    ) {}

    @Schema(description = "Environmental footprint roll-up metrics")
    public record FootprintInfo(
            boolean available,
            String status,
            String carbonFootprintKgCo2e,
            String waterConsumptionLiters,
            String message
    ) {}
}
