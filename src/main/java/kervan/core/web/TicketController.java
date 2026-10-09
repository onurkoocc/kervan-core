package kervan.core.web;

import java.net.URI;
import kervan.core.domain.Ticket;
import kervan.core.service.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
class TicketController {

    /** Enums stay strings so an unknown value is answered with invalid_request. */
    record TicketRequest(String trackingNo, String category, String description, String channel) {
    }

    private final TicketService tickets;

    TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping
    ResponseEntity<Ticket> open(@RequestBody TicketRequest body) {
        Ticket ticket = tickets.open(body.trackingNo(), body.category(), body.description(), body.channel());
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + ticket.ticketId())).body(ticket);
    }

    @GetMapping("/{ticketId}")
    Ticket get(@PathVariable String ticketId) {
        return tickets.get(ticketId);
    }
}
