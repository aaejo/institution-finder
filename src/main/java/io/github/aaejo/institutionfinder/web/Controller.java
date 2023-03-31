package io.github.aaejo.institutionfinder.web;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.aaejo.institutionfinder.finder.InstitutionFinder;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Omri Harary
 */
@Slf4j
@RestController
@Profile("default")
public class Controller {

    private final InstitutionFinder institutionFinder;

    public Controller(InstitutionFinder institutionFinder) {
        this.institutionFinder = institutionFinder;
    }

    @PostMapping("/start")
    public void startFinding() {
        log.info("Received request to begin producing institutions.");
        institutionFinder.produceInstitutions();
    }
}
