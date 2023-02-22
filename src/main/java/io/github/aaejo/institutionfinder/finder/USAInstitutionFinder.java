package io.github.aaejo.institutionfinder.finder;

import java.io.IOException;
import java.util.Arrays;

import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import io.github.aaejo.institutionfinder.messaging.producer.InstitutionsProducer;
import io.github.aaejo.messaging.records.Institution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class USAInstitutionFinder implements InstitutionFinder {

    private final InstitutionsProducer institutionsProducer;
    private final Connection registryConnection;

    public static final String[] STATES = { "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "HI", "ID",
            "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
            "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV",
            "WI", "WY", "AS", "FM", "GU", "MH", "MP", "PW", "PR", "VI" };

    public static final String PROGRAMS = "38.0104+" +  // Applied and Professional Ethics
                                          "38.0103+" +  // Ethics
                                          "38.0102+" +  // Logic
                                          "38.0101+" +  // Philosophy
                                          "38.0199";    // Philosophy, Other

    public USAInstitutionFinder(InstitutionsProducer institutionsProducer, Connection registryConnection) {
        this.institutionsProducer = institutionsProducer;
        this.registryConnection = registryConnection;
    }

    @Override
    public void produceInstitutions() {
        log.info("Producing institutions for {} US states and/or territories", STATES.length);

        for (String state : STATES) {
            produceStateInstitutions(state);
        }

        log.info("Done");
    }

    public void produceStateInstitutions(String state) {
        log.info("Producing for state = {}", state);

        int pageNum = 1;
        int pageLimit = 0; // Total number of results pages. 0 if unknown.
        boolean hasNextPage = false;

        do {
            // 1. Attempt to get results page (with single retry).
            Document resultsPage = getResultsPage(state, pageNum);

            if (resultsPage == null // 2. If getting the results page failed, and
                && ((pageLimit != 0 && pageNum < pageLimit) // 2.1. either the current page is within the known page limit
                    || (pageLimit == 0))) { // 2.2 or the page limit is unknown (eg when the first page failed to load).
                
                // 2.3. Then move onto the next page and attempt to get that instead (with single retry).
                pageNum++;
                resultsPage = getResultsPage(state, pageNum);
            }

            // 3. If trying the next page also failed, stop processing this state.
            if (resultsPage == null) {
                return;
            }

            Element resultsTableBody = resultsPage
                    .getElementById("ctl00_cphCollegeNavBody_ucResultsMain_tblResults").firstElementChild();
            Element pagingControls = resultsPage.getElementById("ctl00_cphCollegeNavBody_ucResultsMain_divPagingControls");

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
                        log.debug("Failed to determine page limit from paging controls. Checked token = {}", finalToken);
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
                String schoolId = Arrays.stream(schoolInfoLink.attr("href").split("&"))
                        .filter(p -> p.startsWith("id") || p.startsWith("?id"))
                        .findFirst().get().split("=")[1];

                log.info("{} id = {}", schoolName, schoolId);

                Institution institution = getInstitutionDetails(schoolName, schoolId);
                if (institution != null) {
                    institutionsProducer.send(institution);
                }
            }
        } while (hasNextPage);
    }

    public Institution getInstitutionDetails(String schoolName, String schoolId) {
        Document infoPage = null;
        boolean retry = false;

        do {
            try {
                infoPage = registryConnection
                            .newRequest()
                            .data("id", schoolId)
                            .get();

                if (retry) {
                    retry = false; // Stop retrying when succeeded after retry
                }
            } catch (IOException e) {
                if (retry) {
                    log.error("Failed to get details page for {} on retry", schoolName, e);
                    return null;
                } else {
                    log.error("Failed to get details page for {}, will retry", schoolName, e);
                    retry = true;
                }
            }
        } while (retry);

        /*
         * This should never happen but if somehow we've reached here with a null infoPage even
         * after the retry and early return, just return null for safety.
         */
        if (infoPage == null) {
            return null;
        }

        String address = infoPage.selectFirst(".headerlg").parent().textNodes().get(0).text();
        String website = infoPage.selectFirst(":containsOwn(Website:)").siblingElements().first().text();

        return new Institution(schoolName, "USA", address, website);
    }

    /**
     * Get a College Naviagtor search results page for a certain state. Will retry once.
     *
     * @param state 2-letter state abbreviation
     * @param page  page number of results to fetch
     * @return      page contents as a Jsoup Document, or null
     */
    private Document getResultsPage(String state, int page) {
        Document resultsPage = null;
        boolean retry = false;

        do {
            try {
                resultsPage = registryConnection
                        .newRequest()
                        .data("p", PROGRAMS)
                        .data("s", state)
                        .data("pg", Integer.toString(page))
                        .get();

                if (retry) {
                    retry = false; // Stop retrying when succeeded after retry
                }
            } catch (IOException e) {
                if (retry) {
                    log.error("Failed to connect to College Navigator with state = {} on retry", state, e);
                    retry = false;
                } else {
                    log.error("Failed to connect to College Navigator with state = {}, will retry", state, e);
                    retry = true;
                }
            }
        } while (retry);

        return resultsPage;
    }
}
