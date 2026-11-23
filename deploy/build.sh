#!/usr/bin/env bash
# Build the server image and push it to ghcr.io (run `docker login ghcr.io` first).
# Prints the tag to pass to the playbook: ansible-playbook site.yml -e atchou_image_tag=<tag>
set -euo pipefail
cd "$(dirname "$0")/.."

IMAGE="${IMAGE:-ghcr.io/victorjoos/atchou-server}"
PLATFORM="${PLATFORM:-linux/amd64}"  # use linux/arm64 for an ARM VPS
TAG="$(git rev-parse --short HEAD)"

docker buildx build --platform "$PLATFORM" -t "$IMAGE:$TAG" -t "$IMAGE:latest" --push server
echo "Pushed $IMAGE:$TAG"
