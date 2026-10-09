# kervan-core

The domain API and system of record of **Kervan**, the fictional cargo and delivery company
that Mandate's brownfield work is built and measured on. It owns shipments, deliveries,
tickets and customers in PostgreSQL. All data is synthetic.

Java 21, Spring Boot 3.5, Spring JDBC (`JdbcClient`), Flyway, PostgreSQL 16.

## Where it sits in Kervan

```text
voice ──▶ mcp ──(REST, X-Service-Token)──▶ core ──▶ postgres
```

`kervan-mcp` is the only caller. Its tools `get_shipment`, `reschedule_delivery` and
`open_ticket` map one to one to the endpoints below and pass core's error codes through
(`409 not_reschedulable`, …). The system model in `kervan-system` names two interfaces
this repository provides:

| Interface | Kind | File |
|---|---|---|
| `CoreApi` | `openapi` | [`docs/openapi.json`](docs/openapi.json) |
| `CoreSchema` | `db_schema` | [`src/main/resources/db/migration`](src/main/resources/db/migration) |

## API

Every request needs `X-Service-Token: <SERVICE_TOKEN>`; otherwise the answer is
`401 {"error":"unauthorized","message":"…"}`. `GET /actuator/health` is the only exception.
Errors are always `{"error": "<code>", "message": "…"}`.

| Method and path | Body | Answer |
|---|---|---|
| `GET /api/v1/shipments/{trackingNo}` | — | 200 `Shipment` · 404 `not_found` |
| `POST /api/v1/shipments/{trackingNo}/reschedule` | `{newDate, verificationCode, reason?}` | 200 `Delivery` · 400 `invalid_date` · 403 `verification_failed` · 404 `not_found` · 409 `not_reschedulable` |
| `POST /api/v1/tickets` | `{trackingNo?, category, description, channel}` | 201 `Ticket` · 400 `invalid_request` · 404 `not_found` |
| `GET /api/v1/tickets/{ticketId}` | — | 200 `Ticket` · 404 `not_found` |

`docs/openapi.json` (OpenAPI 3.0.3) is the full description. It is written by hand, with sorted
keys; `OpenApiContractTest` fails when it drifts from the controllers (operations), from
the response records (properties, enum values) or from the response codes above.

### Reschedule rules

Checked in this order:

1. The shipment exists, else 404 `not_found`.
2. `verificationCode` is exactly the last four digits of the recipient's phone, else
   403 `verification_failed`. Checked before anything else about the shipment is revealed.
3. The shipment is `CREATED` or `IN_TRANSIT` and has a delivery row, else 409
   `not_reschedulable` (`OUT_FOR_DELIVERY`, `DELIVERED`, `RETURNED`).
4. `newDate` is a `YYYY-MM-DD` date from tomorrow to today + 14 days, both inclusive, where
   "today" is taken in **Europe/Istanbul** whatever the host's zone, else 400 `invalid_date`.

A success sets the delivery's date and status `RESCHEDULED` (attempts unchanged) and appends
the shipment event "Delivery rescheduled to <date> at the recipient's request". `reason` is
accepted but not stored, because it is free text and may contain personal data. The clock is
a `java.time.Clock` bean, so tests pin it.

### Tickets

Ids are `TCK-` plus six digits, from the sequence `ticket_number_seq`, which starts at
`100001` so seed data can use `TCK-000001` … `TCK-100000`. A number that a seed row already
holds is skipped. `description` is at most 2000 characters; `channel` is lower case (`agent`,
`web`, `phone`).

## Database

Flyway migrations in `src/main/resources/db/migration`, schema `public`:

- `V1__core_schema.sql`: `customers`, `shipments`, `deliveries`, `shipment_events`,
  `tickets`, with check constraints for every enum column and `ticket_number_seq`.
- `V2__shipment_overview_view.sql`: the view `v_shipment_overview` that the Mandate read
  broker queries. It grants nothing; `kervan-system`'s seed scripts create the read-only role
  `kervan_reader`.

Notes for seed scripts:

- `deliveries."window"` must be quoted: `window` is a reserved word in PostgreSQL.
- `shipment_events.id` and `tickets.id` are identities starting at 1000000, so seed rows may
  use small explicit ids. `customers.id` and `deliveries.id` start at 1; when a seed gives
  explicit ids there, it should call `setval` on the identity afterwards.
- `customers.full_name`, `phone` and `email` are `confidential` (SPEC §5.2); everything else
  is `internal`.

## Configuration

Environment only. The defaults are the compose values.

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | `8080` | Listen port |
| `DB_URL` | `jdbc:postgresql://postgres:5432/kervan` | JDBC URL |
| `DB_USER` / `DB_PASSWORD` | `kervan_core` / required (compose sets it) | Owner of the schema; Flyway migrates as this user. The password has no default in the code |
| `SERVICE_TOKEN` | `svc-dev-token` | Expected `X-Service-Token`. Empty refuses every request |
| `LOG_FILE` | unset | Also append each log line to this file |

## Logs

One JSON object per line on stdout, and appended to `LOG_FILE` when it is set:

```json
{"ts":"2026-10-09T21:15:03.709Z","level":"info","service":"core","msg":"POST /api/v1/shipments/KRV20000001/reschedule","trace_id":"8e7a35a13ae643929894b65c2c4172e9","status":200,"duration_ms":26}
```

- `ts`, `level`, `service`, `msg` are always present; `trace_id`, `status` and `duration_ms`
  appear on request lines.
- The trace id comes from a W3C `traceparent` header, else from `X-Trace-Id`, else it is new.
  It is echoed back as `X-Trace-Id`.
- Request lines name the method and path, which may hold a tracking number. Bodies, names,
  phones and emails are never logged. Messages of database exceptions are dropped, because
  they can quote row values; only their class names are logged.

## Build, test, run

```sh
mvn -B test                  # unit, web (MockMvc) and contract tests; no Docker, no network
mvn -B test -Pintegration    # database tests against PostgreSQL 16 (Testcontainers, needs Docker)
mvn -B package -DskipTests   # target/kervan-core.jar
```

Without a local Maven:

```sh
docker run --rm -v "$PWD:/src" -v kervan-core-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-21 mvn -B test
```

The image follows SPEC §11. The first stage sees only `pom.xml` and resolves every dependency
and plugin. The sources are then compiled in a `RUN --network=none` step. The runtime is
`eclipse-temurin:21-jre`, runs as uid 10001 and exposes 8080. Tests run in CI, not in the image
build.

```sh
docker build -t kervan-core .
docker network create kervan
docker run -d --network kervan --network-alias postgres \
  -e POSTGRES_DB=kervan -e POSTGRES_USER=kervan_core -e POSTGRES_PASSWORD=kervan_core postgres:16
docker run --rm --network kervan -p 8080:8080 kervan-core
curl localhost:8080/actuator/health                     # {"status":"UP"}
curl -H 'X-Service-Token: svc-dev-token' localhost:8080/api/v1/shipments/KRV10000001
```

In Kervan, `kervan-system/compose` builds this image and applies the seed data after the
migrations.

## Layout

```text
src/main/java/kervan/core/
  domain/    records, enums, RescheduleRules, CoreException (sealed: one subclass per error code)
  service/   ShipmentService, TicketService (transactions, rule order)
  store/     ShipmentStore, TicketStore and their JdbcClient implementations
  web/       controllers, error mapping, ServiceTokenFilter, RequestLogFilter
  logging/   JSON-lines formatter and appender
src/test/java/kervan/core/
  support/   InMemoryStore and fixtures (stand-ins for PostgreSQL)
  store/     DatabaseIntegrationTest (@Tag("integration"))
```
