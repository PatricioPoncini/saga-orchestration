# Mapa: concepto del patrón Saga → código → escenario

Referencia pensada para escribir el blog: cada concepto con el archivo que lo implementa y el escenario
de la UI que lo muestra.

| Concepto | Dónde está en el código | Escenario |
|---|---|---|
| **Saga orquestada**: un coordinador central decide el próximo paso | `saga-orchestrator/.../domain/saga/SagaEngine.kt` | todos |
| **Definición declarativa** de pasos + compensaciones | `saga-orchestrator/.../domain/order/CreateOrderSaga.kt` | todos |
| **Transacción local** por paso (DB propia, sin 2PC) | `*/application/service/*Service.kt` + `IdempotentMessageHandler` (`messaging/.../Inbox.kt`) | 1 |
| **Transacciones compensatorias** | `Payment.refund()`, `Order.reject()` | 2, 3 |
| **Compensación en orden inverso** | `SagaEngine.compensateFrom()` | 3 |
| **Compensable / Pivot / Retriable** | `StepKind` en `SagaDefinition.kt`; `RESERVE_STOCK` es el pivot | 3 |
| **Falta de aislamiento + semantic lock** | `OrderStatus.PENDING` (`order-service/.../domain/Order.kt`) | tablas en vivo |
| **Transactional Outbox** (DB + mensaje atómico) | `messaging/.../Outbox.kt` (`Outbox` + `OutboxRelay`) | detalle técnico en la línea de tiempo |
| **Entrega at-least-once → consumidor idempotente** | `messaging/.../Inbox.kt` (tabla `processed_messages`) | 4 |
| **Idempotencia de negocio** (reintentos con otro messageId) | `PaymentService.authorize()`, `InventoryService.reserve()` | 5 |
| **Correlación de respuestas** y descarte de respuestas tardías | `SagaEngine.onReply()` (compara `commandType`) | 5 |
| **Timeouts y reintentos** del orquestador | `SagaEngine.onTimeout()` + `adapter/in/scheduling/TimeoutScheduler.kt` | 5, 6 |
| **Intervención manual** cuando se agotan los reintentos | `SagaStatus.FAILED` | 6 (sin levantar inventory) |
| **Estado de la saga persistido** (sobrevive reinicios) | `saga_instance` + `JdbcSagaRepository.kt` | `docker compose restart saga-orchestrator` en plena saga |
| **Mensajería durable** | colas durables de RabbitMQ (`MessagingAutoConfiguration.kt`) | 6 |
| **Dead letter queue** | `saga.dlx` → `saga.dead-letters` | mensajes que fallan 3 veces |
| **Arquitectura hexagonal verificada** | `*/src/test/.../HexagonalArchitectureTest.kt` | `./gradlew test` |

## Decisiones de diseño (y alternativas para mencionar en el blog)

- **Orquestación vs. coreografía**: elegimos orquestación porque deja la lógica de la saga en un solo
  lugar (`CreateOrderSaga.kt`) y es más fácil de visualizar. En coreografía cada servicio reacciona a
  eventos de los demás: hay menos acoplamiento a un coordinador, pero el flujo queda disperso.
- **Orquestador como servicio separado**: también podría vivir dentro de order-service (lo hace
  Eventuate Tram). Lo separamos para que se vea claro quién coordina y quién participa.
- **Un Postgres con varias bases**: por simplicidad. Lógicamente es *database per service*:
  ningún servicio lee la base de otro.
- **Librería `messaging` compartida**: contiene los contratos de mensajes y la infraestructura
  (outbox/inbox). En producción los contratos suelen versionarse aparte (schemas, AsyncAPI).
- **Las trazas de la UI** (`saga.trace`) no son parte del patrón: son observabilidad para la demo.
