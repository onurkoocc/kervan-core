package kervan.core.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import kervan.core.domain.Channel;
import kervan.core.domain.Ticket;
import kervan.core.domain.TicketCategory;
import kervan.core.domain.TicketStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTicketStore implements TicketStore {

    private static final String COLUMNS = "ticket_id, tracking_no, category, description, status, channel, created_at";

    // Numbers come from ticket_number_seq; one that a seed row already holds is skipped.
    private static final String INSERT = """
            insert into tickets (ticket_id, tracking_no, category, description, status, channel, created_at)
            values ('TCK-' || lpad(nextval('ticket_number_seq')::text, 6, '0'),
                    :trackingNo, :category, :description, 'OPEN', :channel, :createdAt)
            on conflict (ticket_id) do nothing
            returning\s""" + COLUMNS;

    private static final int MAX_ATTEMPTS = 20;

    private final JdbcClient jdbc;

    JdbcTicketStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Ticket open(String trackingNo, TicketCategory category, String description, Channel channel,
            Instant createdAt) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Optional<Ticket> stored = jdbc.sql(INSERT)
                    .param("trackingNo", trackingNo)
                    .param("category", category.name())
                    .param("description", description)
                    .param("channel", channel.wire())
                    .param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                    .query(JdbcTicketStore::ticket)
                    .optional();
            if (stored.isPresent()) {
                return stored.get();
            }
        }
        throw new IllegalStateException("no free ticket number after " + MAX_ATTEMPTS + " attempts");
    }

    @Override
    public Optional<Ticket> findTicket(String ticketId) {
        return jdbc.sql("select " + COLUMNS + " from tickets where ticket_id = :ticketId")
                .param("ticketId", ticketId)
                .query(JdbcTicketStore::ticket)
                .optional();
    }

    private static Ticket ticket(ResultSet rs, int row) throws SQLException {
        return new Ticket(
                rs.getString("ticket_id"),
                rs.getString("tracking_no"),
                TicketCategory.valueOf(rs.getString("category")),
                rs.getString("description"),
                TicketStatus.valueOf(rs.getString("status")),
                Channel.fromWire(rs.getString("channel")).orElseThrow(),
                JdbcShipmentStore.instant(rs, "created_at"));
    }
}
