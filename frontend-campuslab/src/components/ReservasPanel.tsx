import { useEffect, useState } from 'react';
import { api } from '../api';
import type { Producto, Reserva } from '../api';

const ESTADOS = ['SOLICITADA', 'APROBADA', 'EN_PREPARACION', 'EN_USO', 'DEVUELTA', 'CANCELADA'];

const RUT_VALIDO = /^[0-9]{7,8}-[0-9K]$/;

/** Acepta el RUT con o sin guion/puntos y lo deja como 12345678-9 (formato que exige el backend). */
function normalizarRut(valor: string): string {
  const limpio = valor.replace(/[^0-9kK]/g, '').toUpperCase();
  return limpio.length < 2 ? limpio : `${limpio.slice(0, -1)}-${limpio.slice(-1)}`;
}

/** Digito verificador chileno (modulo 11): multiplicadores 2..7 de derecha a izquierda. */
function digitoVerificador(cuerpo: string): string {
  let suma = 0;
  let mult = 2;
  for (let i = cuerpo.length - 1; i >= 0; i--) {
    suma += Number(cuerpo[i]) * mult;
    mult = mult === 7 ? 2 : mult + 1;
  }
  const resto = 11 - (suma % 11);
  return resto === 11 ? '0' : resto === 10 ? 'K' : String(resto);
}

/** Devuelve el motivo si el RUT (ya normalizado) es invalido, o null si es correcto. */
function errorRut(rut: string): string | null {
  if (!RUT_VALIDO.test(rut)) {
    return 'RUT no valido: use 7 u 8 digitos y digito verificador (ej. 12345678-9).';
  }
  const [cuerpo, dv] = rut.split('-');
  const esperado = digitoVerificador(cuerpo);
  return dv === esperado ? null : `RUT no valido: el digito verificador de ${cuerpo} es ${esperado}, no ${dv}.`;
}

interface Props {
  isAdmin: boolean;
}

export function ReservasPanel({ isAdmin }: Props) {
  const [reservas, setReservas] = useState<Reserva[]>([]);
  const [productos, setProductos] = useState<Producto[]>([]);
  const [nombre, setNombre] = useState('');
  const [rut, setRut] = useState('');
  const [rutTocado, setRutTocado] = useState(false);
  const [fecha, setFecha] = useState('');
  const [productoId, setProductoId] = useState<number | ''>('');
  const [error, setError] = useState<string | null>(null);

  const cargar = () => {
    api.listarReservas().then(setReservas).catch((e: Error) => setError(e.message));
    api.listarProductos().then(setProductos).catch(() => undefined);
  };

  useEffect(cargar, []);

  const crear = async () => {
    setError(null);
    if (productoId === '') {
      setError('Debe seleccionar un producto.');
      return;
    }
    const rutFinal = normalizarRut(rut);
    setRut(rutFinal);
    setRutTocado(true);
    const motivoRut = errorRut(rutFinal);
    if (motivoRut) {
      setError(motivoRut);
      return;
    }
    // datetime-local entrega "yyyy-MM-ddTHH:mm"; el backend espera segundos.
    const fechaIso = fecha.length === 16 ? `${fecha}:00` : fecha;
    try {
      await api.crearReserva({ nombre, rut: rutFinal, fecha: fechaIso, productoId: Number(productoId) });
      setNombre('');
      setRut('');
      setRutTocado(false);
      setFecha('');
      setProductoId('');
      cargar();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const cambiar = async (id: number, status: string) => {
    setError(null);
    try {
      await api.cambiarEstado(id, status);
      cargar();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const eliminar = async (id: number) => {
    setError(null);
    try {
      await api.eliminarReserva(id);
      cargar();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <section className="panel">
      <div className="form-card">
        <h3>Nueva reserva</h3>
        <div className="form-grid">
          <label>Nombre
            <input value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Nombre del solicitante" />
          </label>
          <label>RUT
            <input
              value={rut}
              onChange={(e) => setRut(e.target.value)}
              onBlur={() => { setRut(normalizarRut(rut)); setRutTocado(true); }}
              placeholder="12345678-9 (el guion es opcional)"
            />
            {rutTocado && rut !== '' && errorRut(normalizarRut(rut)) && (
              <span className="campo-error">{errorRut(normalizarRut(rut))}</span>
            )}
          </label>
          <label>Fecha y hora
            <input type="datetime-local" value={fecha} onChange={(e) => setFecha(e.target.value)} />
          </label>
          <label>Producto
            <select value={productoId} onChange={(e) => setProductoId(e.target.value === '' ? '' : Number(e.target.value))}>
              <option value="">Seleccione...</option>
              {productos.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
            </select>
          </label>
        </div>
        <div className="form-actions">
          <button className="btn" onClick={() => void crear()}>Crear reserva</button>
        </div>
      </div>

      {error && <p className="error">{error}</p>}

      <table className="tabla">
        <thead>
          <tr><th>ID</th><th>Nombre</th><th>RUT</th><th>Fecha</th><th>Producto</th><th>Estado</th><th>Acciones</th></tr>
        </thead>
        <tbody>
          {reservas.map((r) => (
            <tr key={r.id}>
              <td>{r.id}</td>
              <td>{r.nombre}</td>
              <td>{r.rut}</td>
              <td>{r.fecha?.replace('T', ' ').slice(0, 16)}</td>
              <td>{r.productoId}</td>
              <td>
                {isAdmin ? (
                  <select value={r.estado} onChange={(e) => void cambiar(r.id, e.target.value)}>
                    {ESTADOS.map((s) => <option key={s} value={s}>{s}</option>)}
                  </select>
                ) : (
                  <span className="estado">{r.estado}</span>
                )}
              </td>
              <td className="acciones">
                <button className="link danger" onClick={() => void eliminar(r.id)}>Eliminar</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {!isAdmin && <p className="hint">El cambio de estado esta reservado a los roles Admin u Operador.</p>}
    </section>
  );
}
