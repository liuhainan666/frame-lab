package com.framelab.config;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter INSTANT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer framelabJacksonCustomizer() {
        return builder -> builder
                .serializerByType(Instant.class, new InstantSerializer())
                .deserializerByType(Instant.class, new InstantDeserializer());
    }

    static final class InstantSerializer extends JsonSerializer<Instant> {
        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(INSTANT_FORMAT.format(value));
        }
    }

    static final class InstantDeserializer extends JsonDeserializer<Instant> {
        @Override
        public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getText();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return Instant.parse(text);
            } catch (java.time.format.DateTimeParseException ex) {
                return OffsetDateTime.parse(text).toInstant();
            }
        }
    }
}
