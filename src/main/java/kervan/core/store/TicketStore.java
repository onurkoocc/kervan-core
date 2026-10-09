package kervan.core.store;

import java.time.Instant;
import java.util.Optional;
import kervan.core.domain.Channel;
import kervan.core.domain.Ticket;
import kervan.core.domain.TicketCategory;

public interface TicketStore {

    /** Stores a new OPEN ticket and gives it the next free id of the form TCK-######. */
    Ticket open(String trackingNo, TicketCategory category, String description, Channel channel, Instant createdAt);

    Optional<Ticket> findTicket(String ticketId);
}
