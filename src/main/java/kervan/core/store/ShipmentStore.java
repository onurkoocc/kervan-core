package kervan.core.store;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import kervan.core.domain.Delivery;
import kervan.core.domain.ShipmentEvent;
import kervan.core.domain.ShipmentRecord;

/** Shipments, their delivery and their event history. */
public interface ShipmentStore {

    Optional<ShipmentRecord> find(String trackingNo);

    /** Like {@link #find} but locks the shipment until the surrounding transaction ends. */
    Optional<ShipmentRecord> findForUpdate(String trackingNo);

    boolean exists(String trackingNo);

    Optional<Delivery> findDelivery(String trackingNo);

    Optional<ShipmentEvent> lastEvent(String trackingNo);

    /** Moves the delivery to {@code date} with status RESCHEDULED and returns the updated row. */
    Delivery reschedule(String trackingNo, LocalDate date);

    void appendEvent(String trackingNo, Instant at, String description);
}
