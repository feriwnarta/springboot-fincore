#!/usr/bin/env bash
set -euo pipefail

# Replays retained Kafka events into local Search Service projections.
# Source PostgreSQL/MongoDB data is not changed.
docker compose stop search-service >/dev/null
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:19092 \
  --group fincore-search-service \
  --all-topics \
  --reset-offsets \
  --to-earliest \
  --execute

POSTGRES_HOST_PORT="${POSTGRES_HOST_PORT:-15432}" docker compose start search-service >/dev/null

for attempt in {1..30}; do
  if curl --fail --silent http://localhost:8083/actuator/health >/dev/null; then
    break
  fi
  sleep 2
done

docker compose exec -T elasticsearch curl --fail --silent \
  'http://localhost:9200/_cat/indices/fincore-*?h=index,docs.count,health' || true
