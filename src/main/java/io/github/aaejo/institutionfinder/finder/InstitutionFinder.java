package io.github.aaejo.institutionfinder.finder;

import org.springframework.scheduling.annotation.Async;

/**
 * Base interface for international InstitutionFinder implementations
 */
public interface InstitutionFinder {

    /**
     * Produce institutions in an implementation-specific manner.
     */
    @Async
    public void produceInstitutions();
}
