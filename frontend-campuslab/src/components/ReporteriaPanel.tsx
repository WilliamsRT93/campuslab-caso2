import { useCallback, useEffect, useState } from 'react';
import type { UIEvent } from 'react';
import { api } from '../api';
import { kafkaUiUrl } from '../authConfig';
import type { ConsumerGroupKafka, EventoAuditoria, ReporteVenta, TopicoKafka } from '../api';

type Resultado = { ok: boolean; mensaje: string };

/** Mezcla "nuevos" (pueden traer filas ya conocidas) con "actuales" sin duplicar, mas reciente primero. */
function fusionarPorId<T extends { id: number }>(nuevos: T[], actuales: T[]): T[] {
  const idsActuales = new Set(actuales.map((x) => x.id));
  const agregados = nuevos.filter((n) => !idsActuales.has(n.id));
  if (agregados.length === 0) return actuales;
  return [...actuales, ...agregados].sort((a, b) => b.id - a.id);
}

/**
 * Panel de reporteria en tiempo real y auditoria (EP5): ambas listas las alimenta SOLO
 * ms-campuslab-reporteria, consumiendo los topicos Kafka campuslab.ventas/campuslab.auditoria
 * que publican bookings y catalog. Este panel nunca escribe en Kafka directo -- los 4 botones
 * de prueba llaman al administrador de Kafka (mismos casos prefabricados que Swagger).
 */
export function ReporteriaPanel() {
  const [subTab, setSubTab] = useState<'ventas' | 'auditoria' | 'topologia'>('ventas');
  const [ventas, setVentas] = useState<ReporteVenta[]>([]);
  const [totalVentas, setTotalVentas] = useState(0);
  const [cargandoMasVentas, setCargandoMasVentas] = useState(false);
  const [auditoria, setAuditoria] = useState<EventoAuditoria[]>([]);
  const [totalAuditoria, setTotalAuditoria] = useState(0);
  const [cargandoMasAuditoria, setCargandoMasAuditoria] = useState(false);
  const [topicos, setTopicos] = useState<TopicoKafka[]>([]);
  const [grupos, setGrupos] = useState<ConsumerGroupKafka[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [probando, setProbando] = useState<string | null>(null);
  const [resultado, setResultado] = useState<Resultado | null>(null);
  const [cargaTopico, setCargaTopico] = useState<'ventas' | 'auditoria'>('ventas');
  const [cargaCantidad, setCargaCantidad] = useState(200);
  const [cargaTipo, setCargaTipo] = useState<'VALIDO' | 'MALFORMADO' | 'MIXTO'>('VALIDO');
  const [generandoCarga, setGenerandoCarga] = useState(false);
  const [resultadoCarga, setResultadoCarga] = useState<Resultado | null>(null);

  // Poll cada 3s: trae siempre la primera pagina (las mas recientes) y la fusiona con lo ya
  // cargado por scroll infinito, sin perder las paginas antiguas que el usuario ya trajo.
  const cargar = useCallback(() => {
    api.listarVentas()
      .then((p) => {
        setTotalVentas(p.total);
        setVentas((actuales) => fusionarPorId(p.items, actuales));
      })
      .catch((e: Error) => setError(e.message));
    api.listarAuditoriaKafka()
      .then((p) => {
        setTotalAuditoria(p.total);
        setAuditoria((actuales) => fusionarPorId(p.items, actuales));
      })
      .catch((e: Error) => setError(e.message));
    api.listarTopicosKafka().then(setTopicos).catch(() => undefined);
    api.listarConsumerGroupsKafka().then(setGrupos).catch(() => undefined);
  }, []);

  useEffect(() => {
    cargar();
    const id = setInterval(cargar, 3000);
    return () => clearInterval(id);
  }, [cargar]);

  /** Scroll infinito: pide la siguiente pagina de 50 filas mas antiguas que la ultima cargada. */
  const cargarMasVentas = useCallback(() => {
    if (cargandoMasVentas || ventas.length === 0 || ventas.length >= totalVentas) return;
    const cursor = ventas[ventas.length - 1].id;
    setCargandoMasVentas(true);
    api.listarVentas(cursor)
      .then((p) => {
        setTotalVentas(p.total);
        setVentas((previas) => fusionarPorId(p.items, previas));
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setCargandoMasVentas(false));
  }, [cargandoMasVentas, totalVentas, ventas]);

  const cargarMasAuditoria = useCallback(() => {
    if (cargandoMasAuditoria || auditoria.length === 0 || auditoria.length >= totalAuditoria) return;
    const cursor = auditoria[auditoria.length - 1].id;
    setCargandoMasAuditoria(true);
    api.listarAuditoriaKafka(cursor)
      .then((p) => {
        setTotalAuditoria(p.total);
        setAuditoria((previas) => fusionarPorId(p.items, previas));
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setCargandoMasAuditoria(false));
  }, [cargandoMasAuditoria, totalAuditoria, auditoria]);

  const alHacerScroll = (cargarMas: () => void) => (e: UIEvent<HTMLDivElement>) => {
    const el = e.currentTarget;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 80) {
      cargarMas();
    }
  };

  const probar = async (topicoLogico: 'ventas' | 'auditoria', tipoCaso: 'VALIDO' | 'MALFORMADO') => {
    const clave = `${topicoLogico}-${tipoCaso}`;
    setResultado(null);
    setProbando(clave);
    try {
      const r = await api.publicarPruebaKafka({ topicoLogico, tipoCaso });
      setResultado({ ok: tipoCaso === 'VALIDO', mensaje: r.detalle });
      setTimeout(cargar, 2500);
    } catch (e) {
      setResultado({ ok: false, mensaje: (e as Error).message });
    } finally {
      setProbando(null);
    }
  };

  const generarCarga = async () => {
    setResultadoCarga(null);
    setGenerandoCarga(true);
    try {
      const r = await api.generarCargaKafka({
        topicoLogico: cargaTopico,
        cantidadMensajes: cargaCantidad,
        tipoCaso: cargaTipo,
      });
      setResultadoCarga({ ok: true, mensaje: r.detalle });
      setTimeout(cargar, 2000);
    } catch (e) {
      setResultadoCarga({ ok: false, mensaje: (e as Error).message });
    } finally {
      setGenerandoCarga(false);
    }
  };

  return (
    <section className="panel">
      <div>
        <p className="kicker">CampusLab · Operaciones</p>
        <h2 className="titulo-panel">Reporteria y auditoria de reservas</h2>
        <p className="hint">
          Ventas confirmadas y cambios de catalogo, en tiempo real. Esta informacion la alimenta Kafka
          en segundo plano (reservas → <code>campuslab.ventas</code>, catalogo → <code>campuslab.auditoria</code>);
          aqui solo se muestra el resultado.
        </p>
      </div>

      <nav className="tabs">
        <button className={subTab === 'ventas' ? 'active' : ''} onClick={() => setSubTab('ventas')}>
          Ventas en tiempo real ({totalVentas})
        </button>
        <button className={subTab === 'auditoria' ? 'active' : ''} onClick={() => setSubTab('auditoria')}>
          Auditoria de catalogo ({totalAuditoria})
        </button>
        <button className={subTab === 'topologia' ? 'active' : ''} onClick={() => setSubTab('topologia')}>
          Topicos y consumer groups
        </button>
      </nav>

      {error && <p className="error">{error}</p>}

      {subTab === 'ventas' && (
        <>
          <div className="tabla-wrap tabla-scroll" onScroll={alHacerScroll(cargarMasVentas)}>
            <table className="tabla">
              <thead>
                <tr>
                  <th>Reserva</th><th>Nombre</th><th>RUT</th><th>Fecha</th><th>Producto</th><th>Estado</th><th>Recibido</th>
                </tr>
              </thead>
              <tbody>
                {ventas.map((v) => (
                  <tr key={v.id}>
                    <td>#{v.reservaId}</td>
                    <td>{v.nombre}</td>
                    <td>{v.rut}</td>
                    <td>{v.fecha?.replace('T', ' ').slice(0, 16)}</td>
                    <td>#{v.productoId}</td>
                    <td><span className="estado">{v.estado}</span></td>
                    <td>{new Date(v.recibidoEn).toLocaleTimeString()}</td>
                  </tr>
                ))}
                {ventas.length === 0 && (
                  <tr><td colSpan={7} className="hint">Sin ventas registradas todavia.</td></tr>
                )}
              </tbody>
            </table>
          </div>
          <p className="hint">
            {cargandoMasVentas
              ? 'Cargando mas...'
              : ventas.length >= totalVentas && ventas.length > 0
                ? `Mostrando las ${ventas.length} de ${totalVentas}.`
                : `Mostrando ${ventas.length} de ${totalVentas} -- hace scroll para ver mas.`}
          </p>
        </>
      )}

      {subTab === 'auditoria' && (
        <>
          <div className="tabla-wrap tabla-scroll" onScroll={alHacerScroll(cargarMasAuditoria)}>
            <table className="tabla">
              <thead>
                <tr>
                  <th>Accion</th><th>Producto</th><th>Nombre</th><th>Tipo</th><th>Stock</th><th>Disponible</th><th>Recibido</th>
                </tr>
              </thead>
              <tbody>
                {auditoria.map((a) => (
                  <tr key={a.id}>
                    <td><span className="badge">{a.accion}</span></td>
                    <td>#{a.productoId}</td>
                    <td>{a.nombre ?? '—'}</td>
                    <td>{a.tipo ?? '—'}</td>
                    <td>{a.stock ?? '—'}</td>
                    <td>{a.disponible === null ? '—' : a.disponible ? 'Si' : 'No'}</td>
                    <td>{new Date(a.recibidoEn).toLocaleTimeString()}</td>
                  </tr>
                ))}
                {auditoria.length === 0 && (
                  <tr><td colSpan={7} className="hint">Sin eventos de auditoria todavia.</td></tr>
                )}
              </tbody>
            </table>
          </div>
          <p className="hint">
            {cargandoMasAuditoria
              ? 'Cargando mas...'
              : auditoria.length >= totalAuditoria && auditoria.length > 0
                ? `Mostrando las ${auditoria.length} de ${totalAuditoria}.`
                : `Mostrando ${auditoria.length} de ${totalAuditoria} -- hace scroll para ver mas.`}
          </p>
        </>
      )}

      {subTab === 'topologia' && (
        <>
          {kafkaUiUrl && (
            <p className="hint">
              <a className="btn btn-ghost" href={kafkaUiUrl} target="_blank" rel="noopener noreferrer">
                Abrir Kafka-UI ↗
              </a>{' '}
              para inspeccionar brokers, particiones y mensajes directamente.
            </p>
          )}
          <h4 className="sub-h">Topicos</h4>
          <div className="tabla-wrap">
            <table className="tabla">
              <thead>
                <tr><th>Topico</th><th>Particiones</th><th>Factor replicacion</th><th>Retencion (ms)</th><th>Cleanup policy</th></tr>
              </thead>
              <tbody>
                {topicos.map((t) => (
                  <tr key={t.nombre}>
                    <td>
                      {t.nombre}
                      {t.nombre.endsWith('.dlt') && <span className="badge dlq">DLT</span>}
                    </td>
                    <td>{t.particiones}</td>
                    <td>{t.factorReplicacion}</td>
                    <td>{t.configuracion['retention.ms'] ?? '—'}</td>
                    <td>{t.configuracion['cleanup.policy'] ?? '—'}</td>
                  </tr>
                ))}
                {topicos.length === 0 && (
                  <tr><td colSpan={5} className="hint">Sin topicos creados todavia.</td></tr>
                )}
              </tbody>
            </table>
          </div>

          <h4 className="sub-h">Consumer groups</h4>
          <div className="tabla-wrap">
            <table className="tabla">
              <thead>
                <tr>
                  <th>Group ID</th><th>Estado</th><th>Miembros</th><th>Topicos asignados</th>
                  <th>Consumer lag</th>
                </tr>
              </thead>
              <tbody>
                {grupos.map((g) => (
                  <tr key={g.groupId}>
                    <td>{g.groupId}</td>
                    <td>{g.estado}</td>
                    <td>{g.numeroDeMiembros}</td>
                    <td>{g.topicosAsignados.join(', ') || '—'}</td>
                    <td>
                      <span className={g.lagTotal > 0 ? 'badge dlq' : 'badge'}>{g.lagTotal}</span>
                      {g.detalleLag.length > 0 && (
                        <ul className="lag-detalle">
                          {g.detalleLag.map((l) => (
                            <li key={`${l.topico}-${l.particion}`}>
                              {l.topico}[{l.particion}]: {l.offsetComprometido}/{l.offsetFinal} (lag {l.lag})
                            </li>
                          ))}
                        </ul>
                      )}
                    </td>
                  </tr>
                ))}
                {grupos.length === 0 && (
                  <tr><td colSpan={5} className="hint">Sin consumer groups activos todavia.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}

      <details className="herramientas-prueba">
        <summary>Herramientas de prueba de Kafka (demo tecnica)</summary>

        <div className="form-card">
          <h3>Casos de prueba (Kafka → reporteria)</h3>
          <p className="hint">
            Cada boton publica un evento real en Kafka a traves de <code>ms-campuslab-kafka-admin</code> (mismos
            casos prefabricados disponibles en Swagger, <code>/api/kafka/prueba/publicar</code>). El caso{' '}
            <strong>valido</strong> debe aparecer como fila nueva arriba en pocos segundos; el caso{' '}
            <strong>malformado</strong> no debe generar fila nueva -- termina en el topico{' '}
            <code>&lt;nombre&gt;.dlt</code> (revisar con Kafka-UI o <code>kafka-console-consumer</code>).
          </p>
          <div className="form-actions">
            <button className="btn" disabled={probando !== null} onClick={() => void probar('ventas', 'VALIDO')}>
              {probando === 'ventas-VALIDO' ? 'Enviando...' : 'Venta valida'}
            </button>
            <button className="btn btn-ghost" disabled={probando !== null} onClick={() => void probar('ventas', 'MALFORMADO')}>
              {probando === 'ventas-MALFORMADO' ? 'Enviando...' : 'Venta malformada -> DLT'}
            </button>
            <button className="btn" disabled={probando !== null} onClick={() => void probar('auditoria', 'VALIDO')}>
              {probando === 'auditoria-VALIDO' ? 'Enviando...' : 'Auditoria valida'}
            </button>
            <button className="btn btn-ghost" disabled={probando !== null} onClick={() => void probar('auditoria', 'MALFORMADO')}>
              {probando === 'auditoria-MALFORMADO' ? 'Enviando...' : 'Auditoria malformada -> DLT'}
            </button>
          </div>
          {resultado && <p className={resultado.ok ? 'hint dlq-ok' : 'error'}>{resultado.mensaje}</p>}
        </div>

        <div className="form-card">
          <h3>Prueba de carga (consumer lag)</h3>
          <p className="hint">
            Publica varios mensajes EN BURST en el topico elegido, sin esperar la confirmacion de cada uno.
            Elija la mezcla: solo validos, solo malformados (todos a la DLT) o mixto (mitad y mitad). El{' '}
            <strong>consumer lag</strong> (pestaña "Topicos y consumer groups") debe subir de forma visible y
            volver a 0 segundos despues, a medida que <code>ms-campuslab-reporteria</code> los procesa.
          </p>
          <div className="form-actions">
            <select
              value={cargaTopico}
              disabled={generandoCarga}
              onChange={(e) => setCargaTopico(e.target.value as 'ventas' | 'auditoria')}
            >
              <option value="ventas">Topico: ventas</option>
              <option value="auditoria">Topico: auditoria</option>
            </select>
            <select
              value={cargaTipo}
              disabled={generandoCarga}
              onChange={(e) => setCargaTipo(e.target.value as 'VALIDO' | 'MALFORMADO' | 'MIXTO')}
            >
              <option value="VALIDO">Solo buenas (validas)</option>
              <option value="MALFORMADO">Solo malas (-&gt; DLT)</option>
              <option value="MIXTO">Mixto (mitad y mitad)</option>
            </select>
            <input
              type="number"
              min={1}
              max={2000}
              value={cargaCantidad}
              disabled={generandoCarga}
              onChange={(e) => setCargaCantidad(Number(e.target.value))}
              aria-label="Cantidad de mensajes"
            />
            <button className="btn" disabled={generandoCarga} onClick={() => void generarCarga()}>
              {generandoCarga ? 'Generando...' : `Generar carga (${cargaCantidad} msjs)`}
            </button>
          </div>
          {resultadoCarga && <p className={resultadoCarga.ok ? 'hint dlq-ok' : 'error'}>{resultadoCarga.mensaje}</p>}
        </div>
      </details>
    </section>
  );
}
