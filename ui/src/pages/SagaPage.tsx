import { useCallback, useEffect, useRef, useState } from 'react'
import { api, HttpError } from '../api'
import { Glossary } from '../components/Glossary'
import { SagaDiagram } from '../components/SagaDiagram'
import { Sidebar } from '../components/Sidebar'
import { Timeline } from '../components/Timeline'
import { SAGA_STATUS } from '../labels'
import { useSagaStream } from '../live'
import { rememberSaga } from '../mySagas'
import { Link } from '../router'
import { readLock, useSagaLock } from '../sagaLock'
import { SCENARIOS, type Scenario } from '../scenarios'
import type { OutageStatus, Product, SagaDetail, StepView } from '../types'

interface Props {
  resetTick: number
  onError: (message: string | null) => void
  onLive: (live: boolean) => void
}

const pad = (n: number) => String(n).padStart(2, '0')
const remaining = (iso: string | null, now: number) =>
  iso ? Math.max(0, Math.round((new Date(iso).getTime() - now) / 1000)) : null

export function SagaPage({ resetTick, onError, onLive }: Props) {
  const initialLock = useRef(readLock()).current
  const [definition, setDefinition] = useState<StepView[]>([])
  const [products, setProducts] = useState<Product[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(initialLock?.sagaId ?? null)
  const [scenario, setScenario] = useState<Scenario | null>(
    () => SCENARIOS.find((s) => s.id === initialLock?.scenarioId) ?? null,
  )
  const [detail, setDetail] = useState<SagaDetail | null>(null)
  const [missing, setMissing] = useState(false)
  const [outage, setOutage] = useState<OutageStatus | null>(null)
  const [busy, setBusy] = useState(false)
  const [now, setNow] = useState(Date.now())

  const selectedRef = useRef(selectedId)
  selectedRef.current = selectedId
  const scenarioRef = useRef(scenario)
  scenarioRef.current = scenario
  const shownRef = useRef<string | null>(null)

  const tick = useSagaStream(selectedId, onLive)
  const { lockedSagaId, lock } = useSagaLock(detail?.saga ?? null, missing)
  const locked = busy || lockedSagaId !== null

  useEffect(() => {
    api.definition().then(setDefinition).catch(() => undefined)
    api.products().then(setProducts).catch(() => undefined)
  }, [])

  const refresh = useCallback(async () => {
    const id = selectedRef.current
    if (!id) return
    try {
      const d = await api.saga(id)
      if (selectedRef.current !== id) return
      shownRef.current = id
      setMissing(false)
      setDetail(d)
      onError(null)
      if (scenarioRef.current?.faults.inventoryDown) {
        api.outage(id).then((o) => selectedRef.current === id && setOutage(o)).catch(() => undefined)
      }
    } catch (e) {
      if (selectedRef.current !== id) return
      if (e instanceof HttpError && e.status === 404) {
        setMissing(true)
        if (shownRef.current === id) {
          setSelectedId(null)
          setDetail(null)
          setScenario(null)
          setOutage(null)
        }
      } else {
        onError('No se puede contactar al orquestador. ¿Están levantados los servicios? (docker compose up)')
      }
    }
  }, [onError])

  useEffect(() => {
    refresh()
  }, [tick, resetTick, selectedId, refresh])

  useEffect(() => {
    const poll = window.setInterval(refresh, 3000)
    return () => window.clearInterval(poll)
  }, [refresh])

  useEffect(() => {
    if (!outage?.down) return
    const clock = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(clock)
  }, [outage?.down])

  const start = async (s: Scenario) => {
    if (locked) return
    setBusy(true)
    setScenario(s)
    setDetail(null)
    setOutage(null)
    try {
      const catalog = products.length ? products : await api.products()
      const product = catalog.find((p) => p.id === s.order.productId)
      if (!product) throw new Error('producto inexistente')
      const created = await api.createOrder({
        customerId: 'cliente-1',
        productId: s.order.productId,
        quantity: s.order.quantity,
        amount: product.price * s.order.quantity,
        faults: s.faults,
      })
      lock(created.sagaId, s.id)
      rememberSaga(created.sagaId, s.id)
      setSelectedId(created.sagaId)
    } catch {
      onError('No se pudo iniciar el escenario')
    } finally {
      setBusy(false)
    }
  }

  const recover = async () => {
    if (!selectedId) return
    setOutage(await api.recover(selectedId).catch(() => outage))
  }

  const saga = detail?.saga ?? null
  const steps = saga?.steps ?? definition
  const status = saga ? SAGA_STATUS[saga.status] : null
  const product = saga ? products.find((p) => p.id === saga.data.productId) : null
  const running = saga ? saga.status === 'RUNNING' || saga.status === 'COMPENSATING' : false
  const autoRecoverIn = remaining(outage?.recoversAt ?? null, now)

  return (
    <>
      <section className="hero">
        <p className="eyebrow">Demo interactiva · Patrón Saga con orquestación</p>
        <h1>Saga orquestada, en vivo.</h1>
        <p className="lead">
          Una saga reemplaza una transacción distribuida por una secuencia de transacciones locales. En esta demo, un
          orquestador coordina la creación de una orden entre tres servicios independientes, <b>order</b>,{' '}
          <b>payment</b> e <b>inventory</b>, cada uno con su propia base de datos. Si un paso falla, ejecuta las
          compensaciones en orden inverso para dejar el sistema consistente.
        </p>

        <ol className="guide" aria-label="Cómo usar la demo">
          <li>
            <span className="guide-num">01</span>
            <b>Elegí un escenario</b>
            <span>En la lista de la izquierda: el camino feliz o una falla concreta (pago rechazado, falta de stock, un servicio caído…).</span>
          </li>
          <li>
            <span className="guide-num">02</span>
            <b>Seguí la saga</b>
            <span>El diagrama muestra en qué paso está y si compensa. Debajo, la línea de tiempo detalla cada mensaje y cada decisión del orquestador.</span>
          </li>
          <li>
            <span className="guide-num">03</span>
            <b>Mirá los datos</b>
            <span>
              En <Link href="/datos">Datos y mensajes</Link> se ven las tablas de cada servicio y el recorrido de cada mensaje por RabbitMQ.
            </span>
          </li>
        </ol>
      </section>

      <div className="layout">
        <Sidebar activeScenario={scenario?.id ?? null} onScenario={start} locked={locked} lockedSagaId={lockedSagaId} />

        <main className="main" id="saga">
          <div className="saga-head">
            <div className="pills">
              <span className="pill">CreateOrderSaga</span>
              {scenario && (
                <span className="pill">
                  Escenario {pad(SCENARIOS.indexOf(scenario) + 1)} · {scenario.concept}
                </span>
              )}
            </div>
            <div className="title-row">
              {saga ? (
                <h2>Saga <span className="mono">{saga.id.slice(0, 8)}</span></h2>
              ) : (
                <h2>{selectedId ? 'Iniciando saga…' : 'Los 4 pasos de la saga'}</h2>
              )}
              {status && saga && (
                <span className={`status ${status.tone} ${saga.status === 'RUNNING' ? 'running' : ''}`}>{status.label}</span>
              )}
              {saga && (
                <Link className="more-link" href={`/datos?saga=${saga.id}`}>Ver sus mensajes y tablas →</Link>
              )}
            </div>
            {scenario && <p>{scenario.description}</p>}
            {saga && (
              <p className="dim">
                {saga.data.quantity} × {product?.name ?? saga.data.productId} · ${Number(saga.data.amount).toFixed(2)} ·
                orden <span className="mono">{saga.data.orderId.slice(0, 8)}</span>
              </p>
            )}
            {saga?.awaiting && (
              <p className="waiting">
                El orquestador espera <span className="mono">{saga.awaiting.commandType}</span> de{' '}
                <b>{saga.awaiting.participant}</b>
                {saga.awaiting.compensating && ' (compensación)'} · intento {saga.awaiting.attempts}
              </p>
            )}
            {outage?.down && running && (
              <div className="callout" role="status">
                <span>
                  <b>inventory está caído para tu saga.</b> Sus mensajes esperan en la cola de reintento de RabbitMQ y
                  el orquestador los reenvía. Levantalo antes de que se agoten los 5 intentos (~50 s) y la saga continúa.
                  {autoRecoverIn !== null && (
                    <span className="dim"> Vuelve solo en {Math.floor(autoRecoverIn / 60)}:{pad(autoRecoverIn % 60)}.</span>
                  )}
                </span>
                <button className="btn-primary" onClick={recover}>Levantar inventory</button>
              </div>
            )}
            {saga?.failureReason && (
              <p>Motivo: <span className={saga.status === 'FAILED' ? 'fail' : 'comp'}>{saga.failureReason}</span></p>
            )}
            {!saga && !selectedId && <p>Elegí un escenario a la izquierda para ver la saga en movimiento.</p>}
          </div>

          <SagaDiagram steps={steps} />

          {detail && <Timeline entries={detail.timeline} />}
        </main>
      </div>

      <Glossary />
    </>
  )
}
