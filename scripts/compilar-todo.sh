#!/bin/bash
# =============================================================================
# compilar-todo.sh — Compila con Maven todos los microservicios Spring Boot (EP3)
#
# Ejecuta `mvn clean package` en cada microservicio y muestra el resultado y el
# tamano del .jar generado. Para correr uno solo en local:
#   cd ms-campuslab-catalog && mvn spring-boot:run
#
# Uso:  ./compilar-todo.sh [--con-tests]
# Requiere: Java 17 y Maven 3.9+ (mvn en el PATH).
# =============================================================================
set -u

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
TESTS="-DskipTests"; [ "${1:-}" = "--con-tests" ] && TESTS=""
SERVICIOS=(ms-campuslab-catalog ms-campuslab-bookings ms-campuslab-bff ms-campuslab-rabbitmq-admin)

command -v mvn >/dev/null 2>&1 || { echo "❌ No se encontro 'mvn' en el PATH (instale Maven 3.9+ y Java 17)"; exit 1; }

FALLOS=0
for s in "${SERVICIOS[@]}"; do
  printf "🔨 %-30s " "$s"
  if (cd "$RAIZ/$s" && mvn -q -B clean package $TESTS > "/tmp/mvn_${s}.log" 2>&1); then
    jar=$(ls "$RAIZ/$s"/target/*.jar 2>/dev/null | head -1)
    echo "✅ OK  ($(du -h "$jar" | cut -f1)  $(basename "$jar"))"
  else
    FALLOS=$((FALLOS+1)); echo "❌ ERROR  (detalle en /tmp/mvn_${s}.log)"
  fi
done
echo
[ "$FALLOS" -eq 0 ] && echo "✅ Los ${#SERVICIOS[@]} microservicios compilaron correctamente" || echo "❌ ${FALLOS} microservicio(s) con error"
exit "$FALLOS"
