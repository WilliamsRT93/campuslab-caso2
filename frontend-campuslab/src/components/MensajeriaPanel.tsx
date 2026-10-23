import { useEffect, useState } from 'react';
import { api } from '../api';
import type { Producto } from '../api';
import { ProbarColasPanel } from './ProbarColasPanel';

type Resultado = { ok: boolean; mensaje: string };

/**
 * Demo de colas muertas (DLQ) para el usuario Admin.
 * No llama a un endpoint nuevo: reutiliza la creacion de reserva/producto
 * ya existente con nombre="FORZAR_FALLO" -- el mismo valor que los listeners
 * de los microservicios (NotificacionListener/DocumentoListener/AuditoriaListener)
 * detectan para hacer NACK sin reencolar y mandar el mensaje a su DLQ real.
 */
export function MensajeriaPanel() {
  const [productos, setProductos] = useState<Producto[]>([]);
  const [cargando, setCargando] = useState<'reserva' | 'producto' | null>(null);
  const [resultado, setResultado] = useState<Resultado | null>(null);

  useEffect(() => {
    api.listarProductos().then(setProductos).catch(() => undefined);
  }, []);

  const forzarFalloReserva = async () => {
    setResultado(null);
    if (productos.length === 0) {
      setResultado({ ok: false, mensaje: 'No hay productos en el catalogo para asociar la reserva de prueba.' });
      return;
    }
    setCargando('reserva');
    try {
      const ahora = new Date(Date.now() + 60 * 60 * 1000).toISOString().slice(0, 19);
      const reserva = await api.crearReserva({
        nombre: 'FORZAR_FALLO',
        rut: '11111111-1',
        fecha: ahora,
        productoId: productos[0].id,
      });
      setResultado({
        ok: true,
        mensaje: `Reserva #${reserva.id} creada con payload FORZAR_FALLO -> ambos listeners (notificaciones y documentos) hicieron NACK -> los 2 mensajes cayeron en cola-notificaciones-dlq y cola-documentos-dlq.`,
      });
    } catch (e) {
      setResultado({ ok: false, mensaje: (e as Error).message });
    } finally {
      setCargando(null);
    }
  };

  const forzarFalloAuditoria = async () => {
    setResultado(null);
    setCargando('producto');
    try {
      const producto = await api.crearProducto({
        nombre: 'FORZAR_FALLO',
        tipo: 'EQUIPO',
        stock: 0,
        disponible: false,
      });
      setResultado({
        ok: true,
        mensaje: `Producto #${producto.id} creado con payload FORZAR_FALLO -> AuditoriaListener hizo NACK -> el mensaje cayo en cola-auditoria-dlq.`,
      });
    } catch (e) {
      setResultado({ ok: false, mensaje: (e as Error).message });
    } finally {
      setCargando(null);
    }
  };

  return (
    <section className="panel">
      <ProbarColasPanel />

      <div className="form-card">
        <h3>Demo de colas muertas (DLQ)</h3>
        <p className="hint">
          Cada boton crea un registro real con el payload especial <code>FORZAR_FALLO</code>. El listener
          correspondiente del backend lo detecta, hace <code>NACK</code> sin reencolar, y RabbitMQ enruta el
          mensaje automaticamente a su cola de mensajes muertos (DLQ) -- sin tocar la base de datos de
          produccion ni afectar reservas/productos reales. Para ver el resultado, abrir la Management UI de
          RabbitMQ o el listener <code>monitor-colas.sh</code> en vivo.
        </p>

        <div className="form-actions">
          <button className="btn" disabled={cargando !== null} onClick={() => void forzarFalloReserva()}>
            {cargando === 'reserva' ? 'Enviando...' : 'Forzar fallo -> DLQ notificaciones + documentos'}
          </button>
          <button className="btn" disabled={cargando !== null} onClick={() => void forzarFalloAuditoria()}>
            {cargando === 'producto' ? 'Enviando...' : 'Forzar fallo -> DLQ auditoria'}
          </button>
        </div>

        {resultado && (
          <p className={resultado.ok ? 'hint dlq-ok' : 'error'}>{resultado.mensaje}</p>
        )}
      </div>
    </section>
  );
}
