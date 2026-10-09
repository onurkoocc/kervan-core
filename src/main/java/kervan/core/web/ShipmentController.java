package kervan.core.web;

import kervan.core.domain.Delivery;
import kervan.core.domain.Shipment;
import kervan.core.service.ShipmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipments")
class ShipmentController {

    /** {@code newDate} stays a string so a malformed date is answered with invalid_date. */
    record RescheduleRequest(String newDate, String verificationCode, String reason) {
    }

    private final ShipmentService shipments;

    ShipmentController(ShipmentService shipments) {
        this.shipments = shipments;
    }

    @GetMapping("/{trackingNo}")
    Shipment get(@PathVariable String trackingNo) {
        return shipments.get(trackingNo);
    }

    @PostMapping("/{trackingNo}/reschedule")
    Delivery reschedule(@PathVariable String trackingNo, @RequestBody RescheduleRequest body) {
        return shipments.reschedule(trackingNo, body.newDate(), body.verificationCode());
    }
}
