package com.threadtrace.transformation;

import com.threadtrace.lot.GarmentLot;
import com.threadtrace.lot.QuantityUnit;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "transformation_inputs")
public class TransformationInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transformation_id", nullable = false, updatable = false)
    private LotTransformation transformation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false, updatable = false)
    private GarmentLot inputLot;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private QuantityUnit unit;

    protected TransformationInput() {
    }

    public TransformationInput(
            LotTransformation transformation,
            GarmentLot inputLot,
            BigDecimal quantity,
            QuantityUnit unit
    ) {
        this.transformation = transformation;
        this.inputLot = inputLot;
        this.quantity = quantity;
        this.unit = unit;
    }

    public Long getId() {
        return id;
    }

    public LotTransformation getTransformation() {
        return transformation;
    }

    public GarmentLot getInputLot() {
        return inputLot;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public QuantityUnit getUnit() {
        return unit;
    }
}
