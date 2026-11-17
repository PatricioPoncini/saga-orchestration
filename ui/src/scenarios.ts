import { NO_FAULTS, type Faults } from './types'

export interface Scenario {
  id: string
  title: string
  concept: string
  description: string
  faults: Faults
  order: { productId: string; quantity: number }
}

export const SCENARIOS: Scenario[] = [
  {
    id: 'happy',
    title: 'Camino feliz',
    concept: 'Secuencia de transacciones locales',
    description: 'Payment autoriza, Inventory reserva y la orden se aprueba. Saga COMPLETED.',
    faults: NO_FAULTS,
    order: { productId: 'p-1', quantity: 1 },
  },
  {
    id: 'payment-rejected',
    title: 'Pago rechazado',
    concept: 'Compensación de un solo paso',
    description: '3 monitores = $1350, supera el límite de crédito de $1000. Se compensa la orden con RejectOrder.',
    faults: NO_FAULTS,
    order: { productId: 'p-2', quantity: 3 },
  },
  {
    id: 'out-of-stock',
    title: 'Sin stock',
    concept: 'Compensaciones en orden inverso',
    description: 'El pago se autoriza pero la silla no tiene stock (falla el pivot). Se ejecuta RefundPayment y después RejectOrder.',
    faults: NO_FAULTS,
    order: { productId: 'p-3', quantity: 1 },
  },
  {
    id: 'duplicates',
    title: 'Mensajes duplicados',
    concept: 'Idempotencia (Inbox)',
    description: 'El orquestador publica cada comando dos veces con el mismo messageId. Los participantes procesan sólo el primero.',
    faults: { ...NO_FAULTS, duplicateCommands: true },
    order: { productId: 'p-4', quantity: 2 },
  },
  {
    id: 'slow-payment',
    title: 'Payment lento',
    concept: 'Timeout, reintento e idempotencia de negocio',
    description: 'Payment tarda 14 s y el timeout es de 10 s, así que el orquestador reenvía el comando. La respuesta tardía se descarta.',
    faults: { ...NO_FAULTS, paymentDelayMs: 14000 },
    order: { productId: 'p-1', quantity: 1 },
  },
  {
    id: 'inventory-down',
    title: 'Inventory caído',
    concept: 'Mensajería durable / recuperación',
    description: 'Inventory no acepta los mensajes de tu saga: esperan en la cola de reintento de RabbitMQ mientras el orquestador reenvía. Levantalo antes de ~50 s y la saga continúa; si no, termina FAILED.',
    faults: { ...NO_FAULTS, inventoryDown: true },
    order: { productId: 'p-4', quantity: 1 },
  },
]
