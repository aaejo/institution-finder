package io.github.aaejo;

import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

public class App {

    // public static final String[] STATES = { "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "HI", "ID",
    //         "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
    //         "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV",
    //         "WI", "WY", "AS", "FM", "GU", "MH", "MP", "PW", "PR", "VI" };
    public static final String[] STATES = { "AL", "NY", "AS" };
    public static final String PROGRAMS = "38.0104+" +
                                          "38.0103+" +
                                          "38.0102+" +
                                          "38.0101+" +
                                          "38.0199";

    public static void main(String[] args) throws IOException {
        System.out.println("Let's try this...");

        for (String state : STATES) {
            Document page = Jsoup
                    .connect("https://nces.ed.gov/collegenavigator/?s=" + state
                            + "&p=" + PROGRAMS)
                    .get();
            System.out.println("State " + state);

            Element resultsInfo = page.getElementById("ctl00_cphCollegeNavBody_ucResultsMain_divMsg");
            Element resultsTable = page.getElementById("ctl00_cphCollegeNavBody_ucResultsMain_tblResults");
            Element pagingControls = page.getElementById("ctl00_cphCollegeNavBody_ucResultsMain_divPagingControls");

            Element resultsTableBody = resultsTable.firstElementChild();

            if (resultsTableBody == null) {
                System.out.println("No results on page");
                continue;
            }
            // List<TextNode> resultsInfoText = resultsInfo.textNodes();

            Elements results = resultsTableBody.select(".resultsW, .resultsY");
            System.out.println(results.size() + " results on page");

            for (Element result : results) {
                Element schoolElement = result.child(1); // 0 = info button, 1 = school page link, 2 = add button

            }

            if (pagingControls.ownText().equals("Showing All Results")) {
                System.out.println("Only one page of results");
            } else {
                System.out.println("Probably more than one page of results");
            }
        }

        System.out.println("Done");
    }
}
