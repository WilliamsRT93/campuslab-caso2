import { useCallback, useEffect, useState } from 'react';
import { api } from '../api';
import type { ResultadoPublicacion, Topologia } from '../api';

type Entrada = { hora: string; resultado?: ResultadoPublicacion; error?: string };

const OTRA = '__otra__';

/** Tipos de error que los consumidores reconocen en el texto del mensaje (demo de ACK/NACK, reintentos y DLQ). */
const MODOS_ERROR = [
  { valor: '', etiqueta: 'Ninguno: mensaje normal (el consumidor lo procesa y hace ACK)' },
  { valor: 'FORZAR_FALLO', etiqueta: 'No recuperable (FORZAR_FALLO): NACK sin reencolar, va directo a la cola muerta' },
  { valor: 'FALLO_TRANSITORIO', etiqueta: 'Recuperable (FALLO_TRANSITORIO): falla 2 veces, se reintenta y luego se procesa bien' },
  { valor: 'FALLO_TRANSITORIO:9', etiqueta: 'Recuperable que agota reintentos (FALLO_TRANSITORIO:9): 3 intentos y va a la cola muerta' },
];

/**
 * Prueba en vivo de exchanges, bindings y colas (solo Admin). La topologia se lee de RabbitMQ
 * (no es una lista fija) y cada envio muestra la confirmacion del broker y la cola a la que llego.
 */
export function ProbarColasPanel() {
  const [topologia, setTopologia] = useState<Topologia | null>(null);
  const [errorTopologia, setErrorTopologia] = useState<string | null>(null);
  const [exchange, setExchange] = useState('');
  const [routingKey, setRoutingKey] = useState('');
  const [otraClave, setOtraClave] = useState('sin.binding.demo');
  const [mensaje, setMensaje] = useState('Mensaje de prueba desde el front');
  const [modoError, setModoError] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [historial, setHistorial] = useState<Entrada[]>([]);

  const cargar = useCallback(() => {
    api
      .topologia()
      .then((t) => {
        setTopologia(t);
        setErrorTopologia(null);
      })
      .catch((e: Error) => setErrorTopologia(e.message));
  }, []);

  useEffect(() => {
    cargar();
    const id = setInterval(cargar, 2500);
    return () => clearInterval(id);
  }, [cargar]);

  const exchangePrincipal = topologia?.exchanges.find((e) => !e.nombre.endsWith('.dlx'))?.nombre ?? '';
  const exchangeActual = exchange || exchangePrincipal;
  const clavesDelExchange = (topologia?.bindings ?? []).filter((b) => b.exchange === exchangeActual);
  const claveActual = routingKey && (routingKey === OTRA || clavesDelExchange.some((b) => b.routingKey === routingKey))
    ? routingKey
    : clavesDelExchange[0]?.routingKey ?? OTRA;
  const claveEfectiva = claveActual === OTRA ? otraClave.trim() : claveActual;
  const textoEnvio = modoError ? `${modoError} ${mensaje}` : mensaje;

  const enviar = async (ex: string, clave: string) => {
    const hora = new Date().toLocaleTimeString();
    try {
      const resultado = await api.publicar({ exchange: ex, routingKey: clave, mensaje: textoEnvio });
      setHistorial((h) => [{ hora, resultado }, ...h].slice(0, 8));
    } catch (e) {
      setHistorial((h) => [{ hora, error: (e as Error).message }, ...h].slice(0, 8));
    }
    cargar();
  };

  const enviarUno = async () => {
    setEnviando(true);
    await enviar(exchangeActual, claveEfectiva);
    setEnviando(false);
  };

  const enviarALasTres = async () => {
    setEnviando(true);
    for (const b of (topologia?.bindings ?? []).filter((x) => x.exchange === exchangePrincipal)) {
      await enviar(exchangePrincipal, b.routingKey);
    }
    setEnviando(false);
  };

  const sinBinding = async () => {
    setEnviando(true);
    await enviar(exchangePrincipal, 'sin.binding.demo');
    setEnviando(false);
  };

  return (
    <div className="form-card">
      <h3>Probar exchange, bindings y las 3 colas</h3>
      <p className="hint">
        La topologia se lee en vivo de RabbitMQ (se actualiza cada 2,5 s). Envia un mensaje por el exchange con una
        routing key: el broker lo confirma (publisher confirm) y el binding lo enruta a su cola; el consumidor lo
        procesa (ACK). Un error no recuperable (<code>FORZAR_FALLO</code>) va directo a la cola muerta; uno recuperable
        (<code>FALLO_TRANSITORIO</code>) se reintenta hasta 3 veces y solo si se agotan va a la cola muerta. Una routing
        key sin binding es devuelta por el broker. Publicados/Procesados se refrescan cada ~5 s (estadisticas de RabbitMQ).
      </p>
      {errorTopologia && <p className="error">No se pudo leer la topologia: {errorTopologia}</p>}

      {topologia && (
        <>
          <h4 className="sub-h">Exchanges</h4>
          <table className="tabla">
            <thead><tr><th>Exchange</th><th>Tipo</th><th>Durable</th><th>Funcion</th></tr></thead>
            <tbody>
              {topologia.exchanges.map((e) => (
                <tr key={e.nombre}><td><code>{e.nombre}</code></td><td>{e.tipo}</td><td>{e.durable ? 'Si' : 'No'}</td><td>{e.funcion}</td></tr>
              ))}
            </tbody>
          </table>

          <h4 className="sub-h">Bindings (exchange <span className="flecha">→</span> routing key <span className="flecha">→</span> cola)</h4>
          <table className="tabla">
            <thead><tr><th>Exchange</th><th>Routing key</th><th>Cola destino</th></tr></thead>
            <tbody>
              {topologia.bindings.map((b) => (
                <tr key={`${b.exchange}|${b.routingKey}|${b.cola}`}>
                  <td><code>{b.exchange}</code></td><td><code>{b.routingKey}</code></td><td><code>{b.cola}</code></td>
                </tr>
              ))}
            </tbody>
          </table>

          <h4 className="sub-h">Colas en vivo</h4>
          <table className="tabla">
            <thead>
              <tr><th>Cola</th><th>Tipo</th><th>En cola</th><th>Consumidores</th><th>Publicados</th><th>Procesados (ACK)</th><th>TTL</th><th>DLX</th></tr>
            </thead>
            <tbody>
              {topologia.colas.map((c) => (
                <tr key={c.nombre}>
                  <td><code>{c.nombre}</code></td>
                  <td><span className={`badge ${c.tipo === 'dlq' ? 'dlq' : 'neg'}`}>{c.tipo === 'dlq' ? 'cola muerta' : 'negocio'}</span></td>
                  <td>{c.mensajes}</td>
                  <td className={c.tipo === 'negocio' ? (c.consumidores > 0 ? 'num-ok' : 'num-alerta') : ''}>{c.consumidores}</td>
                  <td>{c.publicados}</td>
                  <td>{c.procesados}</td>
                  <td>{c.ttlMs !== null ? `${c.ttlMs / 1000} s` : '-'}</td>
                  <td>{c.dlx ? <code>{c.dlx}</code> : '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="hint">Ultima lectura: {new Date(topologia.actualizado).toLocaleTimeString()}</p>
        </>
      )}

      <h4 className="sub-h">Enviar un mensaje de prueba</h4>
      <div className="fila-form">
        <label>Exchange
          <select value={exchangeActual} onChange={(e) => { setExchange(e.target.value); setRoutingKey(''); }}>
            {(topologia?.exchanges ?? []).map((e) => <option key={e.nombre} value={e.nombre}>{e.nombre}</option>)}
          </select>
        </label>
        <label>Routing key (binding)
          <select value={claveActual} onChange={(e) => setRoutingKey(e.target.value)}>
            {clavesDelExchange.map((b) => (
              <option key={`${b.routingKey}|${b.cola}`} value={b.routingKey}>{b.routingKey} → {b.cola}</option>
            ))}
            <option value={OTRA}>Otra routing key (sin binding)…</option>
          </select>
        </label>
        {claveActual === OTRA && (
          <label>Routing key
            <input value={otraClave} onChange={(e) => setOtraClave(e.target.value)} />
          </label>
        )}
      </div>
      <div className="fila-form" style={{ marginTop: 12 }}>
        <label>Mensaje
          <input value={mensaje} maxLength={450} onChange={(e) => setMensaje(e.target.value)} />
        </label>
        <label>Tipo de error a simular
          <select value={modoError} onChange={(e) => setModoError(e.target.value)}>
            {MODOS_ERROR.map((m) => <option key={m.valor} value={m.valor}>{m.etiqueta}</option>)}
          </select>
        </label>
      </div>
      <div className="form-actions">
        <button className="btn" disabled={enviando || !topologia || !claveEfectiva || !mensaje.trim()} onClick={() => void enviarUno()}>
          {enviando ? 'Enviando...' : 'Enviar mensaje'}
        </button>
        <button className="btn btn-ghost" disabled={enviando || !topologia} onClick={() => void enviarALasTres()}>
          Enviar a las 3 colas
        </button>
        <button className="btn btn-ghost" disabled={enviando || !topologia} onClick={() => void sinBinding()}>
          Probar routing key sin binding
        </button>
      </div>

      {historial.map((h, i) => (
        <div key={i} className={`res ${h.error ? 'bad' : h.resultado?.ruteado ? 'ok' : 'warn'}`}>
          <small>{h.hora}{h.resultado ? ` · ${h.resultado.exchange} · ${h.resultado.routingKey}` : ''}</small>
          {h.error ? `Error: ${h.error}` : h.resultado?.detalle}
        </div>
      ))}
    </div>
  );
}
