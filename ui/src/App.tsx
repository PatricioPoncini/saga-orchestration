import { useCallback, useState } from 'react'
import { ResetCountdown } from './components/ResetCountdown'
import { DataPage } from './pages/DataPage'
import { SagaPage } from './pages/SagaPage'
import { Link, useRoute } from './router'

export default function App() {
  const route = useRoute()
  const [live, setLive] = useState(false)
  const [resetTick, setResetTick] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const onError = useCallback((message: string | null) => setError(message), [])
  const onReset = useCallback(() => setResetTick((t) => t + 1), [])
  const onLive = useCallback((value: boolean) => setLive(value), [])

  const isData = route.path.startsWith('/datos')

  return (
    <>
      <div className="sky" aria-hidden="true" />
      <div className="page">
        <header className="topbar">
          <Link className="logo" href="/">Saga<span>Lab</span></Link>
          <nav className="topnav" aria-label="Secciones">
            <Link href="/" className={!isData ? 'current' : ''} aria-current={!isData ? 'page' : undefined}>Saga en vivo</Link>
            <Link href="/datos" className={isData ? 'current' : ''} aria-current={isData ? 'page' : undefined}>Datos y mensajes</Link>
            <a href="https://patricioponcini.com.ar/blog" target="_blank" rel="noreferrer">Blog ↗</a>
          </nav>
          <div className="status-bar">
            <ResetCountdown onReset={onReset} />
            <span className={`live ${live ? 'on' : ''}`}><i />{live ? 'en vivo' : 'desconectado'}</span>
          </div>
        </header>

        {error && <div className="error" role="alert">{error}</div>}

        {isData ? (
          <DataPage resetTick={resetTick} sagaParam={route.params.get('saga')} onError={onError} onLive={onLive} />
        ) : (
          <SagaPage resetTick={resetTick} onError={onError} onLive={onLive} />
        )}

        <footer className="footer">
          <span>SagaLab · patrón Saga con orquestación · Kotlin, Spring Boot, RabbitMQ</span>
          <a href="https://microservices.io/patterns/data/saga.html" target="_blank" rel="noreferrer">microservices.io/patterns/data/saga ↗</a>
        </footer>
      </div>
    </>
  )
}
