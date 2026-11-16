import { useCallback, useEffect, useState } from 'react';
import { api } from '../api';
import type { ConsumerGroupKafka, EventoAuditoria, ReporteVenta, TopicoKafka } from '../api';

type Resultado = { ok: boolean; mensaje: string };

/**
 * Panel de reporteria en tiempo real y auditoria (EP5): ambas listas las alimenta SOLO
 * ms-campuslab-reporteria, consumiendo los topicos Kafka campuslab.ventas/campuslab.auditoria
 * que publican bookings y catalog. Este panel nunca escribe en Kafka directo -- los 4 botones
 * de prueba llaman al administrador de Kafka (mismos casos prefabricados que Swagger).
 */
export function ReporteriaPanel() {
  const [subTab, setSubTab] = useState<'ventas' | 'auditoria' | 'topologia'>('ventas');
  const [ventas, setVentas] = useState<ReporteVenta[]>([]);
  const [auditoria, setAuditoria] = useState<EventoAuditoria[]>([]);
  const [topicos, setTopicos] = useState<TopicoKafka[]>([]);
  const [grupos, setGrupos] = useState<ConsumerGroupKafka[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [probando, setProbando] = useState<string | null>(null);
  const [resultado, setResultado] = useState<Resultado | null>(null);

  const cargar = useCallback(() => {
    api.listarVentas().then(setVentas).catch((e: Error) => setError(e.message));
    api.listarAuditoriaKafka().then(setAuditoria).catch((e: Error) => setError(e.message));
    api.listarTopicosKafka().then(setTopicos).catch(() => undefined);
    api.listarConsumerGroupsKafka().then(setGrupos).catch(() => undefined);
  }, []);

  useEffect(() => {
    cargar();
    const id = setInterval(cargar, 3000);
    return () => clearInterval(id);
  }, [cargar]);

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

  return (
    <section className="panel">
      <div className="form-card">
        <h3>Casos de prueba (Kafka → reporteria)</h3>
        <p className="hint">
          Cada boton publica un evento real en Kafka a traves de <code>ms-campuslab-kafka-admin</code> (mismos
          casos prefabricados disponibles en Swagger, <code>/api/kafka/prueba/publicar</code>). El caso{' '}
          <strong>valido</strong> debe aparecer como fila nueva abajo en pocos segundos; el caso{' '}
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

      <nav className="tabs">
        <button className={subTab === 'ventas' ? 'active' : ''} onClick={() => setSubTab('ventas')}>
          Ventas en tiempo real ({ventas.length})
        </button>
        <button className={subTab === 'auditoria' ? 'active' : ''} onClick={() => setSubTab('auditoria')}>
          Auditoria de catalogo ({auditoria.length})
        </button>
        <button className={subTab === 'topologia' ? 'active' : ''} onClick={() => setSubTab('topologia')}>
          Topicos y consumer groups
        </button>
      </nav>

      {error && <p className="error">{error}</p>}

      {subTab === 'ventas' && (
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
                <td>{v.fecha}</td>
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
      )}

      {subTab === 'auditoria' && (
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
      )}

      {subTab === 'topologia' && (
        <>
          <h4 className="sub-h">Topicos</h4>
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

          <h4 className="sub-h">Consumer groups</h4>
          <table className="tabla">
            <thead>
              <tr><th>Group ID</th><th>Estado</th><th>Miembros</th><th>Topicos asignados</th></tr>
            </thead>
            <tbody>
              {grupos.map((g) => (
                <tr key={g.groupId}>
                  <td>{g.groupId}</td>
                  <td>{g.estado}</td>
                  <td>{g.numeroDeMiembros}</td>
                  <td>{g.topicosAsignados.join(', ') || '—'}</td>
                </tr>
              ))}
              {grupos.length === 0 && (
                <tr><td colSpan={4} className="hint">Sin consumer groups activos todavia.</td></tr>
              )}
            </tbody>
          </table>
        </>
      )}
    </section>
  );
}
