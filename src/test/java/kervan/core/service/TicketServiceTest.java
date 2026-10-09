package kervan.core.service;

import static kervan.core.support.Fixtures.IN_TRANSIT;
import static kervan.core.support.Fixtures.NOW;
import static kervan.core.support.Fixtures.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kervan.core.domain.Channel;
import kervan.core.domain.CoreException;
import kervan.core.domain.Ticket;
import kervan.core.domain.TicketCategory;
import kervan.core.domain.TicketStatus;
import kervan.core.support.Fixtures;
import kervan.core.support.InMemoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TicketServiceTest {

    private final InMemoryStore store = Fixtures.seed(new InMemoryStore());
    private final TicketService service = new TicketService(store, store, Fixtures.CLOCK);

    @Test
    void a_new_ticket_is_open_and_numbered() {
        Ticket ticket = service.open(IN_TRANSIT, "DAMAGED", "The box arrived crushed.", "agent");

        assertThat(ticket.ticketId()).matches("TCK-\\d{6}");
        assertThat(ticket.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.category()).isEqualTo(TicketCategory.DAMAGED);
        assertThat(ticket.channel()).isEqualTo(Channel.AGENT);
        assertThat(ticket.createdAt()).isEqualTo(NOW);
        assertThat(service.get(ticket.ticketId())).isEqualTo(ticket);
    }

    @Test
    void a_ticket_may_have_no_tracking_number() {
        assertThat(service.open(null, "OTHER", "Question about prices.", "web").trackingNo()).isNull();
    }

    @Test
    void an_unknown_tracking_number_is_not_found() {
        assertThatThrownBy(() -> service.open(UNKNOWN, "LOST", "Where is it?", "phone"))
                .isInstanceOf(CoreException.NotFound.class);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "NULL, text, agent",
            "damaged, text, agent",
            "BROKEN, text, agent",
            "DAMAGED, NULL, agent",
            "DAMAGED, '   ', agent",
            "DAMAGED, text, NULL",
            "DAMAGED, text, AGENT",
            "DAMAGED, text, email",
    })
    void a_malformed_ticket_is_an_invalid_request(String category, String description, String channel) {
        assertThatThrownBy(() -> service.open(null, category, description, channel))
                .isInstanceOf(CoreException.InvalidRequest.class);
    }

    @Test
    void descriptions_are_limited_to_2000_characters() {
        service.open(null, "OTHER", "x".repeat(2000), "web");
        assertThatThrownBy(() -> service.open(null, "OTHER", "x".repeat(2001), "web"))
                .isInstanceOf(CoreException.InvalidRequest.class);
    }

    @Test
    void a_blank_tracking_number_is_an_invalid_request() {
        assertThatThrownBy(() -> service.open(" ", "OTHER", "text", "web"))
                .isInstanceOf(CoreException.InvalidRequest.class);
    }

    @Test
    void an_unknown_ticket_is_not_found() {
        assertThatThrownBy(() -> service.get("TCK-000000")).isInstanceOf(CoreException.NotFound.class);
    }
}
