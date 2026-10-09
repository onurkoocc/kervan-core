package kervan.core.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import kervan.core.domain.Delivery;
import kervan.core.domain.Shipment;
import kervan.core.domain.ShipmentEvent;
import kervan.core.domain.Ticket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * docs/openapi.json is hand-written; this test holds it to the code: the same operations as the
 * controllers map, the response codes of SPEC §5, schemas whose properties are the records'
 * components, enum values as Jackson writes them, and sorted keys so the file stays deterministic.
 */
@WebMvcTest
@Import(WebTestConfig.class)
class OpenApiContractTest {

    private static final Path FILE = Path.of("docs/openapi.json");

    /** SPEC §5's answers per operation, plus 401 for the service token. */
    private static final Map<String, Set<String>> RESPONSES = Map.of(
            "GET /api/v1/shipments/{trackingNo}", Set.of("200", "401", "404"),
            "POST /api/v1/shipments/{trackingNo}/reschedule", Set.of("200", "400", "401", "403", "404", "409"),
            "POST /api/v1/tickets", Set.of("201", "400", "401", "404"),
            "GET /api/v1/tickets/{ticketId}", Set.of("200", "401", "404"));

    private static final Map<String, Class<? extends Record>> SCHEMAS = Map.of(
            "Shipment", Shipment.class,
            "ShipmentEvent", ShipmentEvent.class,
            "Delivery", Delivery.class,
            "Ticket", Ticket.class,
            "Error", ApiError.class,
            "RescheduleRequest", ShipmentController.RescheduleRequest.class,
            "TicketRequest", TicketController.TicketRequest.class);

    private static final Set<String> RESPONSE_SCHEMAS = Set.of("Shipment", "ShipmentEvent", "Delivery", "Ticket", "Error");

    private static JsonNode spec;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeAll
    static void read() throws Exception {
        ObjectMapper strict = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        spec = strict.readTree(Files.readString(FILE));
    }

    @Test
    void is_openapi_3_0() {
        assertThat(spec.path("openapi").asText()).startsWith("3.0.");
    }

    @Test
    void describes_exactly_the_mapped_operations() {
        Set<String> mapped = new TreeSet<>();
        mappings.getHandlerMethods().forEach((info, method) -> {
            if (!method.getBeanType().getPackageName().startsWith("kervan.core")) {
                return;
            }
            for (String path : info.getPatternValues()) {
                info.getMethodsCondition().getMethods().forEach(m -> mapped.add(m.name() + " " + path));
            }
        });

        assertThat(operations().keySet()).isEqualTo(mapped).isEqualTo(new TreeSet<>(RESPONSES.keySet()));
    }

    @Test
    void lists_the_spec_response_codes_and_the_error_shape() {
        operations().forEach((operation, node) -> {
            Set<String> codes = new TreeSet<>();
            node.path("responses").fieldNames().forEachRemaining(codes::add);
            assertThat(codes).as(operation).isEqualTo(new TreeSet<>(RESPONSES.get(operation)));
            node.path("responses").properties().forEach(response -> {
                if (response.getKey().startsWith("4")) {
                    JsonNode resolved = resolve(response.getValue());
                    assertThat(resolved.at("/content/application~1json/schema/$ref").asText())
                            .as(operation + " " + response.getKey())
                            .isEqualTo("#/components/schemas/Error");
                }
            });
        });
    }

    @Test
    void schemas_have_the_records_properties() {
        JsonNode schemas = spec.at("/components/schemas");
        assertThat(names(schemas)).isEqualTo(new TreeSet<>(SCHEMAS.keySet()));
        SCHEMAS.forEach((name, type) -> {
            JsonNode schema = schemas.get(name);
            Set<String> components = Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getName)
                    .collect(Collectors.toCollection(TreeSet::new));
            assertThat(names(schema.get("properties"))).as(name).isEqualTo(components);
            if (RESPONSE_SCHEMAS.contains(name)) {
                // Jackson writes every component, nulls included, so all are required.
                assertThat(textSet(schema.get("required"))).as(name + " required").isEqualTo(components);
            }
        });
    }

    @Test
    void enum_values_are_what_jackson_writes() {
        JsonNode schemas = spec.at("/components/schemas");
        for (String name : RESPONSE_SCHEMAS) {
            for (RecordComponent component : SCHEMAS.get(name).getRecordComponents()) {
                if (!component.getType().isEnum()) {
                    continue;
                }
                List<String> wire = new ArrayList<>();
                for (Object constant : component.getType().getEnumConstants()) {
                    wire.add(objectMapper.convertValue(constant, String.class));
                }
                JsonNode property = schemas.at("/" + name + "/properties/" + component.getName());
                assertThat(textList(property.get("enum"))).as(name + "." + component.getName()).isEqualTo(wire);
            }
        }
        assertThat(schemas.at("/TicketRequest/properties/category/enum")).isEqualTo(schemas.at("/Ticket/properties/category/enum"));
        assertThat(schemas.at("/TicketRequest/properties/channel/enum")).isEqualTo(schemas.at("/Ticket/properties/channel/enum"));
    }

    @Test
    void every_operation_needs_the_service_token() {
        assertThat(spec.at("/security/0/serviceToken").isArray()).isTrue();
        assertThat(spec.at("/components/securitySchemes/serviceToken/name").asText()).isEqualTo("X-Service-Token");
        assertThat(spec.at("/components/securitySchemes/serviceToken/in").asText()).isEqualTo("header");
    }

    @Test
    void keys_are_sorted_so_the_file_is_deterministic() throws Exception {
        assertSorted(spec, "$");
        assertThat(Files.readString(FILE)).endsWith("}\n");
    }

    private static void assertSorted(JsonNode node, String where) {
        if (node.isObject()) {
            List<String> keys = new ArrayList<>();
            node.fieldNames().forEachRemaining(keys::add);
            assertThat(keys).as("keys of " + where).isSorted();
            node.properties().forEach(e -> assertSorted(e.getValue(), where + "." + e.getKey()));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                assertSorted(node.get(i), where + "[" + i + "]");
            }
        }
    }

    private static Map<String, JsonNode> operations() {
        Map<String, JsonNode> operations = new TreeMap<>();
        spec.path("paths").properties().forEach(path -> path.getValue().properties().forEach(op ->
                operations.put(op.getKey().toUpperCase() + " " + path.getKey(), op.getValue())));
        return operations;
    }

    private static JsonNode resolve(JsonNode node) {
        String ref = node.path("$ref").asText("");
        return ref.isEmpty() ? node : spec.at(ref.substring(1));
    }

    private static Set<String> names(JsonNode object) {
        Set<String> names = new TreeSet<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static Set<String> textSet(JsonNode array) {
        return new TreeSet<>(textList(array));
    }

    private static List<String> textList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asText()));
        return values;
    }
}
