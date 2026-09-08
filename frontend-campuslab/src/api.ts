import { apiBaseUrl } from './authConfig';
import { getCognitoToken } from './cognito';

/** Recurso del catalogo (producto). */
export interface Producto {
  id: number;
  nombre: string;
  tipo: string;
  stock: number;
  disponible: boolean;
}

/** Reserva de un recurso. */
export interface Reserva {
  id: number;
  nombre: string;
  rut: string;
  fecha: string;
  productoId: number;
  estado: string;
}

/** Identidad y roles derivados del token de Cognito (via BFF). */
export interface Identidad {
  username: string;
  email: string;
  roles: string[];
  isAdmin: boolean;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const token = getCognitoToken();
  const headers: Record<string, string> = {};
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(`${apiBaseUrl}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const message = data?.message || data?.reason || `Error ${response.status}`;
    throw new Error(message);
  }
  return data as T;
}

export const api = {
  me: () => request<Identidad>('GET', '/api/me'),

  listarProductos: () => request<Producto[]>('GET', '/api/catalog/resources'),
  crearProducto: (p: Omit<Producto, 'id'>) => request<Producto>('POST', '/api/catalog/resources', p),
  actualizarProducto: (id: number, p: Omit<Producto, 'id'>) =>
    request<Producto>('PUT', `/api/catalog/resources/${id}`, p),
  eliminarProducto: (id: number) => request<void>('DELETE', `/api/catalog/resources/${id}`),

  listarReservas: () => request<Reserva[]>('GET', '/api/bookings'),
  crearReserva: (r: { nombre: string; rut: string; fecha: string; productoId: number }) =>
    request<Reserva>('POST', '/api/bookings', r),
  cambiarEstado: (id: number, status: string) =>
    request<Reserva>('PUT', `/api/bookings/${id}/status`, { status }),
  eliminarReserva: (id: number) => request<void>('DELETE', `/api/bookings/${id}`),
};
