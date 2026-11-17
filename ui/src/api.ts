import type {
  DatabaseInfo,
  Faults,
  Order,
  OutageStatus,
  OutboxMessage,
  ProcessedMessage,
  Product,
  QueueStatus,
  SagaDetail,
  SagaView,
  ServiceKey,
  StepView,
  TableRows,
} from './types'

export class HttpError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
  }
}

async function http<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  })
  if (!res.ok) throw new HttpError(res.status, `${init?.method ?? 'GET'} ${url} → ${res.status}`)
  return res.json() as Promise<T>
}

const post = <T>(url: string, body: unknown) => http<T>(url, { method: 'POST', body: JSON.stringify(body) })
const enc = encodeURIComponent

export const api = {
  saga: (id: string) => http<SagaDetail>(`/api/orchestrator/sagas/${enc(id)}`),
  sagasByIds: (ids: string[]) =>
    ids.length ? http<SagaView[]>(`/api/orchestrator/sagas?ids=${ids.map(enc).join(',')}`) : Promise.resolve([]),
  definition: () => http<StepView[]>('/api/orchestrator/sagas/definition'),
  products: () => http<Product[]>('/api/inventory/products'),

  createOrder: (body: { customerId: string; productId: string; quantity: number; amount: number; faults: Faults }) =>
    post<Order>('/api/order/orders', body),

  outage: (sagaId: string) => http<OutageStatus>(`/api/inventory/outages/${enc(sagaId)}`),
  recover: (sagaId: string) => post<OutageStatus>(`/api/inventory/outages/${enc(sagaId)}/recover`, {}),

  queues: () => http<QueueStatus[]>('/api/orchestrator/inspect/queues'),
  database: (s: ServiceKey) => http<DatabaseInfo>(`/api/${s}/inspect/database`),
  tableRows: (s: ServiceKey, table: string, filter?: { sagaId?: string; orderId?: string }) => {
    const params = new URLSearchParams({ limit: '25' })
    if (filter?.sagaId) params.set('sagaId', filter.sagaId)
    if (filter?.orderId) params.set('orderId', filter.orderId)
    return http<TableRows>(`/api/${s}/inspect/tables/${enc(table)}?${params}`)
  },
  outbox: (s: ServiceKey, sagaId: string) => http<OutboxMessage[]>(`/api/${s}/inspect/outbox?sagaId=${enc(sagaId)}`),
  inbox: (s: ServiceKey, ids: string[]) =>
    ids.length ? http<ProcessedMessage[]>(`/api/${s}/inspect/inbox?ids=${ids.map(enc).join(',')}`) : Promise.resolve([]),
}
