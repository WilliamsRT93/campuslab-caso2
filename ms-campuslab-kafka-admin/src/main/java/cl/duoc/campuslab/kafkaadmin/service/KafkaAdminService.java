package cl.duoc.campuslab.kafkaadmin.service;

import cl.duoc.campuslab.kafkaadmin.dto.ActualizarConfigTopicoRequest;
import cl.duoc.campuslab.kafkaadmin.dto.ConsumerGroupResponse;
import cl.duoc.campuslab.kafkaadmin.dto.CrearTopicoRequest;
import cl.duoc.campuslab.kafkaadmin.dto.LagParticionResponse;
import cl.duoc.campuslab.kafkaadmin.dto.TopicoResponse;
import cl.duoc.campuslab.kafkaadmin.exception.RecursoExistenteException;
import cl.duoc.campuslab.kafkaadmin.exception.RecursoNoEncontradoException;
import cl.duoc.campuslab.kafkaadmin.exception.SolicitudInvalidaException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.stereotype.Service;

/**
 * Toda la logica de creacion/eliminacion/consulta de topicos y consumer groups vive ACA,
 * encapsulada. El controller solo llama a metodos de alto nivel y no conoce detalles internos
 * del AdminClient de Kafka (rubrica EP5, indicador "logica de administracion encapsulada"/admin
 * REST bien definido, mismo patron que RabbitAdminService).
 */
@Service
public class KafkaAdminService {

    private static final Pattern NOMBRE = Pattern.compile("^[a-zA-Z0-9._-]{1,249}$");
    private static final long TIMEOUT_SEGUNDOS = 15;

    private final AdminClient adminClient;

    public KafkaAdminService(AdminClient adminClient) {
        this.adminClient = adminClient;
    }

    public void crearTopico(CrearTopicoRequest request) {
        validarNombre(request.nombre());
        if (existeTopico(request.nombre())) {
            throw new RecursoExistenteException("El topico '" + request.nombre() + "' ya existe");
        }
        NewTopic nuevoTopico = new NewTopic(request.nombre(), request.particiones(), request.factorReplicacion());
        Map<String, String> configFinal = new java.util.LinkedHashMap<>();
        if (request.retencionMs() != null) {
            configFinal.put("retention.ms", String.valueOf(request.retencionMs()));
        }
        if (request.cleanupPolicy() != null && !request.cleanupPolicy().isBlank()) {
            configFinal.put("cleanup.policy", request.cleanupPolicy());
        }
        if (!configFinal.isEmpty()) {
            nuevoTopico.configs(configFinal);
        }
        ejecutar(adminClient.createTopics(List.of(nuevoTopico)).all());
    }

    public void eliminarTopico(String nombre) {
        validarNombre(nombre);
        exigirTopico(nombre);
        ejecutar(adminClient.deleteTopics(List.of(nombre)).all());
    }

    public List<TopicoResponse> listarTopicos() {
        Collection<TopicListing> listados = ejecutarConValor(adminClient.listTopics().listings());
        List<String> nombres = listados.stream().map(TopicListing::name).toList();
        if (nombres.isEmpty()) {
            return List.of();
        }
        return describirTopicos(nombres);
    }

    public TopicoResponse obtenerTopico(String nombre) {
        validarNombre(nombre);
        List<TopicoResponse> resultado = describirTopicos(List.of(nombre));
        if (resultado.isEmpty()) {
            throw new RecursoNoEncontradoException("El topico '" + nombre + "' no existe");
        }
        return resultado.get(0);
    }

    /** Solo se puede AUMENTAR particiones (Kafka no permite reducirlas). */
    public void aumentarParticiones(String nombre, int nuevoTotal) {
        validarNombre(nombre);
        exigirTopico(nombre);
        TopicoResponse actual = obtenerTopico(nombre);
        if (nuevoTotal <= actual.particiones()) {
            throw new SolicitudInvalidaException("El nuevo numero de particiones (" + nuevoTotal
                    + ") debe ser mayor al actual (" + actual.particiones() + "); Kafka no permite reducirlas");
        }
        ejecutar(adminClient.createPartitions(Map.of(nombre, NewPartitions.increaseTo(nuevoTotal))).all());
    }

    public void actualizarConfiguracion(String nombre, ActualizarConfigTopicoRequest request) {
        validarNombre(nombre);
        exigirTopico(nombre);
        if (request.vacio()) {
            throw new SolicitudInvalidaException("Debe indicar al menos un parametro a actualizar (retencionMs o cleanupPolicy)");
        }
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        List<AlterConfigOp> operaciones = new ArrayList<>();
        if (request.retencionMs() != null) {
            operaciones.add(new AlterConfigOp(new ConfigEntry("retention.ms", String.valueOf(request.retencionMs())),
                    AlterConfigOp.OpType.SET));
        }
        if (request.cleanupPolicy() != null && !request.cleanupPolicy().isBlank()) {
            operaciones.add(new AlterConfigOp(new ConfigEntry("cleanup.policy", request.cleanupPolicy()),
                    AlterConfigOp.OpType.SET));
        }
        ejecutar(adminClient.incrementalAlterConfigs(Map.of(recurso, operaciones)).all());
    }

    public List<ConsumerGroupResponse> listarConsumerGroups() {
        Collection<ConsumerGroupListing> listados = ejecutarConValor(adminClient.listConsumerGroups().all());
        List<String> groupIds = listados.stream().map(ConsumerGroupListing::groupId).toList();
        if (groupIds.isEmpty()) {
            return List.of();
        }
        Map<String, ConsumerGroupDescription> descripciones =
                ejecutarConValor(adminClient.describeConsumerGroups(groupIds).all());
        return descripciones.values().stream().map(this::aRespuesta).collect(Collectors.toList());
    }

    public ConsumerGroupResponse describirConsumerGroup(String groupId) {
        Map<String, ConsumerGroupDescription> descripciones =
                ejecutarConValor(adminClient.describeConsumerGroups(List.of(groupId)).all());
        ConsumerGroupDescription descripcion = descripciones.get(groupId);
        if (descripcion == null || descripcion.members().isEmpty()) {
            throw new RecursoNoEncontradoException("El consumer group '" + groupId + "' no existe o no tiene miembros activos");
        }
        return aRespuesta(descripcion);
    }

    private ConsumerGroupResponse aRespuesta(ConsumerGroupDescription descripcion) {
        List<String> topicos = descripcion.members().stream()
                .flatMap(m -> m.assignment().topicPartitions().stream())
                .map(tp -> tp.topic())
                .distinct()
                .toList();
        List<LagParticionResponse> detalleLag = calcularLag(descripcion.groupId());
        long lagTotal = detalleLag.stream().mapToLong(LagParticionResponse::lag).sum();
        return new ConsumerGroupResponse(descripcion.groupId(), descripcion.state().toString(),
                descripcion.members().size(), topicos, lagTotal, detalleLag);
    }

    /**
     * Lag por particion = offset final del topico (ultimo mensaje producido) menos el offset
     * comprometido por el consumer group (ultimo mensaje procesado). Se usa para los botones de
     * prueba de carga del frontend: tras publicar un burst de mensajes el lag debe subir y luego
     * volver a 0 cuando ms-campuslab-reporteria los consume.
     */
    private List<LagParticionResponse> calcularLag(String groupId) {
        Map<TopicPartition, OffsetAndMetadata> comprometidos =
                ejecutarConValor(adminClient.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata());
        if (comprometidos.isEmpty()) {
            return List.of();
        }
        Map<TopicPartition, OffsetSpec> solicitudFinal = comprometidos.keySet().stream()
                .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> finales =
                ejecutarConValor(adminClient.listOffsets(solicitudFinal).all());

        List<LagParticionResponse> resultado = new ArrayList<>();
        for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : comprometidos.entrySet()) {
            TopicPartition tp = entry.getKey();
            long offsetComprometido = entry.getValue() == null ? 0 : entry.getValue().offset();
            ListOffsetsResult.ListOffsetsResultInfo info = finales.get(tp);
            long offsetFinal = info == null ? offsetComprometido : info.offset();
            long lag = Math.max(0, offsetFinal - offsetComprometido);
            resultado.add(new LagParticionResponse(tp.topic(), tp.partition(), offsetComprometido, offsetFinal, lag));
        }
        resultado.sort(Comparator.comparing(LagParticionResponse::topico)
                .thenComparingInt(LagParticionResponse::particion));
        return resultado;
    }

    private List<TopicoResponse> describirTopicos(List<String> nombres) {
        Map<String, TopicDescription> descripciones;
        try {
            descripciones = ejecutarConValor(adminClient.describeTopics(nombres).allTopicNames());
        } catch (RecursoNoEncontradoException ex) {
            return List.of();
        }
        Map<ConfigResource, Config> configs = ejecutarConValor(adminClient.describeConfigs(
                nombres.stream().map(n -> new ConfigResource(ConfigResource.Type.TOPIC, n)).toList()).all());

        List<TopicoResponse> resultado = new ArrayList<>();
        for (Map.Entry<String, TopicDescription> entry : descripciones.entrySet()) {
            TopicDescription desc = entry.getValue();
            Config config = configs.get(new ConfigResource(ConfigResource.Type.TOPIC, desc.name()));
            Map<String, String> relevante = new java.util.LinkedHashMap<>();
            if (config != null) {
                for (ConfigEntry entradaConfig : config.entries()) {
                    if ("retention.ms".equals(entradaConfig.name()) || "cleanup.policy".equals(entradaConfig.name())) {
                        relevante.put(entradaConfig.name(), entradaConfig.value());
                    }
                }
            }
            int factorReplicacion = desc.partitions().isEmpty() ? 0
                    : desc.partitions().stream().mapToInt(p -> ((TopicPartitionInfo) p).replicas().size()).max().orElse(0);
            resultado.add(new TopicoResponse(desc.name(), desc.partitions().size(), factorReplicacion, relevante));
        }
        return resultado;
    }

    private boolean existeTopico(String nombre) {
        Collection<TopicListing> listados = ejecutarConValor(adminClient.listTopics().listings());
        return listados.stream().anyMatch(t -> t.name().equals(nombre));
    }

    private void exigirTopico(String nombre) {
        if (!existeTopico(nombre)) {
            throw new RecursoNoEncontradoException("El topico '" + nombre + "' no existe");
        }
    }

    private void validarNombre(String nombre) {
        if (nombre == null || !NOMBRE.matcher(nombre).matches()) {
            throw new SolicitudInvalidaException("El nombre del topico no es valido (solo letras, numeros, puntos, "
                    + "guiones y guion bajo; maximo 249 caracteres)");
        }
    }

    private void ejecutar(KafkaFuture<Void> future) {
        try {
            future.get(TIMEOUT_SEGUNDOS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SolicitudInvalidaException("La operacion con Kafka fue interrumpida: " + e.getMessage());
        } catch (ExecutionException | java.util.concurrent.TimeoutException e) {
            throw desenvolver(e);
        }
    }

    private <T> T ejecutarConValor(KafkaFuture<T> future) {
        try {
            return future.get(TIMEOUT_SEGUNDOS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SolicitudInvalidaException("La operacion con Kafka fue interrumpida: " + e.getMessage());
        } catch (ExecutionException | java.util.concurrent.TimeoutException e) {
            throw desenvolver(e);
        }
    }

    private RuntimeException desenvolver(Exception e) {
        Throwable causa = e.getCause() != null ? e.getCause() : e;
        if (causa instanceof TopicExistsException tee) {
            return new RecursoExistenteException(tee.getMessage());
        }
        if (causa instanceof UnknownTopicOrPartitionException utope) {
            return new RecursoNoEncontradoException(utope.getMessage());
        }
        return new SolicitudInvalidaException("Kafka rechazo la operacion: " + causa.getMessage());
    }
}
