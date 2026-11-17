import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from '../api'
import { BrokerFlow } from '../components/data/BrokerFlow'
import { DataMap } from '../components/data/DataMap'
import { buildJourneys, MessageJourney } from '../components/data/MessageJourney'
import { TableExplorer } from '../components/data/TableExplorer'
import { SERVICES } from '../data'
import { SAGA_STATUS } from '../labels'
import { useSagaStream } from '../live'
import { keepOnly, mySagas } from '../mySagas'
import { Link, navigate } from '../router'
import { SCENARIOS } from '../scenarios'
import type { DatabaseInfo, OutboxMessage, ProcessedMessage, QueueStatus, SagaView, ServiceKey, TableRows } from '../types'

type Selection = { service: ServiceKey; table: string }

interface Props {
  resetTick: number
  sagaParam: string | null
  onError: (m: string | null) => void
  onLive: (live: boolean) => void
}

const scenarioTitle = (id: string | null) => SCENARIOS.find((s) => s.id === id)?.title ?? 'orden'

export function DataPage({ resetTick, sagaParam, onError, onLive }: Props) {
  const [sagas, setSagas] = useState<SagaView[]>([])
  const [loaded, setLoaded] = useState(false)
  const [databases, setDatabases] = useState<Partial<Record<ServiceKey, DatabaseInfo>>>({})
  const [outboxes, setOutboxes] = useState<Partial<Record<ServiceKey, OutboxMessage[]>>>({})
  const [inboxes, setInboxes] = useState<Partial<Record<ServiceKey, ProcessedMessage[]>>>({})
  const [selected, setSelected] = useState<Selection>({ service: 'order', table: 'orders' })
  const [rows, setRows] = useState<TableRows | null>(null)
  const [queues, setQueues] = useState<QueueStatus[]>([])
  const [onlySaga, setOnlySaga] = useState(true)

  const sagaId = sagaParam && sagas.some((s) => s.id === sagaParam) ? sagaParam : sagas[0]?.id ?? null
  const saga = sagas.find((s) => s.id === sagaId) ?? null
  const scenarios = useMemo(() => new Map(mySagas().map((s) => [s.id, s.scenarioId])), [sagas])
  const tick = useSagaStream(sagaId, onLive)

  const refreshMine = useCallback(async () => {
    try {
      const found = await api.sagasByIds(mySagas().map((s) => s.id))
      keepOnly(found.map((s) => s.id))
      setSagas(found)
      setLoaded(true)
      onError(null)
    } catch {
      onError('No se puede contactar al orquestador. ¿Están levantados los servicios? (docker compose up)')
    }
  }, [onError])

  const refreshStructure = useCallback(async () => {
    const results = await Promise.allSettled(SERVICES.map((s) => api.database(s.key)))
    const next: Partial<Record<ServiceKey, DatabaseInfo>> = {}
    results.forEach((r, i) => {
      if (r.status === 'fulfilled') next[SERVICES[i].key] = r.value
    })
    setDatabases(next)
  }, [])

  const refreshMessages = useCallback(async () => {
    if (!sagaId) {
      setOutboxes({})
      setInboxes({})
      return
    }
    const out = await Promise.all(SERVICES.map((s) => api.outbox(s.key, sagaId).catch(() => [] as OutboxMessage[])))
    const ids = [...new Set(out.flat().map((m) => m.messageId))]
    const inb = await Promise.all(SERVICES.map((s) => api.inbox(s.key, ids).catch(() => [] as ProcessedMessage[])))
    setOutboxes(Object.fromEntries(SERVICES.map((s, i) => [s.key, out[i]])))
    setInboxes(Object.fromEntries(SERVICES.map((s, i) => [s.key, inb[i]])))
  }, [sagaId])

  const refreshRows = useCallback(async () => {
    const filter = onlySaga && saga ? { sagaId: saga.id, orderId: saga.data.orderId } : undefined
    setRows(await api.tableRows(selected.service, selected.table, filter).catch(() => null))
  }, [selected, onlySaga, saga])

  useEffect(() => {
    refreshMine()
    refreshStructure()
  }, [resetTick, refreshMine, refreshStructure])

  useEffect(() => {
    refreshMessages()
    refreshRows()
  }, [tick, resetTick, refreshMessages, refreshRows])

  useEffect(() => {
    const fast = window.setInterval(() => {
      refreshMessages()
      refreshRows()
    }, 4000)
    const slow = window.setInterval(() => {
      refreshMine()
      refreshStructure()
    }, 10000)
    return () => {
      window.clearInterval(fast)
      window.clearInterval(slow)
    }
  }, [refreshMine, refreshStructure, refreshMessages, refreshRows])

  useEffect(() => {
    const load = () => api.queues().then(setQueues).catch(() => undefined)
    load()
    const poll = window.setInterval(load, 2000)
    return () => window.clearInterval(poll)
  }, [])

  const journeys = useMemo(() => buildJourneys(outboxes, inboxes), [outboxes, inboxes])

  const select = (service: ServiceKey, table: string) => {
    setSelected({ service, table })
    document.getElementById('explorador')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <>
      <section className="hero">
        <p>Datos y mensajes</p>
        <h1>Cuatro bases, ningún join.</h1>
        <p className="lead">
          Cada servicio es dueño de sus datos y <b>nadie lee la base de otro</b>. La única forma de enterarse de algo
          es un mensaje: se escribe en el <b>outbox</b> en la misma transacción, viaja por <b>RabbitMQ</b> y del otro
          lado se anota en <b>processed_messages</b> para no procesarlo dos veces.
        </p>
      </section>

      <DataMap databases={databases} selected={selected} onSelect={select} />

      <BrokerFlow queues={queues} />

      <section aria-label="Viaje de los mensajes" className="journey-section">
        <div className="journey-head">
          <h2 className="section-title">El viaje de cada mensaje de tu saga</h2>
          {sagas.length > 0 && (
            <label className="field saga-picker">
              Tus sagas
              <select value={sagaId ?? ''} onChange={(e) => navigate(`/datos?saga=${e.target.value}`)}>
                {sagas.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.id.slice(0, 8)} · {scenarioTitle(scenarios.get(s.id) ?? null)} · {SAGA_STATUS[s.status].label}
                  </option>
                ))}
              </select>
            </label>
          )}
        </div>

        {loaded && sagas.length === 0 ? (
          <p className="empty-state">
            Todavía no corriste ningún escenario desde este navegador. <Link href="/">Elegí uno en la saga en vivo →</Link>
          </p>
        ) : (
          <>
            <ol className="journey-steps" aria-label="Cómo viaja un mensaje">
              <li><b>1</b><span><span className="strong">Se escribe en el outbox</span> de quien envía, en la misma transacción que el cambio de negocio.</span></li>
              <li><b>2</b><span><span className="strong">El relay lo publica</span> en RabbitMQ (exchange + routing key).</span></li>
              <li><b>3</b><span><span className="strong">El destino lo procesa</span> y lo anota en su inbox para ignorar duplicados.</span></li>
            </ol>
            <div className="journey-legend">
              <span><i className="lg outbox" />esperando en el outbox (1 → 2)</span>
              <span><i className="lg transit" />viajando / en la cola (2 → 3)</span>
              <span><i className="lg dot" />procesado</span>
              {saga && (
                <span className="dim">
                  Saga <span className="mono strong">{saga.id.slice(0, 8)}</span> ·{' '}
                  <span className={SAGA_STATUS[saga.status].tone}>{SAGA_STATUS[saga.status].label}</span> · {journeys.length} mensajes ·
                  clic en una fila para ver el detalle
                </span>
              )}
            </div>
            <MessageJourney journeys={journeys} />
          </>
        )}
      </section>

      <div>
        <h2 className="section-title" style={{ marginBottom: 14 }}>Explorador de tablas</h2>
        <TableExplorer
          databases={databases}
          selected={selected}
          onSelect={(service, table) => setSelected({ service, table })}
          rows={rows}
          onlySaga={onlySaga}
          onOnlySaga={setOnlySaga}
          sagaId={sagaId}
          orderId={saga?.data.orderId ?? null}
        />
      </div>
    </>
  )
}
