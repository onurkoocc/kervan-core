package kervan.core.web;

import java.time.Clock;
import kervan.core.service.ShipmentService;
import kervan.core.service.TicketService;
import kervan.core.support.Fixtures;
import kervan.core.support.InMemoryStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/** The web slice with the real services over an in-memory store and the fixed test clock. */
@TestConfiguration
@Import({ShipmentService.class, TicketService.class})
class WebTestConfig {

    @Bean
    InMemoryStore inMemoryStore() {
        return new InMemoryStore();
    }

    @Bean
    @Primary
    Clock fixedClock() {
        return Fixtures.CLOCK;
    }
}
