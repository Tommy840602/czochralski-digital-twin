package com.twin.flink.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twin.flink.model.FurnaceReading;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

public class FurnaceDeserializer implements DeserializationSchema<FurnaceReading> {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public FurnaceReading deserialize(byte[] message) {
        try {
            return mapper.readValue(message, FurnaceReading.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isEndOfStream(FurnaceReading r) { return false; }

    @Override
    public TypeInformation<FurnaceReading> getProducedType() {
        return TypeInformation.of(FurnaceReading.class);
    }
}
