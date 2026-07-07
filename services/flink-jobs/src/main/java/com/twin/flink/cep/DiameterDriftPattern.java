package com.twin.flink.cep;

import com.twin.flink.model.FurnaceReading;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import java.time.Duration;

public class DiameterDriftPattern {
    public static Pattern<FurnaceReading, FurnaceReading> build() {
        return Pattern.<FurnaceReading>begin("drift")
            .where(new IterativeCondition<FurnaceReading>() {
                @Override
                public boolean filter(FurnaceReading r,
                        IterativeCondition.Context<FurnaceReading> ctx) throws Exception {
                    if (r.getDiameter() == null || r.getDiameterTarget() == null) return false;
                    return Math.abs(r.getDiameter() - r.getDiameterTarget()) > 5.0;
                }
            })
            .times(3)
            .within(Duration.ofSeconds(30));
    }
}
