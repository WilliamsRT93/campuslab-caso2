package cl.duoc.campuslab.rabbitadmin.dto;

import java.util.List;

/** Foto en vivo de la topologia de CampusLab leida desde RabbitMQ (exchanges, colas y bindings reales). */
public record TopologiaResponse(
        List<ExchangeInfo> exchanges,
        List<ColaInfo> colas,
        List<BindingInfo> bindings,
        String actualizado) {

    public record ExchangeInfo(String nombre, String tipo, boolean durable, String funcion) {}

    public record ColaInfo(
            String nombre,
            String tipo,
            long mensajes,
            long consumidores,
            long publicados,
            long procesados,
            Long ttlMs,
            String dlx,
            String nodo) {}

    public record BindingInfo(String exchange, String routingKey, String cola) {}
}
