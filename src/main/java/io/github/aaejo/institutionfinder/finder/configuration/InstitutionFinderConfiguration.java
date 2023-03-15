package io.github.aaejo.institutionfinder.finder.configuration;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.aaejo.institutionfinder.finder.InstitutionFinder;
import io.github.aaejo.institutionfinder.finder.JsonInstitutionFinder;
import io.github.aaejo.institutionfinder.finder.USAInstitutionFinder;
import io.github.aaejo.institutionfinder.messaging.producer.InstitutionsProducer;

@Configuration
@EnableConfigurationProperties(InstitutionFinderProperties.class)
public class InstitutionFinderConfiguration {

    @Autowired
    private InstitutionFinderProperties properties;

    @Autowired
    private InstitutionsProducer institutionsProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    public InstitutionFinder institutionFinder() {
        if (properties.country() == SupportedCountry.USA) {
            if (properties.registryURL() == null) {
                throw new UnsatisfiedDependencyException(
                        null,
                        "institutionFinder",
                        "registryURL",
                        "USA Institution Finder must have a registry URL.");
            }

            // All configuration for the Jsoup client for the USA Finder can be done here before injection.
            Connection connection = Jsoup.connect(properties.registryURL().toString());

            RetryTemplate retryTemplate = RetryTemplate.builder()
                                            .maxAttempts(2) // Initial + 1 retry
                                            .fixedBackoff(2000L)
                                            .build();

            return new USAInstitutionFinder(institutionsProducer, connection, retryTemplate);
        } else {
            return new JsonInstitutionFinder(properties.country().name(), institutionsProducer, objectMapper);
        }
    }
}
