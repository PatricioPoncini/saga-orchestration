import { SCENARIOS, type Scenario } from '../scenarios'

interface Props {
  activeScenario: string | null
  onScenario: (scenario: Scenario) => void
  locked: boolean
  lockedSagaId: string | null
}

const pad = (n: number) => String(n).padStart(2, '0')

export function Sidebar({ activeScenario, onScenario, locked, lockedSagaId }: Props) {
  return (
    <aside className="sidebar" id="scenarios">
      <nav className="scenario-list" aria-label="Guided scenarios">
        <h2 className="section-title">Pick a scenario</h2>
        {SCENARIOS.map((s, i) => (
          <button
            key={s.id}
            className={`scenario ${activeScenario === s.id ? 'active' : ''}`}
            disabled={locked}
            onClick={() => onScenario(s)}
            title={locked ? 'Wait for the current saga to finish' : s.description}
          >
            <span className="scenario-text">
              <span><span className="num">{pad(i + 1)}</span>{s.title}</span>
              <small>{s.concept}</small>
            </span>
            <span className="arrow" aria-hidden="true">→</span>
          </button>
        ))}
      </nav>

      {locked && (
        <div className="lock-notice" role="status">
          <i aria-hidden="true" />
          <span>
            {lockedSagaId ? (
              <>Saga <span className="mono strong">{lockedSagaId.slice(0, 8)}</span> is running.</>
            ) : (
              'Starting the saga…'
            )}{' '}
            When it finishes, you'll be able to pick another scenario.
          </span>
        </div>
      )}
    </aside>
  )
}
