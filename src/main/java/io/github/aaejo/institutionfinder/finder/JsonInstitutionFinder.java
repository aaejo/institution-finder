package io.github.aaejo.institutionfinder.finder;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.aaejo.institutionfinder.messaging.producer.InstitutionsProducer;
import io.github.aaejo.messaging.records.Institution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonInstitutionFinder implements InstitutionFinder {

    private final String country;
    private final InstitutionsProducer institutionsProducer;
    private final ObjectMapper objectMapper;

    public JsonInstitutionFinder(String country, InstitutionsProducer institutionsProducer, ObjectMapper objectMapper) {
        this.country = country.toLowerCase();
        this.institutionsProducer = institutionsProducer;
        this.objectMapper = objectMapper;
    }

    @Override
    public void produceInstitutions() {

        String fileName = country + ".json";
        try (InputStream inputStream = new ClassPathResource(fileName).getInputStream();) {
            produceInstitutionsJson(inputStream);
        } catch (IOException e) {
            log.error("An error occurred processing the institutions JSON file", e);
        }
    }

    public void produceInstitutionsJson(InputStream institutionsFileStream) throws IOException {

        // Using streaming JsonParser instead of ObjectMapper directly to reduce memory
        // overhead of loading entire file at once.
        try (JsonParser parser = objectMapper.getFactory().createParser(institutionsFileStream)) {

            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalStateException("Content not in an array");
            }

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                Institution institution = this.objectMapper.readValue(parser, Institution.class);
                institutionsProducer.send(institution);
            }
        }
    }

}
