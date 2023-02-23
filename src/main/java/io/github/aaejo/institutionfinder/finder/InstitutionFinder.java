package io.github.aaejo.institutionfinder.finder;

/**
 * Base interface for international InstitutionFinder implementations
 */
public interface InstitutionFinder {

    /**
     * Produce institutions in an implementation-specific manner.
     */
    public void produceInstitutions();
}
