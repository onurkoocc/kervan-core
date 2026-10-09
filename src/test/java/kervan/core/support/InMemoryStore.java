package kervan.core.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kervan.core.domain.Channel;
import kervan.core.domain.Delivery;
import kervan.core.domain.DeliveryStatus;
import kervan.core.domain.DeliveryWindow;
import kervan.core.domain.ShipmentEvent;
import kervan.core.domain.ShipmentRecord;
import kervan.core.domain.ShipmentStatus;
import kervan.core.domain.Ticket;
import kervan.core.domain.TicketCategory;
import kervan.core.domain.TicketStatus;
import kervan.core.store.ShipmentStore;
import kervan.core.store.TicketStore;

/** A store held in maps, standing in for PostgreSQL in tests that run without Docker. */
public class InMemoryStore implements ShipmentStore, TicketStore {

    public record Event(String trackingNo, Instant at, String description) {
    }

    private final Map<String, ShipmentRecord> shipments = new LinkedHashMap<>();
    private final Map<String, Delivery> deliveries = new LinkedHashMap<>();
    private final List<Event> events = new ArrayList<>();
    private final Map<String, Ticket> tickets = new LinkedHashMap<>();
    private int nextTicketNumber = 100001;

    public InMemoryStore clear() {
        shipments.clear();
        deliveries.clear();
        events.clear();
        tickets.clear();
        nextTicketNumber = 100001;
        return this;
    }

    public InMemoryStore shipment(String trackingNo, ShipmentStatus status, String phone) {
        shipments.put(trackingNo, new ShipmentRecord(trackingNo, status, "Istanbul", "Ankara", phone));
        return this;
    }

    public InMemoryStore delivery(String trackingNo, LocalDate date, DeliveryStatus status, int attempts) {
        deliveries.put(trackingNo, new Delivery(trackingNo, date, DeliveryWindow.AFTERNOON, status, attempts));
        return this;
    }

    public InMemoryStore event(String trackingNo, Instant at, String description) {
        events.add(new Event(trackingNo, at, description));
        return this;
    }

    public List<Event> events() {
        return List.copyOf(events);
    }

    public Optional<Delivery> delivery(String trackingNo) {
        return Optional.ofNullable(deliveries.get(trackingNo));
    }

    @Override
    public Optional<ShipmentRecord> find(String trackingNo) {
        return Optional.ofNullable(shipments.get(trackingNo));
    }

    @Override
    public Optional<ShipmentRecord> findForUpdate(String trackingNo) {
        return find(trackingNo);
    }

    @Override
    public boolean exists(String trackingNo) {
        return shipments.containsKey(trackingNo);
    }

    @Override
    public Optional<Delivery> findDelivery(String trackingNo) {
        return delivery(trackingNo);
    }

    @Override
    public Optional<ShipmentEvent> lastEvent(String trackingNo) {
        return events.stream()
                .filter(e -> e.trackingNo().equals(trackingNo))
                .max(Comparator.comparing(Event::at))
                .map(e -> new ShipmentEvent(e.at(), e.description()));
    }

    @Override
    public Delivery reschedule(String trackingNo, LocalDate date) {
        Delivery old = deliveries.get(trackingNo);
        Delivery updated = new Delivery(trackingNo, date, old.window(), DeliveryStatus.RESCHEDULED, old.attempts());
        deliveries.put(trackingNo, updated);
        return updated;
    }

    @Override
    public void appendEvent(String trackingNo, Instant at, String description) {
        events.add(new Event(trackingNo, at, description));
    }

    @Override
    public Ticket open(String trackingNo, TicketCategory category, String description, Channel channel,
            Instant createdAt) {
        String id = "TCK-%06d".formatted(nextTicketNumber++);
        Ticket ticket = new Ticket(id, trackingNo, category, description, TicketStatus.OPEN, channel, createdAt);
        tickets.put(id, ticket);
        return ticket;
    }

    @Override
    public Optional<Ticket> findTicket(String ticketId) {
        return Optional.ofNullable(tickets.get(ticketId));
    }
}
