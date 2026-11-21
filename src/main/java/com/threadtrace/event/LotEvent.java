package com.threadtrace.event;

import com.threadtrace.lot.GarmentLot;
import com.threadtrace.lot.LotStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "lot_events")
public class LotEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "garment_lot_id", nullable = false)
    private GarmentLot garmentLot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LotEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private LotStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private LotStatus newStatus;

    @Column(nullable = false, length = 150)
    private String performedBy;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    protected LotEvent() {
        // Required by JPA
    }

    public LotEvent(
            GarmentLot garmentLot,
            LotEventType eventType,
            LotStatus previousStatus,
            LotStatus newStatus,
            String performedBy,
            String description,
            LocalDateTime occurredAt
    ) {
        this.garmentLot = garmentLot;
        this.eventType = eventType;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.performedBy = performedBy;
        this.description = description;
        this.occurredAt = occurredAt;
    }

    @PrePersist
    protected void onCreate() {
        recordedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public GarmentLot getGarmentLot() {
        return garmentLot;
    }

    public LotEventType getEventType() {
        return eventType;
    }

    public LotStatus getPreviousStatus() {
        return previousStatus;
    }

    public LotStatus getNewStatus() {
        return newStatus;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }
}
