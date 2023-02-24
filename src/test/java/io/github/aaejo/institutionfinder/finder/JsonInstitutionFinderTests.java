package io.github.aaejo.institutionfinder.finder;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.aaejo.institutionfinder.finder.JsonInstitutionFinder;
import io.github.aaejo.institutionfinder.messaging.producer.InstitutionsProducer;
import io.github.aaejo.messaging.records.Institution;

public class JsonInstitutionFinderTests {

    private final InstitutionsProducer institutionsProducer = mock(InstitutionsProducer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final JsonInstitutionFinder jsonInstitutionFinder = new JsonInstitutionFinder("Canada",
            institutionsProducer, objectMapper);

    @Test
    void produceInstitutionsJson_notJsonList_illegalStateException() {
        String json = """
                {
                    \"name\": \"Acadia University\",
                    \"country\": \"Canada\",
                    \"address\": \"15 University Ave, Wolfville, NS, B4P 2R6\",
                    \"website\": \"https://philosophy.acadiau.ca/facstaff.html\"
                }""";
        InputStream jsonStream = new ByteArrayInputStream(json.getBytes());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> jsonInstitutionFinder.produceInstitutionsJson(jsonStream));
    }

    @Test
    void produceInstitutionsJson_singleItem_oneInstitutionSent() throws IOException {
        String json = """
                [{
                    \"name\": \"Acadia University\",
                    \"country\": \"Canada\",
                    \"address\": \"15 University Ave, Wolfville, NS, B4P 2R6\",
                    \"website\": \"https://philosophy.acadiau.ca/facstaff.html\"
                }]""";
        InputStream jsonStream = new ByteArrayInputStream(json.getBytes());
        Institution expected = new Institution(
                "Acadia University",
                "Canada",
                "15 University Ave, Wolfville, NS, B4P 2R6",
                "https://philosophy.acadiau.ca/facstaff.html");

        jsonInstitutionFinder.produceInstitutionsJson(jsonStream);

        verify(institutionsProducer).send(expected);
    }

    @Test
    void produceInstitutionsJson_threeItems_threeInstitutionsSent() throws IOException {
        String json = """
                [
                    {
                        \"name\": \"Acadia University\",
                        \"country\": \"Canada\",
                        \"address\": \"15 University Ave, Wolfville, NS, B4P 2R6\",
                        \"website\": \"https://philosophy.acadiau.ca/facstaff.html\"
                    },
                    {
                        \"name\": \"University of Ottawa\",
                        \"country\": \"Canada\",
                        \"address\": \"75 Laurier Ave E, Ottawa, ON, K1N 6N5\",
                        \"website\": \"https://www.uottawa.ca/faculty-arts/philosophy\"
                    },
                    {
                        \"name\": \"Brandon University\",
                        \"country\": \"Canada\",
                        \"address\": \"270 18th St, Brandon, MB, R7A 6A9\",
                        \"website\": \"https://www.brandonu.ca/philosophy/faculty/\"
                    }
                ]""";
        InputStream jsonStream = new ByteArrayInputStream(json.getBytes());
        Institution expected1 = new Institution(
                "Acadia University",
                "Canada",
                "15 University Ave, Wolfville, NS, B4P 2R6",
                "https://philosophy.acadiau.ca/facstaff.html");
        Institution expected2 = new Institution(
                "University of Ottawa",
                "Canada",
                "75 Laurier Ave E, Ottawa, ON, K1N 6N5",
                "https://www.uottawa.ca/faculty-arts/philosophy");
        Institution expected3 = new Institution(
                "Brandon University",
                "Canada",
                "270 18th St, Brandon, MB, R7A 6A9",
                "https://www.brandonu.ca/philosophy/faculty/");

        jsonInstitutionFinder.produceInstitutionsJson(jsonStream);

        verify(institutionsProducer).send(expected1);
        verify(institutionsProducer).send(expected2);
        verify(institutionsProducer).send(expected3);
    }
}
