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
    title: 'Happy path',
    concept: 'Sequence of local transactions',
    description: 'Payment authorizes, Inventory reserves and the order is approved. Saga COMPLETED.',
    faults: NO_FAULTS,
    order: { productId: 'p-1', quantity: 1 },
  },
  {
    id: 'payment-rejected',
    title: 'Payment rejected',
    concept: 'Single-step compensation',
    description: '3 monitors = $1350, which is over the $1000 credit limit. The order is compensated with RejectOrder.',
    faults: NO_FAULTS,
    order: { productId: 'p-2', quantity: 3 },
  },
  {
    id: 'out-of-stock',
    title: 'Out of stock',
    concept: 'Compensations in reverse order',
    description: 'The payment is authorized but the chair is out of stock (the pivot fails). RefundPayment runs, and then RejectOrder.',
    faults: NO_FAULTS,
    order: { productId: 'p-3', quantity: 1 },
  },
  {
    id: 'duplicates',
    title: 'Duplicate messages',
    concept: 'Idempotency (Inbox)',
    description: 'The orchestrator publishes every command twice with the same messageId. Participants only process the first one.',
    faults: { ...NO_FAULTS, duplicateCommands: true },
    order: { productId: 'p-4', quantity: 2 },
  },
  {
    id: 'slow-payment',
    title: 'Slow payment',
    concept: 'Timeout, retry and business idempotency',
    description: 'Payment takes 14 s and the timeout is 10 s, so the orchestrator sends the command again. The late reply is discarded.',
    faults: { ...NO_FAULTS, paymentDelayMs: 14000 },
    order: { productId: 'p-1', quantity: 1 },
  },
  {
    id: 'inventory-down',
    title: 'Inventory down',
    concept: 'Durable messaging / recovery',
    description: 'Inventory does not accept the messages of your saga: they wait in the RabbitMQ retry queue while the orchestrator resends. Bring it back within ~50 s and the saga continues; otherwise it ends as FAILED.',
    faults: { ...NO_FAULTS, inventoryDown: true },
    order: { productId: 'p-4', quantity: 1 },
  },
]
