package kervan.core.domain;

import java.time.LocalDate;

/** The API view of a shipment. It carries no personal data (SPEC §5). */
public record Shipment(
        String trackingNo,
        ShipmentStatus status,
        String originCity,
        String destinationCity,
        LocalDate estimatedDelivery,
        ShipmentEvent lastEvent) {
}
