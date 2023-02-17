package io.github.aaejo.institutionfinder.finder;

import java.io.IOException;
import java.net.URL;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import io.github.aaejo.institutionfinder.messaging.producer.InstitutionsProducer;
import io.github.aaejo.messaging.records.Institution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class USAInstitutionFinder implements InstitutionFinder {

    private final InstitutionsProducer institutionsProducer;
    private final URL registryURL;

    // public static final String[] STATES = { "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "HI", "ID",
    //         "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
    //         "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV",
    //         "WI", "WY", "AS", "FM", "GU", "MH", "MP", "PW", "PR", "VI" };
    public static final String[] STATES = { "AL", "NY", "AS" };
    public static final String PROGRAMS = "38.0104+" +  // Applied and Professional Ethics
                                          "38.0103+" +  // Ethics
                                          "38.0102+" +  // Logic
                                          "38.0101+" +  // Philosophy
                                          "38.0199";    // Philosophy, Other

    public USAInstitutionFinder(InstitutionsProducer institutionsProducer, URL registryURL) {
        this.institutionsProducer = institutionsProducer;
        this.registryURL = registryURL;
    }

    @Override
    public void produceInstitutions() {
        log.info("Producing institutions for {} US states and/or territories", STATES.length);

        for (String state : STATES) {
            log.info("Producing for state = {}", state);
            int pageNum = 1;
            boolean hasNextPage = false;

            do {
                Document page;
                try {
                    page = Jsoup
                            .connect(registryURL.toString())
                            .data("p", PROGRAMS)
                            .data("s", state)
                            .data("pg", Integer.toString(pageNum))
                            .get();
                } catch (IOException e) {
                    log.error("Failed to connect to College Navigator with state = {}", state, e);
                    continue;
                }

                Element resultsTableBody = page
                        .getElementById("ctl00_cphCollegeNavBody_ucResultsMain_tblResults").firstElementChild();
                Element pagingControls = page.getElementById("ctl00_cphCollegeNavBody_ucResultsMain_divPagingControls");

                if (resultsTableBody == null) {
                    log.info("No results on page");
                    hasNextPage = false;
                    continue;
                } else if (pagingControls.selectFirst(":containsOwn(Next Page »)") != null) {
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
                    String schoolInfoUrl = schoolInfoLink.attr("abs:href");
                    String schoolName = schoolInfoLink.text();

                    log.info("{} {}", schoolName, schoolInfoUrl);

                    Institution institution = getInstitutionDetails(schoolName, schoolInfoUrl);
                    if (institution != null) {
                        institutionsProducer.send(institution);
                    }
                }
            } while (hasNextPage);
        }

        log.info("Done");
    }

    public Institution getInstitutionDetails(String schoolName, String schoolInfoUrl) {

        Document infoPage;
        try {
            infoPage = Jsoup
                        .connect(schoolInfoUrl)
                        .get();
        } catch (IOException e) {
            log.error("Failed to get details page for {}", schoolName, e);
            return null;
        }

        String address = infoPage.selectFirst(".headerlg").parent().textNodes().get(0).text();
        String website = infoPage.selectFirst(":containsOwn(Website:)").siblingElements().first().text();

        return new Institution(schoolName, "USA", address, website);
    }
}
