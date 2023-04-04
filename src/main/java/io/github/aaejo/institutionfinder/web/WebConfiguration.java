package io.github.aaejo.institutionfinder.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author Omri Harary
 */
@Configuration
@EnableAsync
@Profile("default")
public class WebConfiguration {
}
