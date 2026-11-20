package cl.duoc.campuslab.kafkaadmin.dto;

import java.util.List;

public record ConsumerGroupResponse(
        String groupId,
        String estado,
        int numeroDeMiembros,
        List<String> topicosAsignados,
        long lagTotal,
        List<LagParticionResponse> detalleLag) {
}
