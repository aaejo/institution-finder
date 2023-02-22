package io.github.aaejo.institutionfinder.unit.finder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

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

    @Test
    void getInstitutionDetails_failToGetPage_returnNull() throws IOException {
        setupSchoolInfoExceptionMock();
        String schoolName = "University of California-Berkeley";
        String schoolId = "110635";

        Institution actual = usaFinder.getInstitutionDetails(schoolName, schoolId);

        assertNull(actual);
    }

    @Test
    void produceStateInstitutions_noResults_sucessfulProcessing() throws IOException {
        setupSchoolInfoMock();
        setupASMock();

        usaFinder.produceStateInstitutions("AS");

        verify(institutionsProducer, never()).send(any(Institution.class));
    }

    @Test
    void produceStateInstitutions_singlePageResults_sucessfulProcessing() throws IOException {
        setupSchoolInfoMock();
        setupALMock();

        usaFinder.produceStateInstitutions("AL");

        verify(institutionsProducer, times(9)).send(any(Institution.class));
    }

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

    @Test
    void produceStateInstitutions_multiPageResults_sucessfulProcessing() throws IOException {
        setupSchoolInfoMock();
        setupNYMock();

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
