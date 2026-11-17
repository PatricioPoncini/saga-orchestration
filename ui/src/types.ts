export type SagaStatus = 'RUNNING' | 'COMPENSATING' | 'COMPLETED' | 'COMPENSATED' | 'FAILED'
export type StepState = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'COMPENSATING' | 'COMPENSATED' | 'SKIPPED'
export type StepKind = 'COMPENSATABLE' | 'PIVOT' | 'RETRIABLE'

export interface StepView {
  name: string
  participant: string
  kind: StepKind
  description: string
  compensation: string | null
  state: StepState
}

export interface SagaView {
  id: string
  type: string
  status: SagaStatus
  data: {
    orderId: string
    customerId: string
    productId: string
    quantity: number
    amount: number
    paymentId: string | null
  }
  steps: StepView[]
  awaiting: { commandType: string; participant: string; compensating: boolean; attempts: number; sentAt: string } | null
  failureReason: string | null
  createdAt: string
  updatedAt: string
}

export interface TimelineEntry {
  id: number
  sagaId: string
  occurredAt: string
  kind: string
  actor: string
  target: string | null
  messageType: string | null
  description: string
}

export interface SagaDetail {
  saga: SagaView
  timeline: TimelineEntry[]
}

export interface Order {
  id: string
  sagaId: string
  customerId: string
  productId: string
  quantity: number
  amount: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  rejectionReason: string | null
  updatedAt: string
}

export interface Product {
  id: string
  name: string
  price: number
  available: number
  reserved: number
}

export type ServiceKey = 'order' | 'payment' | 'inventory' | 'orchestrator'

export interface ColumnInfo {
  name: string
  type: string
  nullable: boolean
  primaryKey: boolean
}

export interface TableInfo {
  name: string
  category: 'domain' | 'saga' | 'messaging'
  columns: ColumnInfo[]
  rowCount: number
  pending: number | null
}

export interface DatabaseInfo {
  service: string
  database: string
  tables: TableInfo[]
}

export interface TableRows {
  table: string
  columns: ColumnInfo[]
  rows: Record<string, string | number | boolean | null>[]
  total: number
  filteredBySaga: boolean
}

export interface QueueStatus {
  name: string
  messages: number
  consumers: number
}

export interface OutboxMessage {
  id: number
  messageId: string
  sagaId: string
  type: string
  exchange: string
  routingKey: string
  createdAt: string
  publishedAt: string | null
}

export interface ProcessedMessage {
  messageId: string
  type: string
  processedAt: string
}

export interface Faults {
  paymentRejected: boolean
  paymentDelayMs: number
  inventoryDown: boolean
  duplicateCommands: boolean
}

export const NO_FAULTS: Faults = {
  paymentRejected: false,
  paymentDelayMs: 0,
  inventoryDown: false,
  duplicateCommands: false,
}

export interface OutageStatus {
  down: boolean
  since: string | null
  recoversAt: string | null
}
