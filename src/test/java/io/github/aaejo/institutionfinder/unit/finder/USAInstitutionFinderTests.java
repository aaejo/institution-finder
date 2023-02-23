package io.github.aaejo.institutionfinder.unit.finder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import io.github.aaejo.institutionfinder.finder.USAInstitutionFinder;
import io.github.aaejo.institutionfinder.messaging.producer.InstitutionsProducer;
import io.github.aaejo.messaging.records.Institution;

public class USAInstitutionFinderTests {

    private final InstitutionsProducer institutionsProducer = mock(InstitutionsProducer.class);
    private final Connection connection = mock(Connection.class, RETURNS_DEEP_STUBS);

    private final USAInstitutionFinder usaFinder = new USAInstitutionFinder(institutionsProducer, connection);

    /**
     * Successful case of fetching institution details.
     */
    @Test
    void getInstitutionDetails_success_correctDetailsReturned() throws IOException {
        setupSchoolInfoMock();
        String schoolName = "University of California-Berkeley";
        String schoolId = "110635";
        Institution expected = new Institution(
                "University of California-Berkeley",
                "USA",
                "200 California Hall, Berkeley, California 94720",
                "www.berkeley.edu/");

        Institution actual = usaFinder.getInstitutionDetails(schoolName, schoolId);

        assertEquals(expected, actual);
    }

    /**
     * When there is a failure to load the institution's information page, even after
     * retrying, the method returns null.
     */
    @Test
    void getInstitutionDetails_failToGetPage_returnNull() throws IOException {
        setupSchoolInfoExceptionMock();
        String schoolName = "University of California-Berkeley";
        String schoolId = "110635";

        Institution actual = usaFinder.getInstitutionDetails(schoolName, schoolId);

        assertNull(actual);
    }

    /**
     * When the institution's information page fails to load, it can recover by retrying.
     */
    @Test
    void getInstitutionDetails_failToGetPageFirstTime_succeedsAfterRetry() throws IOException {
        when(connection
                .newRequest()
                .data(eq("id"), anyString())
                .get())
            .thenThrow(new IOException()) // Throw first time
            .thenReturn(Jsoup.parse(      // Succeed second time
                    new File("src/test/resources/collegenavigator/110635.html"),
                    "UTF-8",
                    "https://nces.ed.gov/collegenavigator/"));
        String schoolName = "University of California-Berkeley";
        String schoolId = "110635";
        Institution expected = new Institution(
                "University of California-Berkeley",
                "USA",
                "200 California Hall, Berkeley, California 94720",
                "www.berkeley.edu/");

        Institution actual = usaFinder.getInstitutionDetails(schoolName, schoolId);

        assertEquals(expected, actual);
    }

    /**
     * A state/territory may not have any institutions that match the programs filter set.
     * In this case, an empty page of results will load. This may also occur if an additional
     * page of results that does not exist is attempted to be loaded. In either case, the
     * empty page can be handled gracefully.
     */
    @Test
    void produceStateInstitutions_noResults_sucessfulProcessing() throws IOException {
        setupSchoolInfoMock();
        setupASMock();

        usaFinder.produceStateInstitutions("AS");

        verify(institutionsProducer, never()).send(any(Institution.class));
    }

    /**
     * Successful case for a single page of search results.
     */
    @Test
    void produceStateInstitutions_singlePageResults_sucessfulProcessing() throws IOException {
        setupSchoolInfoMock();
        setupALMock();

        usaFinder.produceStateInstitutions("AL");

        verify(institutionsProducer, times(9)).send(any(Institution.class));
    }

    /**
     * When a single page of search results fails to load for whatever reason (even after retrying) the
     * next page will be "probed" even if it is unknown whether another page exists. If loading the next
     * page succeeds, in the case that there was only one page of results, that page will be empty. This
     * situation can be handled gracefully.
     */
    @Test
    void produceInstitutions_singlePageResultsFailsAndNextPageLoads_handledGracefully() throws IOException {
        setupSchoolInfoMock();
        when(connection
                .newRequest()
                .data("p", USAInstitutionFinder.PROGRAMS)
                .data("s", "AL")
                .data("pg", "1")
                .get())
            .thenThrow(new IOException());
        // When the next page is probed (because limit is unknown), sucessfully load a page with no results
        when(connection
                .newRequest()
                .data("p", USAInstitutionFinder.PROGRAMS)
                .data("s", "AL")
                .data("pg", "2")
                .get())
            .thenReturn(Jsoup.parse(
                    new File("src/test/resources/collegenavigator/AS.html"), // Using AS as exemplar of page with no results
                    "UTF-8",
                    "https://nces.ed.gov/collegenavigator/"));

        usaFinder.produceStateInstitutions("AL");

        verify(institutionsProducer, never()).send(any(Institution.class));
    }

    /**
     * The same scenario as the previous test, however in this case the next page "probed" also does not load.
     * In this situation, it's treated as any two pages failing consecutively, and processing is aborted
     * for the state.
     */
    @Test
    void produceInstitutions_singlePageResultsFailsAndNextPageFails_handledGracefully() throws IOException {
        setupSchoolInfoMock();
        when(connection
                .newRequest()
                .data(eq("p"), eq(USAInstitutionFinder.PROGRAMS))
                .data(eq("s"), eq("AL"))
                .data(eq("pg"), anyString())
                .get())
            .thenThrow(new IOException());

        usaFinder.produceStateInstitutions("AL");

        verify(institutionsProducer, never()).send(any(Institution.class));
    }

    /**
     * In the processing of a page of results, if getting the details for one of the institutions
     * somehow fails, the remaining will still be sent successfully.
     */
    @Test
    void produceStateInstitutions_getInstitutionDetailsFailsOnce_remainingStillSent() throws IOException {
        setupSchoolInfoMock();
        setupALMock();
        // One of the IDs will throw, rest will still work
        when(connection
                .newRequest()
                .data(eq("id"), eq("100751"))
                .get())
            .thenThrow(new IOException());

        usaFinder.produceStateInstitutions("AL");

        verify(institutionsProducer, times(8)).send(any(Institution.class));
    }

    /**
     * Successful processing of multi-page results when no errors occur.
     */
    @Test
    void produceStateInstitutions_multiPageResults_sucessfulProcessing() throws IOException {
        setupSchoolInfoMock();
        setupNYMock();

        usaFinder.produceStateInstitutions("NY");

        verify(institutionsProducer, times(77)).send(any(Institution.class));
    }

    /**
     * When a failure occurs in loading the first page of results, even after retry, the next page will be
     * "probed" even if it is unknown that there is a second page. From there, the process continues
     * without further issue.
     */
    @Test
    void produceStateInstitutions_firstMultiPageResultsFails_remainingStillSent() throws IOException {
        setupSchoolInfoMock();
        setupNYMock();
        // 1st page of 6 will fail to load, representing 15 results
        when(connection
                .newRequest()
                .data("p", USAInstitutionFinder.PROGRAMS)
                .data("s", "NY")
                .data("pg", "1")
                .get())
            .thenThrow(new IOException());

        usaFinder.produceStateInstitutions("NY");

        verify(institutionsProducer, times(62)).send(any(Institution.class));
    }

    /**
     * When a failure occurs in loading a page of results, even after retry, the processing will continue to the
     * next page when it is known that there are additional pages to scrape. From there, the process continues
     * without further issue.
     */
    @Test
    void produceStateInstitutions_secondMultiPageResultsFails_remainingStillSent() throws IOException {
        setupSchoolInfoMock();
        setupNYMock();
        // 2nd page of 6 will fail to load, representing 15 results
        when(connection
                .newRequest()
                .data("p", USAInstitutionFinder.PROGRAMS)
                .data("s", "NY")
                .data("pg", "2")
                .get())
            .thenThrow(new IOException());

        usaFinder.produceStateInstitutions("NY");

        verify(institutionsProducer, times(62)).send(any(Institution.class));
    }

    /**
     * When loading a page fails (after retrying), the next page is attempted. If the next page also fails,
     * the system will back off and abort the current state to prevent overwhelming the server. This has no
     * effect on the institutions collected before the failure.
     */
    @Test
    void produceStateInstitutions_thirdAndFourthMultiPageResultsFail_abortedRemaining() throws IOException {
        setupSchoolInfoMock();
        setupNYMock();
        when(connection
                .newRequest()
                .data("p", USAInstitutionFinder.PROGRAMS)
                .data("s", "NY")
                .data("pg", "3")
                .get())
            .thenThrow(new IOException());
        when(connection
                .newRequest()
                .data("p", USAInstitutionFinder.PROGRAMS)
                .data("s", "NY")
                .data("pg", "4")
                .get())
            .thenThrow(new IOException());

        usaFinder.produceStateInstitutions("NY");

        verify(institutionsProducer, times(30)).send(any(Institution.class));
    }

    /**
     * When a failure occurs in loading the final page of results, even after retry, the processing will not
     * attempt to check the next page. Since the previous pages loaded successfully, it is known that no more
     * pages exist, so there is nothing to check.
     */
    @Test
    void produceStateInstitutions_finalMultiPageResultsFails_restSentAndNoMoreChecked() throws IOException {
        setupSchoolInfoMock();
        setupNYMock();
        when(connection
                .newRequest()
                .data("p", USAInstitutionFinder.PROGRAMS)
                .data("s", "NY")
                .data("pg", "6")
                .get())
            .thenThrow(new IOException());

        usaFinder.produceStateInstitutions("NY");

        verify(institutionsProducer, times(75)).send(any(Institution.class));
        // Page 7 was never probed because it's known not to exist (weird formatting because of how deep stubbing works)
        verify(connection
                .newRequest()
                .data("p", USAInstitutionFinder.PROGRAMS)
                .data("s", "NY")
                .data("pg", "7"),
            never())
                .get();
    }

    /**
     * If a results page fails to load the first time, but loads successfully after a retry, the processing
     * can continue without any issues.
     */
    @Test
    void produceStateInstitutions_fifthMultiPageResultFailsFirstTime_succeedsAfterRetry() throws IOException {
        setupSchoolInfoMock();
        setupNYMock();
        when(connection
                .newRequest()
                .data("p", USAInstitutionFinder.PROGRAMS)
                .data("s", "NY")
                .data("pg", "5")
                .get())
            .thenThrow(new IOException())
            .thenReturn(Jsoup.parse(
                new File("src/test/resources/collegenavigator/NY5.html"),
                "UTF-8",
                "https://nces.ed.gov/collegenavigator/"));

        usaFinder.produceStateInstitutions("NY");

        verify(institutionsProducer, times(77)).send(any(Institution.class));
    }

    /**
     * Setup mock response for querying school info. Using UC Berkeley in all cases.
     */
    private void setupSchoolInfoMock() throws IOException {
        when(connection
                .newRequest()
                .data(eq("id"), anyString())
                .get())
            .thenReturn(Jsoup.parse(
                    new File("src/test/resources/collegenavigator/110635.html"),
                    "UTF-8",
                    "https://nces.ed.gov/collegenavigator/"));
    }

    /**
     * Setup mock to throw IOException when querying school info.
     */
    private void setupSchoolInfoExceptionMock() throws IOException {
        when(connection
                .newRequest()
                .data(eq("id"), anyString())
                .get())
            .thenThrow(new IOException());
    }

    /**
     * Setup mock response for querying AS institutions.
     */
    private void setupASMock() throws IOException {
        when(connection
                .newRequest()
                .data("p", USAInstitutionFinder.PROGRAMS)
                .data("s", "AS")
                .data("pg", "1")
                .get())
            .thenReturn(Jsoup.parse(
                    new File("src/test/resources/collegenavigator/AS.html"),
                    "UTF-8",
                    "https://nces.ed.gov/collegenavigator/"));
    }

    /**
     * Setup mock response for querying AL institutions.
     */
    private void setupALMock() throws IOException {
        when(connection
                .newRequest()
                .data("p", USAInstitutionFinder.PROGRAMS)
                .data("s", "AL")
                .data("pg", "1")
                .get())
            .thenReturn(Jsoup.parse(
                    new File("src/test/resources/collegenavigator/AL.html"),
                    "UTF-8",
                    "https://nces.ed.gov/collegenavigator/"));
    }

    /**
     * Setup mock response for querying NY institutions. Multiple pages of results
     */
    private void setupNYMock() throws IOException {
        for (int i = 1; i <= 6; i++) {
            when(connection
                    .newRequest()
                    .data("p", USAInstitutionFinder.PROGRAMS)
                    .data("s", "NY")
                    .data("pg", Integer.toString(i))
                    .get())
                .thenReturn(Jsoup.parse(
                        new File("src/test/resources/collegenavigator/NY" + i + ".html"),
                        "UTF-8",
                        "https://nces.ed.gov/collegenavigator/"));
        }
    }
}
