package io.github.aaejo.institutionfinder;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import io.github.aaejo.institutionfinder.finder.InstitutionFinder;

@SpringBootApplication
public class InstitutionFinderApplication {

    public static void main(String[] args) {
        SpringApplication.run(InstitutionFinderApplication.class, args);
    }

    @Bean
    @Profile("console")
    public ApplicationRunner runner(InstitutionFinder institutionFinder) {
        return args -> {
            System.out.println("Hit Enter to send...");
            System.in.read();
            institutionFinder.produceInstitutions();
        };
    }

}
