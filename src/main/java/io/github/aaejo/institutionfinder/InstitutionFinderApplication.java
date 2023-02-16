package io.github.aaejo.institutionfinder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import io.github.aaejo.institutionfinder.finder.InstitutionFinder;

@SpringBootApplication
public class InstitutionFinderApplication {

    @Autowired
    private InstitutionFinder institutionFinder;

    public static void main(String[] args) {
        SpringApplication.run(InstitutionFinderApplication.class, args);
    }

    @Bean
    @Profile("default") // Don't run from test(s)
    public ApplicationRunner runner() {
        return args -> {
            System.out.println("Hit Enter to send...");
            System.in.read();
            institutionFinder.produceInstitutions();
        };
    }

}
