package kervan.core.domain;

/**
 * A shipment as the store holds it, with the recipient's phone for verification.
 * Never serialized; {@link #toString()} hides the phone so it cannot leak into logs.
 */
public record ShipmentRecord(
        String trackingNo,
        ShipmentStatus status,
        String originCity,
        String destinationCity,
        String recipientPhone) {

    @Override
    public String toString() {
        return "ShipmentRecord[trackingNo=%s, status=%s, originCity=%s, destinationCity=%s, recipientPhone=***]"
                .formatted(trackingNo, status, originCity, destinationCity);
    }
}
