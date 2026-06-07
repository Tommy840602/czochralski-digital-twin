package com.twin.flink.cep;

import com.twin.flink.model.FurnaceReading;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import java.time.Duration;

public class HeaterOverheatPattern {
    public static Pattern<FurnaceReading, FurnaceReading> build() {
        return Pattern.<FurnaceReading>begin("overheat")
            .where(new IterativeCondition<FurnaceReading>() {
                @Override
                public boolean filter(FurnaceReading r,
                        IterativeCondition.Context<FurnaceReading> ctx) throws Exception {
                    return r.getHeaterTemp() != null && r.getHeaterTemp() > 1450.0;
                }
            })
            .times(3)
            .within(Duration.ofSeconds(60));
    }
}
