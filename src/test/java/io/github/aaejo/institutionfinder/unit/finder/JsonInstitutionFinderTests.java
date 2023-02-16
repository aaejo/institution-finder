package io.github.aaejo.institutionfinder.unit.finder;

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.aaejo.institutionfinder.finder.JsonInstitutionFinder;
import io.github.aaejo.institutionfinder.messaging.producer.InstitutionsProducer;

public class JsonInstitutionFinderTests {

    private final InstitutionsProducer institutionsProducer = mock(InstitutionsProducer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final JsonInstitutionFinder jsonInstitutionFinder = new JsonInstitutionFinder("Canada",
            institutionsProducer, objectMapper);

    @Test
    void notJsonList_illegalStateException() {
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

}
