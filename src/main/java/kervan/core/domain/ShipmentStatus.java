package kervan.core.domain;

public enum ShipmentStatus {
    CREATED,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    RETURNED;

    /** A delivery date can only move while the parcel has not left for its last mile. */
    public boolean isReschedulable() {
        return this == CREATED || this == IN_TRANSIT;
    }
}
