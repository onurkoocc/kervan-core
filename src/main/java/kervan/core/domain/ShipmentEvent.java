package kervan.core.domain;

import java.time.Instant;

public record ShipmentEvent(Instant at, String description) {
}
