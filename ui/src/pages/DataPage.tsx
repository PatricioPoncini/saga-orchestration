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

const scenarioTitle = (id: string | null) => SCENARIOS.find((s) => s.id === id)?.title ?? 'order'

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
      onError('Cannot reach the orchestrator. Are the services running? (docker compose up)')
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
    document.getElementById('explorer')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <>
      <section className="hero">
        <p>Data & messages</p>
        <h1>Four databases, zero joins.</h1>
        <p className="lead">
          Each service owns its data and <b>nobody reads another service's database</b>. The only way to find out about
          something is a message: it is written to the <b>outbox</b> in the same transaction, travels through <b>RabbitMQ</b> and on the
          other side it is recorded in <b>processed_messages</b> so it is not processed twice.
        </p>
      </section>

      <DataMap databases={databases} selected={selected} onSelect={select} />

      <BrokerFlow queues={queues} />

      <section aria-label="Message journey" className="journey-section">
        <div className="journey-head">
          <h2 className="section-title">The journey of each message in your saga</h2>
          {sagas.length > 0 && (
            <label className="field saga-picker">
              Your sagas
              <select value={sagaId ?? ''} onChange={(e) => navigate(`/data?saga=${e.target.value}`)}>
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
            You haven't run any scenario from this browser yet. <Link href="/">Pick one in the live saga →</Link>
          </p>
        ) : (
          <>
            <ol className="journey-steps" aria-label="How a message travels">
              <li><b>1</b><span><span className="strong">It is written to the outbox</span> of the sender, in the same transaction as the business change.</span></li>
              <li><b>2</b><span><span className="strong">The relay publishes it</span> to RabbitMQ (exchange + routing key).</span></li>
              <li><b>3</b><span><span className="strong">The receiver processes it</span> and records it in its inbox to ignore duplicates.</span></li>
            </ol>
            <div className="journey-legend">
              <span><i className="lg outbox" />waiting in the outbox (1 → 2)</span>
              <span><i className="lg transit" />travelling / in the queue (2 → 3)</span>
              <span><i className="lg dot" />processed</span>
              {saga && (
                <span className="dim">
                  Saga <span className="mono strong">{saga.id.slice(0, 8)}</span> ·{' '}
                  <span className={SAGA_STATUS[saga.status].tone}>{SAGA_STATUS[saga.status].label}</span> · {journeys.length} messages ·
                  click a row to see the details
                </span>
              )}
            </div>
            <MessageJourney journeys={journeys} />
          </>
        )}
      </section>

      <div>
        <h2 className="section-title" style={{ marginBottom: 14 }}>Table explorer</h2>
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
