import type { ServiceKey } from './types'

export const SERVICES: { key: ServiceKey; name: string; queues: string[] }[] = [
  { key: 'order', name: 'order-service', queues: ['order.commands'] },
  { key: 'orchestrator', name: 'saga-orchestrator', queues: ['orchestrator.commands', 'orchestrator.replies', 'orchestrator.trace'] },
  { key: 'payment', name: 'payment-service', queues: ['payment.commands'] },
  { key: 'inventory', name: 'inventory-service', queues: ['inventory.commands'] },
]

export const TABLE_INFO: Record<string, string> = {
  orders: 'Las órdenes. PENDING mientras la saga está en curso: es un bloqueo semántico.',
  payments: 'Pagos autorizados, rechazados o reembolsados (la compensación).',
  products: 'Catálogo y stock disponible / reservado.',
  reservations: 'Una reserva (o rechazo) por orden: idempotencia de negocio ante reintentos.',
  saga_instance: 'Estado persistido de cada saga: el orquestador puede reiniciarse sin perderlas.',
  saga_timeline: 'Historial que ves en la línea de tiempo (observabilidad, no es parte del patrón).',
  outbox: 'Mensajes escritos en la MISMA transacción que el cambio de negocio. Un relay los publica en RabbitMQ.',
  processed_messages: 'Inbox: ids de mensajes ya procesados. Si llega un duplicado, se ignora.',
}

export const CATEGORY_LABEL: Record<string, string> = {
  domain: 'negocio',
  saga: 'saga',
  messaging: 'mensajería',
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
