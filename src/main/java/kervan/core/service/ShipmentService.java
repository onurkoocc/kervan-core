package kervan.core.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import kervan.core.domain.CoreException;
import kervan.core.domain.Delivery;
import kervan.core.domain.RescheduleRules;
import kervan.core.domain.Shipment;
import kervan.core.domain.ShipmentRecord;
import kervan.core.store.ShipmentStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentService {

    private final ShipmentStore store;
    private final Clock clock;

    public ShipmentService(ShipmentStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Shipment get(String trackingNo) {
        ShipmentRecord shipment = store.find(trackingNo).orElseThrow(() -> notFound(trackingNo));
        return new Shipment(
                shipment.trackingNo(),
                shipment.status(),
                shipment.originCity(),
                shipment.destinationCity(),
                store.findDelivery(trackingNo).map(Delivery::deliveryDate).orElse(null),
                store.lastEvent(trackingNo).orElse(null));
    }

    /**
     * Applies SPEC §5's reschedule rules in this order: the shipment exists (404), the caller
     * knows the recipient's code (403), the shipment may still move (409), the date is in range (400).
     * The reason is accepted but not stored: it is free text and may hold personal data.
     */
    @Transactional
    public Delivery reschedule(String trackingNo, String newDate, String verificationCode) {
        ShipmentRecord shipment = store.findForUpdate(trackingNo).orElseThrow(() -> notFound(trackingNo));
        if (!RescheduleRules.verificationMatches(shipment.recipientPhone(), verificationCode)) {
            throw new CoreException.VerificationFailed();
        }
        RescheduleRules.requireReschedulable(shipment.status());
        Optional<Delivery> delivery = store.findDelivery(trackingNo);
        if (delivery.isEmpty()) {
            throw new CoreException.NotReschedulable("shipment " + trackingNo + " has no delivery to reschedule");
        }
        LocalDate date = RescheduleRules.requireValidNewDate(newDate, RescheduleRules.today(clock));
        Delivery updated = store.reschedule(trackingNo, date);
        store.appendEvent(trackingNo, clock.instant().truncatedTo(ChronoUnit.MILLIS),
                "Delivery rescheduled to " + date + " at the recipient's request");
        return updated;
    }

    static CoreException.NotFound notFound(String trackingNo) {
        return new CoreException.NotFound("shipment " + trackingNo + " not found");
    }
}
