package com.threadtrace.transformation;

import com.threadtrace.lot.QuantityUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LotTransformationRequest(

        @NotNull
        LotTransformationType transformationType,

        @NotNull
        @PastOrPresent
        LocalDateTime occurredAt,

        @NotBlank
        @Size(max = 1000)
        String notes,

        @NotNull
        @DecimalMin("0.0")
        @Digits(integer = 15, fraction = 4)
        BigDecimal wasteQuantity,

        @NotNull
        QuantityUnit wasteUnit,

        @NotEmpty
        @Valid
        List<InputLotRequest> inputs,

        @NotEmpty
        @Valid
        List<OutputLotRequest> outputs

) {

    public record InputLotRequest(

            @NotNull
            Long lotId,

            @NotNull
            @DecimalMin(value = "0.0", inclusive = false)
            @Digits(integer = 15, fraction = 4)
            BigDecimal quantity,

            @NotNull
            QuantityUnit unit

    ) {}

    public record OutputLotRequest(

            @NotBlank
            @Size(max = 50)
            String lotNumber,

            @NotNull
            Long supplierId,

            @Size(max = 500)
            String description,

            @NotNull
            @DecimalMin(value = "0.0", inclusive = false)
            @Digits(integer = 15, fraction = 4)
            BigDecimal quantity,

            @NotNull
            QuantityUnit unit

    ) {}
}
