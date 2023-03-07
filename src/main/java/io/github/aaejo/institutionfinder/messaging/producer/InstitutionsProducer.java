package io.github.aaejo.institutionfinder.messaging.producer;

import java.util.concurrent.CompletableFuture;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import io.github.aaejo.messaging.records.Institution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class InstitutionsProducer {

    private static final String TOPIC = "institutions";

    private final KafkaTemplate<String, Institution> template;

    public InstitutionsProducer(KafkaTemplate<String, Institution> template) {
        this.template = template;
    }

    public void send(final Institution institution) {
        CompletableFuture<SendResult<String, Institution>> sendResultFuture = this.template.send(TOPIC, institution);
        sendResultFuture.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Sent: {}", institution.toString());
            }
            else {
                log.error("Failed to send: {}", institution.toString(), ex);
            }
        });
    }
}
