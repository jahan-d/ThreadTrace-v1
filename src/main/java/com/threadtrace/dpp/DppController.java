package com.threadtrace.dpp;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dpp")
@Validated
@Tag(
        name = "Digital Product Passport",
        description = "Public APIs for consumer and auditor product traceability passport & QR verification"
)
public class DppController {

    private final DppService dppService;

    public DppController(DppService dppService) {
        this.dppService = dppService;
    }

    @GetMapping("/{lotNumber}")
    @Operation(summary = "Get Digital Product Passport by garment lot number")
    public ResponseEntity<DppResponse> getPassport(@PathVariable String lotNumber) {

        DppResponse response = dppService.getPassportByLotNumber(lotNumber);

        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/{lotNumber}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Get QR Code image linking to the public Digital Product Passport")
    public ResponseEntity<byte[]> getQrCode(
            @PathVariable String lotNumber,
            @RequestParam(defaultValue = "300") int width,
            @RequestParam(defaultValue = "300") int height
    ) {

        byte[] qrBytes = dppService.generateQrCode(lotNumber, width, height);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(qrBytes.length);
        headers.setCacheControl("public, max-age=86400");

        return new ResponseEntity<>(qrBytes, headers, HttpStatus.OK);
    }
}
