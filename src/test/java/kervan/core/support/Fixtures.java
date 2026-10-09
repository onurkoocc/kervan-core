package kervan.core.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import kervan.core.domain.DeliveryStatus;
import kervan.core.domain.ShipmentStatus;

/** Shared test data. All of it is synthetic. */
public final class Fixtures {

    /**
     * 21:30 UTC on 9 October is 00:30 on 10 October in Istanbul (UTC+3), so "today" depends on the
     * zone: tests built on this clock catch a service that uses UTC or the host's zone.
     */
    public static final Instant NOW = Instant.parse("2026-10-09T21:30:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final LocalDate TODAY_IN_ISTANBUL = LocalDate.parse("2026-10-10");

    public static final String IN_TRANSIT = "KRV10000001";
    public static final String OUT_FOR_DELIVERY = "KRV10000002";
    public static final String DELIVERED = "KRV10000003";
    public static final String NO_DELIVERY = "KRV10000004";
    public static final String UNKNOWN = "KRV99999999";

    /** Recipient phone of every fixture shipment; the verification code is its last four digits. */
    public static final String PHONE = "+90 532 555 12 34";
    public static final String CODE = "1234";

    private Fixtures() {
    }

    public static InMemoryStore seed(InMemoryStore store) {
        return store.clear()
                .shipment(IN_TRANSIT, ShipmentStatus.IN_TRANSIT, PHONE)
                .delivery(IN_TRANSIT, LocalDate.parse("2026-10-12"), DeliveryStatus.SCHEDULED, 0)
                .event(IN_TRANSIT, Instant.parse("2026-10-08T09:00:00Z"), "Accepted at Istanbul hub")
                .event(IN_TRANSIT, Instant.parse("2026-10-09T06:15:00Z"), "Departed Istanbul hub")
                .shipment(OUT_FOR_DELIVERY, ShipmentStatus.OUT_FOR_DELIVERY, PHONE)
                .delivery(OUT_FOR_DELIVERY, LocalDate.parse("2026-10-10"), DeliveryStatus.SCHEDULED, 0)
                .shipment(DELIVERED, ShipmentStatus.DELIVERED, PHONE)
                .delivery(DELIVERED, LocalDate.parse("2026-10-08"), DeliveryStatus.DELIVERED, 1)
                .shipment(NO_DELIVERY, ShipmentStatus.CREATED, PHONE);
    }
}
