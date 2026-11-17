import { GLOSSARY } from '../glossary'

export function Glossary() {
  return (
    <section className="glossary" aria-labelledby="glosario-title" id="glosario">
      <h2 className="section-title" id="glosario-title">Glosario</h2>
      <dl className="glossary-grid">
        {GLOSSARY.map((t) => (
          <div key={t.term} className="term">
            <dt>{t.term}</dt>
            <dd>{t.definition}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}
