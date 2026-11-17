import type { SagaStatus, StepState } from './types'

export type Tone = 'ok' | 'fail' | 'comp' | 'strong' | 'dim' | ''

export const SAGA_STATUS: Record<SagaStatus, { label: string; tone: Tone }> = {
  RUNNING: { label: 'en curso', tone: 'strong' },
  COMPENSATING: { label: 'compensando', tone: 'comp' },
  COMPLETED: { label: 'completada', tone: 'ok' },
  COMPENSATED: { label: 'compensada', tone: 'comp' },
  FAILED: { label: 'fallida', tone: 'fail' },
}

export const STEP_STATE: Record<StepState, string> = {
  PENDING: 'pendiente',
  RUNNING: 'ejecutando…',
  SUCCEEDED: 'ok',
  FAILED: 'falló',
  COMPENSATING: 'deshaciendo…',
  COMPENSATED: 'deshecho',
  SKIPPED: 'no se ejecuta',
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
