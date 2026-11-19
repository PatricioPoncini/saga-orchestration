import { KIND, SERVICE, STEP_STATE } from '../labels'
import type { StepState, StepView } from '../types'

const REACHED: StepState[] = ['RUNNING', 'SUCCEEDED', 'FAILED', 'COMPENSATING', 'COMPENSATED']

export function SagaDiagram({ steps }: { steps: StepView[] }) {
  const compensable = steps.map((s, i) => (s.compensation ? i : -1)).filter((i) => i >= 0)
  const columns = { gridTemplateColumns: `repeat(${steps.length}, minmax(0, 1fr))` }

  return (
    <div className="sky-panel">
      <div className="constellation" style={columns} role="list" aria-label="Saga steps">
        {steps.map((step, i) => {
          const next = steps[i + 1]
          return (
            <div key={step.name} className={`star-cell s-${step.state.toLowerCase()}`} role="listitem">
              {next && <span aria-hidden="true" className={`link ${REACHED.includes(next.state) ? 'reached' : ''}`} />}
              <div className="star">{i + 1}</div>
              <span className="name">{step.name}</span>
              <span className="who">{SERVICE[step.participant] ?? step.participant}</span>
              <span className={`pill ${step.kind === 'PIVOT' ? 'kind-pivot' : ''}`}>{KIND[step.kind]}</span>
              <span className="state">{STEP_STATE[step.state]}</span>
            </div>
          )
        })}

        {compensable.length > 0 &&
          steps.map((step, i) =>
            step.compensation ? (
              <div key={`l${i}`} className={`comp-link ${compState(step.state) !== 'idle' ? 'active' : ''}`} aria-hidden="true">
                <span />
              </div>
            ) : (
              <div key={`l${i}`} />
            ),
          )}

        {compensable.length > 0 &&
          steps.map((step, i) => {
            if (!step.compensation) return <div key={`c${i}`} />
            const state = compState(step.state)
            const position = compensable.indexOf(i)
            const previous = position > 0 ? compensable[position - 1] : null
            const reverseActive = previous !== null && compState(steps[previous].state) !== 'idle'
            return (
              <div key={`c${i}`} className={`comp-cell c-${state}`}>
                {previous !== null && (
                  <span
                    aria-hidden="true"
                    className={`reverse ${reverseActive ? 'active' : ''}`}
                    style={{ width: `calc(${(i - previous) * 100}% - 56px)` }}
                  >
                    <em>← reverse order</em>
                  </span>
                )}
                <div className="comp-star" aria-hidden="true">↺</div>
                <span className="name">{step.compensation}</span>
                <span className="state">
                  {state === 'running' ? 'running…' : state === 'done' ? 'done' : 'only if something fails later'}
                </span>
              </div>
            )
          })}
      </div>

      <div className="legend" aria-label="Legend">
        <span><i style={{ borderColor: 'var(--ok)' }} />ok</span>
        <span><i style={{ borderColor: '#fff' }} />running</span>
        <span><i style={{ borderColor: 'var(--fail)' }} />failed</span>
        <span><i style={{ borderColor: 'var(--comp)' }} />compensated</span>
        <span><i style={{ borderColor: 'rgba(255,255,255,.4)', borderStyle: 'dashed' }} />not executed</span>
        <span className="dim">compensable = can be undone · pivot = point of no return · retriable = only retried</span>
      </div>
    </div>
  )
}

function compState(stepState: StepState): 'idle' | 'running' | 'done' {
  if (stepState === 'COMPENSATING') return 'running'
  if (stepState === 'COMPENSATED') return 'done'
  return 'idle'
}
