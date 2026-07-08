package com.twin.alarm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "spc_violation")
@IdClass(SpcViolation.PK.class)
@Getter
@Setter
@NoArgsConstructor
public class SpcViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "spc_violation_seq")
    @SequenceGenerator(name = "spc_violation_seq", sequenceName = "spc_violation_id_seq", allocationSize = 1)
    private Long id;

    @Id
    @Column(name = "ts", nullable = false)
    private Instant ts;

    @Column(name = "furnace_id", nullable = false, length = 16)
    private String furnaceId;

    @Column(name = "ingot_id", length = 32)
    private String ingotId;

    @Column(name = "param_name", nullable = false, length = 32)
    private String paramName;

    @Column(name = "rule_id", nullable = false)
    private Integer ruleId;

    @Column(name = "rule_name", nullable = false, length = 96)
    private String ruleName;

    @Column(name = "value", nullable = false)
    private Double value;

    @Column(name = "mean", nullable = false)
    private Double mean;

    @Column(name = "std_dev", nullable = false)
    private Double stdDev;

    @Column(name = "ucl_3sigma")
    private Double ucl3sigma;

    @Column(name = "lcl_3sigma")
    private Double lcl3sigma;

    @Column(name = "severity", nullable = false, length = 8)
    private String severity;

    // 複合主鍵給 hypertable 用
    public static class PK implements Serializable {
        private Long id;
        private Instant ts;

        public PK() {}
        public PK(Long id, Instant ts) { this.id = id; this.ts = ts; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK)) return false;
            PK pk = (PK) o;
            return Objects.equals(id, pk.id) && Objects.equals(ts, pk.ts);
        }

        @Override
        public int hashCode() { return Objects.hash(id, ts); }
    }
}
