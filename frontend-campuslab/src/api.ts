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

/** Fila del panel de ventas en tiempo real (EP5, alimentada por ms-campuslab-reporteria via Kafka). */
export interface ReporteVenta {
  id: number;
  reservaId: number;
  nombre: string;
  rut: string;
  fecha: string;
  productoId: number;
  estado: string;
  recibidoEn: string;
}

/** Fila del registro de auditoria del catalogo (EP5, alimentada por ms-campuslab-reporteria via Kafka). */
export interface EventoAuditoria {
  id: number;
  accion: string;
  productoId: number;
  nombre: string | null;
  tipo: string | null;
  stock: number | null;
  disponible: boolean | null;
  recibidoEn: string;
}

/** Topico de Kafka con su configuracion (EP5, lo entrega el administrador de Kafka). */
export interface TopicoKafka {
  nombre: string;
  particiones: number;
  factorReplicacion: number;
  configuracion: Record<string, string>;
}

/** Lag de una particion puntual: offset final del topico vs. offset comprometido por el grupo (EP5). */
export interface LagParticionKafka {
  topico: string;
  particion: number;
  offsetComprometido: number;
  offsetFinal: number;
  lag: number;
}

/** Consumer group de Kafka con sus topicos asignados y su consumer lag (EP5). */
export interface ConsumerGroupKafka {
  groupId: string;
  estado: string;
  numeroDeMiembros: number;
  topicosAsignados: string[];
  lagTotal: number;
  detalleLag: LagParticionKafka[];
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

/** Resultado de publicar un caso de prueba Kafka (valido o malformado), via ms-campuslab-kafka-admin. */
export interface ResultadoPruebaKafka {
  topico: string;
  payloadEnviado: string;
  particion: number;
  offset: number;
  detalle: string;
}

/** Resultado de generar una carga configurable de mensajes para forzar consumer lag (EP5). */
export interface ResultadoCargaKafka {
  topico: string;
  mensajesPublicados: number;
  validos: number;
  malformados: number;
  duracionMs: number;
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

  // EP5: panel de reporteria/auditoria (ms-campuslab-reporteria, consumidor Kafka)
  listarVentas: () => request<ReporteVenta[]>('GET', '/api/reporteria/ventas'),
  listarAuditoriaKafka: () => request<EventoAuditoria[]>('GET', '/api/reporteria/auditoria'),

  // EP5: administrador de Kafka (ms-campuslab-kafka-admin)
  listarTopicosKafka: () => request<TopicoKafka[]>('GET', '/api/kafka/topics'),
  listarConsumerGroupsKafka: () => request<ConsumerGroupKafka[]>('GET', '/api/kafka/consumer-groups'),
  publicarPruebaKafka: (p: { topicoLogico: 'ventas' | 'auditoria'; tipoCaso: 'VALIDO' | 'MALFORMADO' }) =>
    request<ResultadoPruebaKafka>('POST', '/api/kafka/prueba/publicar', p),
  generarCargaKafka: (p: {
    topicoLogico: 'ventas' | 'auditoria';
    cantidadMensajes: number;
    tipoCaso: 'VALIDO' | 'MALFORMADO' | 'MIXTO';
  }) => request<ResultadoCargaKafka>('POST', '/api/kafka/prueba/carga', p),

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
