package com.twin.alarm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "oee_target")
@Getter
@Setter
@NoArgsConstructor
public class OeeTarget {

    @Id
    @Column(name = "furnace_id", length = 16)
    private String furnaceId;

    @Column(name = "target_length_mm", nullable = false)
    private Double targetLengthMm;

    @Column(name = "target_cycle_hours", nullable = false)
    private Double targetCycleHours;

    @Column(name = "target_gr_mean", nullable = false)
    private Double targetGrMean;

    @Column(name = "quality_threshold_pct", nullable = false)
    private Double qualityThresholdPct;
}