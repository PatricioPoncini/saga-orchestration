export interface Term {
  term: string
  definition: string
}

export const GLOSSARY: Term[] = [
  {
    term: 'Saga',
    definition:
      'Una operación larga partida en pasos, cada uno en su propio servicio y con su propia base. No hay una transacción que abarque todo.',
  },
  {
    term: 'Orquestación',
    definition:
      'Un coordinador central, el orquestador, le dice a cada servicio qué hacer y espera su respuesta. La alternativa es la coreografía: cada servicio reacciona a eventos de los demás.',
  },
  {
    term: 'Compensación',
    definition:
      'La acción que deshace un paso que ya se confirmó: devolver un pago, cancelar una orden. Se ejecutan en orden inverso.',
  },
  {
    term: 'Pivot',
    definition:
      'El paso sin vuelta atrás. Si falla, se compensa todo lo anterior; si sale bien, la saga sí o sí va a terminar bien.',
  },
  {
    term: 'Retriable',
    definition:
      'Los pasos que vienen después del pivot. No pueden fallar por reglas de negocio: si algo sale mal, sólo se reintentan.',
  },
  {
    term: 'Falta de aislamiento',
    definition:
      'Mientras la saga corre, otros pueden ver estados intermedios: la orden sigue "pendiente" aunque el pago ya se cobró.',
  },
  {
    term: 'Timeout y reintento',
    definition:
      'Si un servicio no responde a tiempo, el orquestador reenvía el comando. Si se agotan los intentos, la saga queda fallida y pide intervención manual.',
  },
  {
    term: 'Idempotencia',
    definition:
      'Procesar el mismo mensaje dos veces tiene el mismo efecto que procesarlo una. Es clave porque la mensajería entrega "al menos una vez".',
  },
  {
    term: 'Outbox',
    definition:
      'El mensaje se guarda en la misma transacción que el cambio de negocio y después se publica. Así nunca queda un cambio sin su mensaje, ni un mensaje sin su cambio.',
  },
]
