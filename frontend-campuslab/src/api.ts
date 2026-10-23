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

/** Topologia en vivo de RabbitMQ (la entrega el administrador de RabbitMQ, solo rol Admin). */
export interface ExchangeInfo { nombre: string; tipo: string; durable: boolean; funcion: string }
export interface ColaInfo {
  nombre: string;
  tipo: 'negocio' | 'dlq';
  mensajes: number;
  consumidores: number;
  publicados: number;
  procesados: number;
  ttlMs: number | null;
  dlx: string | null;
  nodo: string | null;
}
export interface BindingInfo { exchange: string; routingKey: string; cola: string }
export interface Topologia {
  exchanges: ExchangeInfo[];
  colas: ColaInfo[];
  bindings: BindingInfo[];
  actualizado: string;
}

/** Resultado de publicar un mensaje de prueba: confirmacion del broker y cola de destino. */
export interface ResultadoPublicacion {
  exchange: string;
  routingKey: string;
  confirmado: boolean;
  ruteado: boolean;
  colasDestino: string[];
  detalle: string;
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
    const base = data?.message || data?.reason || `Error ${response.status}`;
    const detalle = data?.errores && typeof data.errores === 'object'
      ? Object.entries(data.errores as Record<string, string>).map(([campo, msg]) => `${campo}: ${msg}`).join(' · ')
      : '';
    throw new Error(detalle ? `${base} — ${detalle}` : base);
  }
  return data as T;
}

export const api = {
  me: () => request<Identidad>('GET', '/api/me'),

  topologia: () => request<Topologia>('GET', '/api/rabbitmq/topologia'),
  publicar: (p: { exchange: string; routingKey: string; mensaje: string }) =>
    request<ResultadoPublicacion>('POST', '/api/rabbitmq/publicar', p),

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
