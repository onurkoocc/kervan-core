package kervan.core.domain;

import java.time.LocalDate;

public record Delivery(
        String trackingNo,
        LocalDate deliveryDate,
        DeliveryWindow window,
        DeliveryStatus status,
        int attempts) {
}
