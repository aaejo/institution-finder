package io.github.aaejo.institutionfinder.finder.configuration;

import java.net.URI;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Omri Harary
 */
@ConfigurationProperties(prefix = "aaejo.jds.institution-finder")
public record InstitutionFinderProperties(SupportedCountry country, URI registryUrl, Optional<String> file) {
}
