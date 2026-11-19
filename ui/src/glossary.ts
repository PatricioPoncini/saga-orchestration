export interface Term {
  term: string
  definition: string
}

export const GLOSSARY: Term[] = [
  {
    term: 'Saga',
    definition:
      'A long operation split into steps, each one in its own service with its own database. There is no single transaction that covers everything.',
  },
  {
    term: 'Orchestration',
    definition:
      'A central coordinator, the orchestrator, tells each service what to do and waits for its reply. The alternative is choreography: each service reacts to the events of the others.',
  },
  {
    term: 'Compensation',
    definition:
      'The action that undoes a step that was already committed: refunding a payment, cancelling an order. Compensations run in reverse order.',
  },
  {
    term: 'Pivot',
    definition:
      'The step with no way back. If it fails, everything before it is compensated; if it succeeds, the saga will definitely finish successfully.',
  },
  {
    term: 'Retriable',
    definition:
      'The steps that come after the pivot. They cannot fail because of business rules: if something goes wrong, they are just retried.',
  },
  {
    term: 'Lack of isolation',
    definition:
      'While the saga is running, others can see intermediate states: the order is still "pending" even though the payment was already charged.',
  },
  {
    term: 'Timeout and retry',
    definition:
      'If a service does not reply in time, the orchestrator sends the command again. If it runs out of attempts, the saga ends as failed and needs manual intervention.',
  },
  {
    term: 'Idempotency',
    definition:
      'Processing the same message twice has the same effect as processing it once. This is key because messaging delivers "at least once".',
  },
  {
    term: 'Outbox',
    definition:
      'The message is saved in the same transaction as the business change and published afterwards. This way there is never a change without its message, or a message without its change.',
  },
]
