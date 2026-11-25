package com.threadtrace.dpp;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.threadtrace.event.LotEvent;
import com.threadtrace.event.LotEventRepository;
import com.threadtrace.lot.*;
import com.threadtrace.supplier.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

@Service
public class DppService {

    private final GarmentLotRepository garmentLotRepository;
    private final LotEventRepository lotEventRepository;
    private final LotLineageRepository lotLineageRepository;
    private final String dppBaseUrl;

    public DppService(
            GarmentLotRepository garmentLotRepository,
            LotEventRepository lotEventRepository,
            LotLineageRepository lotLineageRepository,
            @Value("${threadtrace.dpp.base-url:http://localhost:8080/api/dpp}") String dppBaseUrl
    ) {
        this.garmentLotRepository = garmentLotRepository;
        this.lotEventRepository = lotEventRepository;
        this.lotLineageRepository = lotLineageRepository;
        this.dppBaseUrl = dppBaseUrl.endsWith("/") ? dppBaseUrl.substring(0, dppBaseUrl.length() - 1) : dppBaseUrl;
    }

    @Transactional(readOnly = true)
    public DppResponse getPassportByLotNumber(String lotNumber) {

        GarmentLot lot = garmentLotRepository.findByLotNumber(lotNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Garment lot not found: " + lotNumber
                ));

        // 1. Supplier details masked for public privacy (no email, no phone)
        Supplier supplier = lot.getSupplier();
        DppResponse.PublicSupplierInfo publicSupplier = supplier != null
                ? new DppResponse.PublicSupplierInfo(
                supplier.getName(),
                supplier.getCountry(),
                supplier.getAddress()
        )
                : null;

        // 2. Event History
        List<LotEvent> events = lotEventRepository.findByGarmentLot_IdOrderByOccurredAtAscRecordedAtAsc(lot.getId());
        List<DppResponse.PublicEventInfo> publicEvents = events.stream()
                .map(e -> new DppResponse.PublicEventInfo(
                        e.getEventType().name(),
                        e.getDescription(),
                        e.getOccurredAt()
                ))
                .toList();

        // 3. Upstream Lineage & Transformations
        LotLineageResponse lineage = lotLineageRepository.findLineage(lot.getId(), LineageDirection.UPSTREAM);
        List<DppResponse.PublicProvenanceNode> upstreamLots = lineage.lots().stream()
                .map(l -> new DppResponse.PublicProvenanceNode(
                        l.id(),
                        l.lotNumber(),
                        l.status(),
                        l.quantity(),
                        l.unit()
                ))
                .toList();

        List<DppResponse.PublicTransformationNode> transformations = lineage.transformations().stream()
                .map(t -> new DppResponse.PublicTransformationNode(
                        t.id(),
                        t.transformationType() != null ? t.transformationType().name() : null,
                        t.occurredAt(),
                        t.wasteQuantity(),
                        t.wasteUnit()
                ))
                .toList();

        // 4. Environmental Footprint Evaluation (Factual: No fake claims when measurements are absent)
        DppResponse.FootprintInfo footprint = new DppResponse.FootprintInfo(
                false,
                "NOT_AVAILABLE",
                null,
                null,
                "Primary LCA carbon and water measurements have not been recorded for this lot batch."
        );

        // 5. Certifications (empty if none recorded in existing schema)
        List<String> certifications = Collections.emptyList();

        String passportUrl = dppBaseUrl + "/" + lot.getLotNumber();
        String qrCodeUrl = passportUrl + "/qr";

        return new DppResponse(
                lot.getLotNumber(),
                lot.getStatus(),
                lot.getDescription(),
                lot.getQuantity(),
                lot.getUnit(),
                publicSupplier,
                publicEvents,
                upstreamLots,
                transformations,
                footprint,
                certifications,
                passportUrl,
                qrCodeUrl
        );
    }

    @Transactional(readOnly = true)
    public byte[] generateQrCode(String lotNumber, int width, int height) {

        if (!garmentLotRepository.existsByLotNumber(lotNumber)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Garment lot not found: " + lotNumber
            );
        }

        String passportUrl = dppBaseUrl + "/" + lotNumber;

        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(passportUrl, BarcodeFormat.QR_CODE, width, height);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate QR code",
                    e
            );
        }
    }
}
