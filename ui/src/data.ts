import type { ServiceKey } from './types'

export const SERVICES: { key: ServiceKey; name: string; queues: string[] }[] = [
  { key: 'order', name: 'order-service', queues: ['order.commands'] },
  { key: 'orchestrator', name: 'saga-orchestrator', queues: ['orchestrator.commands', 'orchestrator.replies', 'orchestrator.trace'] },
  { key: 'payment', name: 'payment-service', queues: ['payment.commands'] },
  { key: 'inventory', name: 'inventory-service', queues: ['inventory.commands'] },
]

export const TABLE_INFO: Record<string, string> = {
  orders: 'The orders. PENDING while the saga is running: it works as a semantic lock.',
  payments: 'Authorized, rejected or refunded payments (the compensation).',
  products: 'Catalog and available / reserved stock.',
  reservations: 'One reservation (or rejection) per order: business idempotency for retries.',
  saga_instance: 'Persisted state of each saga: the orchestrator can restart without losing them.',
  saga_timeline: 'The history you see in the timeline (observability, not part of the pattern).',
  outbox: 'Messages written in the SAME transaction as the business change. A relay publishes them to RabbitMQ.',
  processed_messages: 'Inbox: ids of messages already processed. If a duplicate arrives, it is ignored.',
}

export const CATEGORY_LABEL: Record<string, string> = {
  domain: 'business',
  saga: 'saga',
  messaging: 'messaging',
}

export function targetOf(exchange: string, routingKey: string): ServiceKey {
  if (exchange === 'saga.replies') return 'orchestrator'
  return routingKey as ServiceKey
}

export const serviceName = (key: ServiceKey) => SERVICES.find((s) => s.key === key)?.name ?? key

export function ms(from: string | null, to: string | null): string | null {
  if (!from || !to) return null
  const diff = new Date(to).getTime() - new Date(from).getTime()
  return diff < 1000 ? `${Math.max(0, diff)} ms` : `${(diff / 1000).toFixed(1)} s`
}
