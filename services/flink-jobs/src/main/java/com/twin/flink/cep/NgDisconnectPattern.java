package com.twin.flink.cep;

import com.twin.flink.model.FurnaceReading;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import java.time.Duration;

public class NgDisconnectPattern {
    public static Pattern<FurnaceReading, FurnaceReading> build() {
        return Pattern.<FurnaceReading>begin("ng")
            .where(new IterativeCondition<FurnaceReading>() {
                @Override
                public boolean filter(FurnaceReading r,
                        IterativeCondition.Context<FurnaceReading> ctx) throws Exception {
                    return r.getEvent() != null && r.getEvent() == 6;
                }
            })
            .times(1)
            .within(Duration.ofSeconds(30));
    }
}
