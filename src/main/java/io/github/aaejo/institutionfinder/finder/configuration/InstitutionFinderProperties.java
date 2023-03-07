package io.github.aaejo.institutionfinder.finder.configuration;

import java.net.URL;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aaejo.jds.institution-finder")
public record InstitutionFinderProperties(SupportedCountry country, URL registryURL) {
}
