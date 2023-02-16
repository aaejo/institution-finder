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
    // public static final String[] STATES = { "AL", "NY", "AS" };
    public static final String[] STATES = { "AS", "FM", "GU", "MH", "MP", "PW", "PR", "VI" };
    public static final String PROGRAMS = "38.0104+" +
                                          "38.0103+" +
                                          "38.0102+" +
                                          "38.0101+" +
                                          "38.0199";

    public USAInstitutionFinder(InstitutionsProducer institutionsProducer, URL registryURL) {
        this.institutionsProducer = institutionsProducer;
        this.registryURL = registryURL;
    }

    @Override
    public void produceInstitutions() {
        log.info("Let's try this...");

        for (String state : STATES) {
            int pageCount = 1;
            boolean hasNextPage = true;

            while (hasNextPage) {
                Document page;
                try {
                    page = Jsoup
                            .connect(registryURL.toString())
                            .data("p", PROGRAMS)
                            .data("s", state)
                            .data("pg", Integer.toString(pageCount))
                            .get();
                } catch(IOException e) {
                    log.error("Failed to connect to College Navigator with state = " + state, e);
                    continue;
                }
                log.info("State " + state);

                Element resultsTable = page.getElementById("ctl00_cphCollegeNavBody_ucResultsMain_tblResults");
                Element resultsTableBody = resultsTable.firstElementChild();
                Element pagingControls = page.getElementById("ctl00_cphCollegeNavBody_ucResultsMain_divPagingControls");

                if (resultsTableBody == null) {
                    log.info("No results on page");
                    hasNextPage = false;
                    continue;
                } else if (pagingControls.selectFirst(":containsOwn(Next Page »)") != null) {
                    log.info("Another page of results exists");
                    hasNextPage = true;
                    pageCount++;
                } else {
                    log.info("Final page of results reached");
                    hasNextPage = false;
                }

                Elements results = resultsTableBody.select(".resultsW, .resultsY");
                log.info(results.size() + " results on page");

                for (Element result : results) {
                    Element schoolElement = result.child(1); // 0 = info button, 1 = school page link, 2 = add button
                    Element schoolInfoLinkElement = schoolElement.getElementsByAttribute("href").first();
                    String schoolInfoQuery = schoolInfoLinkElement.attr("href");
                    String schoolName = schoolInfoLinkElement.text();

                    log.info(schoolName + " " + schoolInfoQuery);

                    Document infoPage;
                    try {
                        infoPage = Jsoup
                                    .connect(registryURL.toString() + schoolInfoQuery)
                                    .get();
                    } catch (IOException e) {
                        log.error("Failed to get details page for " + schoolName, e);
                        continue;
                    }

                    String address = infoPage.selectFirst(".headerlg").parent().textNodes().get(0).text();
                    String website = infoPage.selectFirst(":containsOwn(Website:)").siblingElements().first().text();

                    institutionsProducer.send(new Institution(schoolName, "USA", address, website));
                }
            }
        }

        log.info("Done");
    }
}
