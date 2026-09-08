#!/usr/bin/env bash
#
# Provisiona en la instancia EC2 lo necesario para levantar las apps con Docker Compose.
# Ejecutar DENTRO de la instancia (Amazon Linux 2023) via SSH.
#
set -euo pipefail

echo ">> Instalando Docker y el plugin compose..."
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
DOCKER_CONFIG=${DOCKER_CONFIG:-/usr/libexec/docker}
sudo mkdir -p /usr/libexec/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/libexec/docker/cli-plugins/docker-compose
sudo chmod +x /usr/libexec/docker/cli-plugins/docker-compose

echo ">> Listo. Ahora:"
echo "   git clone <repo> && cd Caso2-CampusLab/infra/apps"
echo "   cp .env.example .env   # completar con Cognito/Entra/API GW"
echo "   docker compose up -d --build"
