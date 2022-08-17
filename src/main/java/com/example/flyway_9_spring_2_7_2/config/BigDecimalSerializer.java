package com.example.flyway_9_spring_2_7_2.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalSerializer extends JsonSerializer<BigDecimal> {

    @Override
    public Class<BigDecimal> handledType() {
        return BigDecimal.class;
    }

    @Override
    public void serialize(BigDecimal value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeNumber(value.setScale(2, RoundingMode.HALF_UP));
    }
}