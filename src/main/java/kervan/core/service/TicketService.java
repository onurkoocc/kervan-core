package kervan.core.service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import kervan.core.domain.Channel;
import kervan.core.domain.CoreException;
import kervan.core.domain.Ticket;
import kervan.core.domain.TicketCategory;
import kervan.core.store.ShipmentStore;
import kervan.core.store.TicketStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    private final TicketStore tickets;
    private final ShipmentStore shipments;
    private final Clock clock;

    public TicketService(TicketStore tickets, ShipmentStore shipments, Clock clock) {
        this.tickets = tickets;
        this.shipments = shipments;
        this.clock = clock;
    }

    @Transactional
    public Ticket open(String trackingNo, String category, String description, String channel) {
        TicketCategory parsedCategory = parseCategory(category);
        Channel parsedChannel = Channel.fromWire(channel)
                .orElseThrow(() -> new CoreException.InvalidRequest("channel must be one of agent, web, phone"));
        if (description == null || description.isBlank()) {
            throw new CoreException.InvalidRequest("description is required");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new CoreException.InvalidRequest(
                    "description must be at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        if (trackingNo != null) {
            if (trackingNo.isBlank()) {
                throw new CoreException.InvalidRequest("trackingNo must not be blank when given");
            }
            if (!shipments.exists(trackingNo)) {
                throw ShipmentService.notFound(trackingNo);
            }
        }
        return tickets.open(trackingNo, parsedCategory, description, parsedChannel,
                clock.instant().truncatedTo(ChronoUnit.MILLIS));
    }

    @Transactional(readOnly = true)
    public Ticket get(String ticketId) {
        return tickets.findTicket(ticketId)
                .orElseThrow(() -> new CoreException.NotFound("ticket " + ticketId + " not found"));
    }

    private static TicketCategory parseCategory(String category) {
        return Arrays.stream(TicketCategory.values())
                .filter(c -> c.name().equals(category))
                .findFirst()
                .orElseThrow(() -> new CoreException.InvalidRequest(
                        "category must be one of DAMAGED, LOST, DELAY, OTHER"));
    }
}
