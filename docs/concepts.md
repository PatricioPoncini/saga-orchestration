# Map: Saga pattern concept → code → scenario

A reference for writing the blog post: each concept with the file that implements it and the UI scenario
that shows it.

| Concept | Where it is in the code | Scenario |
|---|---|---|
| **Orchestrated saga**: a central coordinator decides the next step | `saga-orchestrator/.../domain/saga/SagaEngine.kt` | all |
| **Declarative definition** of steps + compensations | `saga-orchestrator/.../domain/order/CreateOrderSaga.kt` | all |
| **Local transaction** per step (own DB, no 2PC) | `*/application/service/*Service.kt` + `IdempotentMessageHandler` (`messaging/.../Inbox.kt`) | 1 |
| **Compensating transactions** | `Payment.refund()`, `Order.reject()` | 2, 3 |
| **Compensation in reverse order** | `SagaEngine.compensateFrom()` | 3 |
| **Compensable / Pivot / Retriable** | `StepKind` in `SagaDefinition.kt`; `RESERVE_STOCK` is the pivot | 3 |
| **Lack of isolation + semantic lock** | `OrderStatus.PENDING` (`order-service/.../domain/Order.kt`) | live tables |
| **Transactional Outbox** (DB + message, atomically) | `messaging/.../Outbox.kt` (`Outbox` + `OutboxRelay`) | technical detail in the timeline |
| **At-least-once delivery → idempotent consumer** | `messaging/.../Inbox.kt` (`processed_messages` table) | 4 |
| **Business idempotency** (retries with a different messageId) | `PaymentService.authorize()`, `InventoryService.reserve()` | 5 |
| **Reply correlation** and discarding late replies | `SagaEngine.onReply()` (compares `commandType`) | 5 |
| **Orchestrator timeouts and retries** | `SagaEngine.onTimeout()` + `adapter/in/scheduling/TimeoutScheduler.kt` | 5, 6 |
| **Manual intervention** when retries run out | `SagaStatus.FAILED` | 6 (without bringing inventory back) |
| **Persisted saga state** (survives restarts) | `saga_instance` + `JdbcSagaRepository.kt` | `docker compose restart saga-orchestrator` in the middle of a saga |
| **Durable messaging** | durable RabbitMQ queues (`MessagingAutoConfiguration.kt`) | 6 |
| **Dead letter queue** | `saga.dlx` → `saga.dead-letters` | messages that fail 3 times |
| **Verified hexagonal architecture** | `*/src/test/.../HexagonalArchitectureTest.kt` | `./gradlew test` |

## Design decisions (and alternatives to mention in the blog)

- **Orchestration vs. choreography**: we chose orchestration because it keeps the saga logic in one
  place (`CreateOrderSaga.kt`) and it's easier to visualize. In choreography each service reacts to
  the other services' events: there's less coupling to a coordinator, but the flow is spread out.
- **Orchestrator as a separate service**: it could also live inside order-service (Eventuate Tram
  does that). We kept it separate so it's clear who coordinates and who participates.
- **One Postgres with several databases**: for simplicity. Logically it's *database per service*:
  no service reads another service's database.
- **Shared `messaging` library**: it contains the message contracts and the infrastructure
  (outbox/inbox). In production, contracts are usually versioned separately (schemas, AsyncAPI).
- **The UI traces** (`saga.trace`) are not part of the pattern: they're observability for the demo.
