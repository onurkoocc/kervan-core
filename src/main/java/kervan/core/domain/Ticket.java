package kervan.core.domain;

import java.time.Instant;

public record Ticket(
        String ticketId,
        String trackingNo,
        TicketCategory category,
        String description,
        TicketStatus status,
        Channel channel,
        Instant createdAt) {
}
