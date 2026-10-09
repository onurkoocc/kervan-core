package kervan.core;

import java.time.Clock;
import kervan.core.domain.RescheduleRules;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class KervanCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(KervanCoreApplication.class, args);
    }

    /** The service clock. Tests replace it with a fixed one. */
    @Bean
    Clock clock() {
        return Clock.system(RescheduleRules.ZONE);
    }
}
