package io.github.aaejo.institutionfinder.finder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.aaejo.institutionfinder.messaging.producer.InstitutionsProducer;
import io.github.aaejo.messaging.records.Institution;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON file-based InstitutionFinder implementation.
 * 
 * @author Omri Harary
 */
@Slf4j
public class JsonInstitutionFinder implements InstitutionFinder {

    private final String country;
    private final InstitutionsProducer institutionsProducer;
    private final ObjectMapper objectMapper;
    private final Optional<String> file;

    private Counter institutionCounter;

    public JsonInstitutionFinder(String country, InstitutionsProducer institutionsProducer, ObjectMapper objectMapper, Optional<String> file) {
        this.country = country.toLowerCase();
        this.institutionsProducer = institutionsProducer;
        this.objectMapper = objectMapper;
        this.file = file;
    }

    public JsonInstitutionFinder(String country, InstitutionsProducer institutionsProducer, ObjectMapper objectMapper,
            Optional<String> file, MeterRegistry registry) {
        this(country, institutionsProducer, objectMapper, file);

        institutionCounter = Counter
                .builder("jds.institution-finder.institutions")
                .tag("country", this.country)
                .register(registry);
    }

    /**
     * Produce institutions from a configured JSON file or one in the classpath
     * associated with this institution finder instance's country.
     */
    @Async
    @Override
    public void produceInstitutions() {
        String defaultFileName = country + ".json";
        Path dataFile = null;
        boolean useClasspathData = true;
        if (file.isPresent()) {
            dataFile = Paths.get(file.get());

            if (Files.isDirectory(dataFile)) {
                // If a directory was provided, append default file name to it
                dataFile = dataFile.resolve(defaultFileName);
                log.warn(
                        "Configured institutions file path was a directory, will attempt to find a file named {} in that directory",
                        defaultFileName);
            }

            if (Files.exists(dataFile, LinkOption.NOFOLLOW_LINKS)) {
                log.info("Using {} as institution data source", dataFile.toString());
                useClasspathData = false;
            } else {
                log.warn("Configured institutions file path not found, will use file named {} on the classpath instead",
                        defaultFileName);
                useClasspathData = true;
            }
        } else {
            log.info("Using {} on the classpath as institution data source", defaultFileName);
        }

        try (InputStream inputStream = useClasspathData ? new ClassPathResource(defaultFileName).getInputStream()
                : Files.newInputStream(dataFile);) {
            produceInstitutionsJson(inputStream);
        } catch (IOException e) {
            log.error("An error occurred processing the institutions JSON file", e);
        }
    }

    /**
     * Produce institutions from a JSON stream. Contents must be in an array.
     *
     * @param institutionsJsonStream    input stream to produce from
     * @throws IOException              thrown by JsonParser or ObjectMapper
     */
    public void produceInstitutionsJson(InputStream institutionsJsonStream) throws IOException {
        // Using streaming JsonParser instead of ObjectMapper directly to reduce memory
        // overhead of loading entire file at once.
        try (JsonParser parser = objectMapper.getFactory().createParser(institutionsJsonStream)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalStateException("Content not in an array");
            }

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                Institution institution = this.objectMapper.readValue(parser, Institution.class);
                institutionsProducer.send(institution);
                institutionCounter.increment();
            }
        }
    }

}
