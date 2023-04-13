package io.github.aaejo.institutionfinder.finder;

import java.io.IOException;
import java.net.URI;

import org.apache.hc.core5.net.URIBuilder;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;

import io.github.aaejo.institutionfinder.messaging.producer.InstitutionsProducer;
import io.github.aaejo.messaging.records.Institution;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * USA-specific InstitutionFinder implementation utilizing NCES's College Navigator service.
 * 
 * @author Omri Harary
 */
@Slf4j
public class USAInstitutionFinder implements InstitutionFinder {

    private final InstitutionsProducer institutionsProducer;
    private final Connection registryConnection;
    private final RetryTemplate retryTemplate;

    private Counter institutionCounter;

    public static final String[] STATES = { "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "HI",
            "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH",
            "NJ", "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA",
            "WV", "WI", "WY", "AS", "FM", "GU", "MH", "MP", "PW", "PR", "VI" };

    public static final String PROGRAMS = "38.0104+" +  // Applied and Professional Ethics
                                          "38.0103+" +  // Ethics
                                          "38.0102+" +  // Logic
                                          "38.0101+" +  // Philosophy
                                          "38.0199";    // Philosophy, Other

    public USAInstitutionFinder(InstitutionsProducer institutionsProducer, Connection registryConnection,
            RetryTemplate retryTemplate) {
        this.institutionsProducer = institutionsProducer;
        this.registryConnection = registryConnection;
        this.retryTemplate = retryTemplate;
    }

    public USAInstitutionFinder(InstitutionsProducer institutionsProducer, Connection registryConnection,
            RetryTemplate retryTemplate, MeterRegistry registry) {
        this(institutionsProducer, registryConnection, retryTemplate);

        institutionCounter = Counter
                .builder("jds.institution-finder.institutions")
                .tag("country", "usa")
                .register(registry);
    }

    /**
     * Produce institutions from College Navigator, using the program codes
     * specified in {@code USAInstitutionFinder.PROGRAMS}.
     */
    @Async
    @Override
    public void produceInstitutions() {
        log.info("Producing institutions for {} US states and/or territories", STATES.length);

        for (String state : STATES) {
            produceStateInstitutions(state);
        }

        log.info("Done");
    }

    /**
     * Produce institutions for a single US state or territory.
     *
     * Results are loaded from College Navigator. If a page of results fails to
     * load, it will be retried according to the instance's RetryTemplate.
     * If the retries also fail, the next page will be tried unless it is known that
     * no next page exists.
     * If two consecutive pages fail, the process will end for the state. Any
     * previous institutions will still have been produced.
     *
     * @param state the state (or territory) to find institutions for.
     */
    public void produceStateInstitutions(String state) {
        log.info("Producing for state = {}", state);

        int pageNum = 1;
        int pageLimit = 0; // Total number of results pages. 0 if unknown.
        boolean hasNextPage = false;

        do {
            // 1. Attempt to get results page
            Document resultsPage = getResultsPage(state, pageNum);

            if (resultsPage == null // 2. If getting the results page failed, and
                && ((pageLimit != 0 && pageNum < pageLimit) // 2.1. either the current page is within the known page limit
                    || (pageLimit == 0))) { // 2.2 or the page limit is unknown (eg when the first page failed to load)
                log.warn("Failed to load page {} of results, attempting next page.", pageNum);

                // 2.3. Then move onto the next page and attempt to get that instead 
                pageNum++;
                resultsPage = getResultsPage(state, pageNum);
            }

            // 3. If trying the next page also failed, stop processing this state.
            if (resultsPage == null) {
                log.error("Results page loading failing consistently, not continuing with this state.");
                return;
            }

            Element resultsTableBody = resultsPage
                    .getElementById("ctl00_cphCollegeNavBody_ucResultsMain_tblResults").firstElementChild();
            Element pagingControls = resultsPage
                    .getElementById("ctl00_cphCollegeNavBody_ucResultsMain_divPagingControls");

            if (resultsTableBody == null) {
                log.info("No results on page");
                hasNextPage = false;
                continue;
            }

            if (pageLimit == 0) { // If pageLimit is unknown, let's figure it out
                if (pagingControls.text().equals("Showing All Results")) {
                    pageLimit = 1;
                } else {
                    String[] pagingControlsTextTokens = pagingControls.text().split(" ");
                    String finalToken = pagingControlsTextTokens[pagingControlsTextTokens.length - 1];
                    try {
                        pageLimit = Integer.parseInt(finalToken);
                    } catch (NumberFormatException e) {
                        log.debug("Failed to determine page limit from paging controls. Checked token = {}",
                                finalToken);
                    }
                }
            }

            if ((pageLimit != 0 && pageNum < pageLimit) // Page limit is known and current page is within it
                    || pagingControls.selectFirst(":containsOwn(Next Page »)") != null) { // Backup check if next page button exists
                log.info("Another page of results exists");
                hasNextPage = true;
                pageNum++;
            } else {
                log.info("Final page of results reached");
                hasNextPage = false;
            }

            Elements results = resultsTableBody.select(".resultsW, .resultsY");
            log.info("{} results on page", results.size());

            for (Element result : results) {
                Element schoolInfoLink = result
                                        .child(1) // 0 = info button, 1 = school page link, 2 = add button
                                        .getElementsByAttribute("href")
                                        .first();
                String schoolName = schoolInfoLink.text();
                String schoolId = new URIBuilder(URI.create(schoolInfoLink.absUrl("href")))
                        .getQueryParams().stream()
                        .filter(p -> p.getName().equals("id"))
                        .findFirst().get().getValue();

                log.debug("{} id = {}", schoolName, schoolId);

                Institution institution = getInstitutionDetails(schoolName, schoolId);
                if (institution != null) {
                    institutionsProducer.send(institution);
                    institutionCounter.increment();
                }
            }
        } while (hasNextPage);
    }

    /**
     * Get information on an institution by querying College Navigator for a school
     * ID. Will use the instance's RetryTemplate for retrying the request if it
     * fails.
     *
     * @param schoolName    name of the institution being queried for
     * @param schoolId      College Navigator ID for the institution being queried for
     * @return              a complete Institution record, or null if unable to load the page
     */
    public Institution getInstitutionDetails(String schoolName, String schoolId) {
        Document infoPage = retryTemplate.execute(
                // Retryable part
                ctx -> {
                    try {
                        return registryConnection
                                .newRequest()
                                .data("id", schoolId)
                                .get();
                    } catch (IOException e) {
                        log.error("Failed to fetch details page for {}. May retry.", schoolName, e);
                        // Rethrowing as RuntimeException for retry handling
                        throw new RuntimeException(e);
                    }
                },
                // Recovery part
                ctx -> {
                    log.info("Max retries exceeded for fetching details page for {}", schoolName);
                    // If we exceed max retries, return null
                    return null;
                });

        if (infoPage == null) {
            return null;
        }

        String address = infoPage.selectFirst(".headerlg").parent().textNodes().get(0).text();
        String website = "https://" + infoPage.selectFirst(":containsOwn(Website:)").siblingElements().first().text();

        return new Institution(schoolName, "USA", address, website);
    }

    /**
     * Get a College Naviagtor search results page for a certain state. Will use the
     * instance's RetryTemplate for retrying the request if it fails.
     *
     * @param state 2-letter state abbreviation
     * @param page  page number of results to fetch
     * @return      page contents as a Jsoup Document, or null
     */
    private Document getResultsPage(String state, int page) {
        Document resultsPage = retryTemplate.execute(
                // Retryable part
                ctx -> {
                    try {
                        return registryConnection
                                .newRequest()
                                .data("p", PROGRAMS)
                                .data("s", state)
                                .data("pg", Integer.toString(page))
                                .get();
                    } catch (IOException e) {
                        log.error("Failed to connect to College Navigator with state = {}. May retry.", state, e);
                        // Rethrowing as RuntimeException for retry handling
                        throw new RuntimeException(e);
                    }
                },
                // Recovery part
                ctx -> {
                    log.info("Max retries exceeded for connecting to College Navigator with state = {}", state);
                    // If we exceed max retries, return null
                    return null;
                });

        return resultsPage;
    }
}
