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

    /** 製程階段（MELT / NECK4 / CROWN / BODY / HOLDING…）。各階段分佈不同，baseline 必須分開建。 */
    @Column(name = "operation_mode", nullable = false, length = 30)
    private String operationMode;

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

    /** σ 寬鬆度倍數，預設 1.0（標準 3σ 管制），使用者可調整放寬/收緊管制界限 */
    @Column(name = "sigma_multiplier", nullable = false)
    private Double sigmaMultiplier = 1.0;

    @PrePersist
    @PreUpdate
    void onSave() {
        if (calculatedAt == null) calculatedAt = Instant.now();
        if (sigmaMultiplier == null) sigmaMultiplier = 1.0;
    }
}
