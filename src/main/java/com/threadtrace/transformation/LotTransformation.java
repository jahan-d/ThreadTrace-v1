package com.threadtrace.transformation;

import com.threadtrace.lot.QuantityUnit;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "lot_transformations")
public class LotTransformation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private LotTransformationType transformationType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @Column(nullable = false, updatable = false, length = 100)
    private String performedBy;

    @Column(length = 1000, updatable = false)
    private String notes;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal wasteQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private QuantityUnit wasteUnit;

    protected LotTransformation() {
    }

    public LotTransformation(
            LotTransformationType transformationType,
            LocalDateTime occurredAt,
            String performedBy,
            String notes,
            BigDecimal wasteQuantity,
            QuantityUnit wasteUnit
    ) {
        this.transformationType = transformationType;
        this.occurredAt = occurredAt;
        this.performedBy = performedBy;
        this.notes = notes;
        this.wasteQuantity = wasteQuantity;
        this.wasteUnit = wasteUnit;
    }

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public LotTransformationType getTransformationType() {
        return transformationType;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getWasteQuantity() {
        return wasteQuantity;
    }

    public QuantityUnit getWasteUnit() {
        return wasteUnit;
    }
}
