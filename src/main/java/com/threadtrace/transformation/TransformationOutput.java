package com.threadtrace.transformation;

import com.threadtrace.lot.GarmentLot;
import com.threadtrace.lot.QuantityUnit;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "transformation_outputs")
public class TransformationOutput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transformation_id", nullable = false, updatable = false)
    private LotTransformation transformation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false, updatable = false)
    private GarmentLot outputLot;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private QuantityUnit unit;

    protected TransformationOutput() {
    }

    public TransformationOutput(
            LotTransformation transformation,
            GarmentLot outputLot,
            BigDecimal quantity,
            QuantityUnit unit
    ) {
        this.transformation = transformation;
        this.outputLot = outputLot;
        this.quantity = quantity;
        this.unit = unit;
    }

    public Long getId() {
        return id;
    }

    public LotTransformation getTransformation() {
        return transformation;
    }

    public GarmentLot getOutputLot() {
        return outputLot;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public QuantityUnit getUnit() {
        return unit;
    }
}
