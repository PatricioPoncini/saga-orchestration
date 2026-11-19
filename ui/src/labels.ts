import type { SagaStatus, StepState } from './types'

export type Tone = 'ok' | 'fail' | 'comp' | 'strong' | 'dim' | ''

export const SAGA_STATUS: Record<SagaStatus, { label: string; tone: Tone }> = {
  RUNNING: { label: 'running', tone: 'strong' },
  COMPENSATING: { label: 'compensating', tone: 'comp' },
  COMPLETED: { label: 'completed', tone: 'ok' },
  COMPENSATED: { label: 'compensated', tone: 'comp' },
  FAILED: { label: 'failed', tone: 'fail' },
}

export const STEP_STATE: Record<StepState, string> = {
  PENDING: 'pending',
  RUNNING: 'running…',
  SUCCEEDED: 'ok',
  FAILED: 'failed',
  COMPENSATING: 'undoing…',
  COMPENSATED: 'undone',
  SKIPPED: 'not executed',
}

export const KIND: Record<string, string> = {
  COMPENSATABLE: 'compensable',
  PIVOT: 'pivot',
  RETRIABLE: 'retriable',
}

export const SERVICE: Record<string, string> = {
  order: 'order-service',
  payment: 'payment-service',
  inventory: 'inventory-service',
}
