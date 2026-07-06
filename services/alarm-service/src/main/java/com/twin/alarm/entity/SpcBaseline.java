package com.twin.alarm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "spc_baseline")
@Getter
@Setter
@NoArgsConstructor
public class SpcBaseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false)
    private Long id;

    @Column(name = "furnace_id", nullable = false, length = 16)
    private String furnaceId;

    @Column(name = "param_name", nullable = false, length = 32)
    private String paramName;

    @Column(name = "mean", nullable = false)
    private Double mean;

    @Column(name = "std_dev", nullable = false)
    private Double stdDev;

    @Column(name = "ucl_3sigma", nullable = false)
    private Double ucl3sigma;

    @Column(name = "lcl_3sigma", nullable = false)
    private Double lcl3sigma;

    @Column(name = "ucl_2sigma", nullable = false)
    private Double ucl2sigma;

    @Column(name = "lcl_2sigma", nullable = false)
    private Double lcl2sigma;

    @Column(name = "ucl_1sigma", nullable = false)
    private Double ucl1sigma;

    @Column(name = "lcl_1sigma", nullable = false)
    private Double lcl1sigma;

    @Column(name = "sample_size", nullable = false)
    private Integer sampleSize;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        if (calculatedAt == null) calculatedAt = Instant.now();
    }
}
