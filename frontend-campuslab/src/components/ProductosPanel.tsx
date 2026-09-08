import { useEffect, useState } from 'react';
import { api } from '../api';
import type { Producto } from '../api';

const TIPOS = ['LABORATORIO', 'EQUIPO', 'INSUMO'];

interface Props {
  isAdmin: boolean;
}

export function ProductosPanel({ isAdmin }: Props) {
  const [productos, setProductos] = useState<Producto[]>([]);
  const [nombre, setNombre] = useState('');
  const [tipo, setTipo] = useState(TIPOS[0]);
  const [stock, setStock] = useState(1);
  const [disponible, setDisponible] = useState(true);
  const [editId, setEditId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const cargar = () => {
    api.listarProductos().then(setProductos).catch((e: Error) => setError(e.message));
  };

  useEffect(cargar, []);

  const limpiar = () => {
    setEditId(null);
    setNombre('');
    setTipo(TIPOS[0]);
    setStock(1);
    setDisponible(true);
  };

  const guardar = async () => {
    setError(null);
    const payload = { nombre, tipo, stock, disponible };
    try {
      if (editId === null) {
        await api.crearProducto(payload);
      } else {
        await api.actualizarProducto(editId, payload);
      }
      limpiar();
      cargar();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const editar = (p: Producto) => {
    setEditId(p.id);
    setNombre(p.nombre);
    setTipo(p.tipo);
    setStock(p.stock);
    setDisponible(p.disponible);
  };

  const eliminar = async (id: number) => {
    setError(null);
    try {
      await api.eliminarProducto(id);
      cargar();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <section className="panel">
      {isAdmin && (
        <div className="form-card">
          <h3>{editId === null ? 'Nuevo producto' : `Editar producto #${editId}`}</h3>
          <div className="form-grid">
            <label>Nombre
              <input value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Ej: Laboratorio de Redes 3" />
            </label>
            <label>Tipo
              <select value={tipo} onChange={(e) => setTipo(e.target.value)}>
                {TIPOS.map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
            </label>
            <label>Stock / cupo
              <input type="number" min={0} value={stock} onChange={(e) => setStock(Number(e.target.value))} />
            </label>
            <label className="check">Disponible
              <input type="checkbox" checked={disponible} onChange={(e) => setDisponible(e.target.checked)} />
            </label>
          </div>
          <div className="form-actions">
            <button className="btn" onClick={() => void guardar()}>{editId === null ? 'Crear' : 'Guardar'}</button>
            {editId !== null && <button className="btn btn-ghost" onClick={limpiar}>Cancelar</button>}
          </div>
        </div>
      )}

      {error && <p className="error">{error}</p>}

      <table className="tabla">
        <thead>
          <tr><th>ID</th><th>Nombre</th><th>Tipo</th><th>Stock</th><th>Disponible</th>{isAdmin && <th>Acciones</th>}</tr>
        </thead>
        <tbody>
          {productos.map((p) => (
            <tr key={p.id}>
              <td>{p.id}</td>
              <td>{p.nombre}</td>
              <td>{p.tipo}</td>
              <td>{p.stock}</td>
              <td>{p.disponible ? 'Si' : 'No'}</td>
              {isAdmin && (
                <td className="acciones">
                  <button className="link" onClick={() => editar(p)}>Editar</button>
                  <button className="link danger" onClick={() => void eliminar(p.id)}>Eliminar</button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      {!isAdmin && <p className="hint">Solo el rol Admin puede crear, editar o eliminar productos.</p>}
    </section>
  );
}
