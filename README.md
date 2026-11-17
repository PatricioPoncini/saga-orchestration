# Saga Pattern: demo con orquestación

Implementación didáctica del [patrón Saga](https://microservices.io/patterns/data/saga.html) con
**Kotlin + Spring Boot + arquitectura hexagonal + RabbitMQ**, con una **UI web** para ver cómo avanza
cada saga, cómo se compensa cuando algo falla y cómo se comporta ante fallas de infraestructura.

## Levantarlo en tu máquina

### Requisitos

- **Docker** con **Docker Compose v2** (Docker Desktop en Mac/Windows, o Docker Engine en Linux).
- Unos **4 GB de RAM libres** para Docker: son 4 servicios Java, Postgres y RabbitMQ.
- Los puertos **3000, 5432, 5672, 8081–8084 y 15672** libres.

No hace falta tener Java, Gradle ni Node instalados: todo se compila dentro de Docker.

### Paso a paso

```bash
git clone https://github.com/PatricioPoncini/saga-orchestration.git
cd saga-orchestration
docker compose up --build -d
```

La primera vez tarda unos minutos porque descarga las imágenes y compila los servicios. Para saber
cuándo está listo:

```bash
docker compose ps
```

Cuando `postgres` y `rabbitmq` figuran como `healthy` y los servicios como `running`, abrí
**http://localhost:3000** y probá los escenarios desde la UI.

Con `make` es lo mismo: `make up` (levanta), `make logs` (logs de los servicios), `make down` (baja todo).

### Qué queda corriendo

| Qué | URL |
|---|---|
| UI de la demo | http://localhost:3000 |
| RabbitMQ management | http://localhost:15672 (guest / guest) |
| order-service | http://localhost:8081 |
| payment-service | http://localhost:8082 |
| inventory-service | http://localhost:8083 |
| saga-orchestrator | http://localhost:8084 |
| Postgres | `localhost:5432` (saga / saga): bases `orders`, `payments`, `inventory`, `orchestrator` |

### Comprobar que todo funciona

```bash
./scripts/smoke-test.sh
```

Corre los escenarios contra los servicios levantados y muestra ✅ o ❌ para cada uno.

### Bajar todo

```bash
docker compose down      # baja los contenedores y conserva los datos
docker compose down -v   # baja todo y borra los datos
```

### Si algo no arranca

- **Un puerto está ocupado**: `lsof -i :3000` (o el puerto que sea) para ver qué lo usa, y cerralo.
- **Un servicio se reinicia en loop**: `docker compose logs -f <servicio>` para ver el error.
- **Cambiaste una migración SQL que ya se había aplicado**: `docker compose down -v` y volvé a levantar.

### Reinicio automático de la demo

Como es una demo, cada servicio **vacía todas sus tablas** (no las borra) y purga sus colas de RabbitMQ cada
15 minutos, alineado al reloj (:00, :15, :30, :45), así los cuatro se reinician en el mismo momento.
Inventory vuelve a cargar el catálogo inicial. La UI muestra cuánto falta para el próximo reinicio.

```bash
DEMO_RESET_INTERVAL=5m docker compose up -d   # cada 5 minutos
DEMO_RESET_INTERVAL=0  docker compose up -d   # nunca
```

También se puede fijar en un archivo `.env` junto al `docker-compose.yml` (`DEMO_RESET_INTERVAL=1h`).

## El caso de uso: `CreateOrderSaga`

Un cliente crea una orden. Para aprobarla hay que **cobrar** (payment-service) y **reservar stock**
(inventory-service). Cada servicio tiene **su propia base de datos**, así que no hay una transacción ACID
que abarque todo: la saga reemplaza esa transacción distribuida por una **secuencia de transacciones
locales**, y cada paso que se puede deshacer tiene una **transacción compensatoria**.

| # | Paso | Participante | Tipo | Compensación |
|---|---|---|---|---|
| 1 | `CREATE_ORDER` (orden en `PENDING`) | order-service | compensable | `RejectOrder` |
| 2 | `AUTHORIZE_PAYMENT` | payment-service | compensable | `RefundPayment` |
| 3 | `RESERVE_STOCK` | inventory-service | **pivot** | — |
| 4 | `APPROVE_ORDER` | order-service | retriable | — |

```mermaid
sequenceDiagram
    participant UI
    participant O as order-service
    participant S as saga-orchestrator
    participant P as payment-service
    participant I as inventory-service
    UI->>O: POST /orders
    O->>O: tx local: Order PENDING + outbox
    O-)S: StartCreateOrderSaga
    S-)P: AuthorizePayment
    P-)S: PaymentAuthorized
    S-)I: ReserveStock
    alt hay stock
        I-)S: StockReserved
        S-)O: ApproveOrder
        O-)S: OrderApproved  (saga COMPLETED)
    else sin stock
        I-)S: StockUnavailable
        S-)P: RefundPayment   (compensación)
        P-)S: PaymentRefunded
        S-)O: RejectOrder     (compensación)
        O-)S: OrderRejected   (saga COMPENSATED)
    end
```

## Escenarios guiados (botones en la UI)

| # | Escenario | Concepto que muestra | Resultado |
|---|---|---|---|
| 1 | Camino feliz | Secuencia de transacciones locales | `COMPLETED` |
| 2 | Pago rechazado ($1350 > límite de $1000) | Compensación de un solo paso | `COMPENSATED` |
| 3 | Sin stock (falla el pivot) | Compensaciones en **orden inverso** | `COMPENSATED` |
| 4 | Mensajes duplicados | Consumidor idempotente (Inbox) | `COMPLETED`, sin efectos duplicados |
| 5 | Payment lento (14 s vs. timeout de 10 s) | Timeout, reintento, idempotencia de negocio, respuesta tardía descartada | `COMPLETED` |
| 6 | Inventory caído | Mensajería durable: los comandos esperan en una cola de reintento (TTL) | `COMPLETED` si vuelve a tiempo, si no `FAILED` |

Cada escenario manda sus fallas junto con la orden (por ejemplo `{"faults": {"paymentDelayMs": 14000}}`).
Viajan como headers de los mensajes de **esa** saga, así que se pueden correr escenarios distintos
en paralelo (por ejemplo, en varias pestañas) sin que se afecten entre sí: "Payment lento" demora sólo las respuestas de tu saga (outbox con
`available_at`) y "Inventory caído" rechaza sólo tus mensajes, que esperan en `inventory.retry` y vuelven solos.

En la UI vas a ver:
- **Diagrama** de la saga: pasos hacia adelante y compensaciones (abajo, en rojo, de derecha a izquierda).
- **Línea de tiempo**: quién le envía qué a quién, decisiones del orquestador, transacciones locales, duplicados ignorados.
- Una saga a la vez: hasta que la saga en curso no termina, no se puede iniciar otro escenario.
- En "Inventory caído", un botón para volver a levantar inventory mientras la saga espera.
- Las bases de cada servicio y el recorrido de cada mensaje están en la sección **Datos y mensajes** (`/datos`).

## Sección "Datos y mensajes" (`/datos`)

Muestra las bases de cada servicio y cómo se comunican, sin que ningún servicio lea la base de otro:

- **Mapa**: las 4 bases (tablas de negocio, de la saga y de mensajería) conectadas sólo a través de RabbitMQ.
- **Viaje de cada mensaje** de una saga: `outbox` de quien envía → exchange → `processed_messages` de quien
  recibe, con los tiempos reales sacados de las tablas (latencia del relay, tiempo en la cola, duplicados).
- **Explorador de tablas**: columnas, tipos y últimas filas de cada tabla, filtrables por saga.

Cada servicio expone endpoints de inspección **de sólo lectura** sobre su propia base (sólo para la demo):

| Endpoint | Devuelve |
|---|---|
| `GET /inspect/database` | tablas, columnas, cantidad de filas y mensajes pendientes en el outbox |
| `GET /inspect/tables/{tabla}?limit=25&sagaId=` | últimas filas de una tabla |
| `GET /inspect/outbox?sagaId=` | mensajes que ese servicio envió en una saga |
| `GET /inspect/inbox?ids=a,b` | cuáles de esos mensajes ya procesó |

## Arquitectura

```
                    ┌──────────────┐  SSE / REST   ┌──────────┐
                    │ saga-        │◀──────────────│  UI      │ (React + nginx)
                    │ orchestrator │               └──────────┘
                    └──────┬───────┘
         comandos (saga.commands) │ ▲ respuestas (saga.replies)
                           ▼      │
   ┌───────────────┐ ┌───────────────┐ ┌─────────────────┐
   │ order-service │ │payment-service│ │inventory-service│
   └──────┬────────┘ └──────┬────────┘ └───────┬─────────┘
          ▼                 ▼                  ▼
       db orders        db payments        db inventory       (una DB por servicio)
```

Cada servicio sigue la **arquitectura hexagonal** (puertos y adaptadores):

```
dev/saga/<servicio>/
  domain/                 ← modelo y reglas de negocio. Kotlin puro.
  application/port/in/    ← casos de uso (lo que el mundo le puede pedir al servicio)
  application/port/out/   ← lo que el servicio necesita del mundo (repositorio, mensajería)
  application/service/    ← implementación de los casos de uso. Sin Spring.
  adapter/in/             ← web (REST), messaging (RabbitMQ listeners), scheduling
  adapter/out/            ← persistence (JDBC), messaging (Outbox)
  config/                 ← cableado de Spring
```

Un test de **ArchUnit** (`HexagonalArchitectureTest`) en cada servicio rompe el build si `domain` o
`application` dependen de Spring o de algún adaptador.

## Mapa concepto → código

Ver [docs/conceptos.md](docs/conceptos.md).

## Desarrollo

```bash
./gradlew test                     # tests de dominio + ArchUnit (JDK 17+)
./scripts/smoke-test.sh            # escenarios end-to-end contra docker compose
cd ui && npm install && npm run dev  # UI con hot reload en :3000 (usa los servicios de docker)
```
