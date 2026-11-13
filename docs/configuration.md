# Configuration

## Machines and ports

Only ports **9092** and **8080–8100** on the Linux machine are reachable from the VM.

| Machine | Port | What |
|---|---|---|
| Linux (`10.18.64.177`) | 8080 | Jenkins (host service, multibranch job `ftrade`) |
| | 8081 | Trade API (`api` container → 8080 inside) |
| | 8082 | Postgres (`trading-postgres` → 5432 inside) |
| | 8083 | Redis price cache (`trading-redis` → 6379 inside) |
| | 8084 | auth-service: login, registration, token refresh (`trading-auth` → 3000 inside) |
| | 8085 | SonarQube (standalone container, not in compose) |
| | 8086 | Redis Insight, web UI for Redis (`trading-redis-insight`, `tools` profile only) |
| | 8087 | Kafbat UI, web UI for Kafka (`trading-kafka-ui`, `tools` profile only) |
| | 8088 | Adminer, web UI for Postgres (`trading-adminer`, `tools` profile only) |
| | 8089 | Web app (`trading-frontend`, nginx → 80 inside): platform-ui, plus `/v1`, `/auth` and `/api` forwarded to the trade API, auth-service and the reporting API |
| | 9092 | Kafka (`trading-kafka`, KRaft; controller on 9093 inside only) |
| Windows VM | 8081 | The app when run locally (`server.port`; 8080 is taken by another Jenkins) |
| Local | 8050 | Reporting API (`reporting/scripts/api.py`, 127.0.0.1) |
| | 4200 | platform-ui dev server (`ng serve`): proxies `/v1` and `/auth` to the Linux machine and `/api` to 8050 (`REPORTING_HOST` / `REPORTING_PORT`) |

## Docker Compose (`../docker-compose.yml`, Linux machine)

| Service | Image | Does |
|---|---|---|
| `market-sim` | `trading-platform-market-sim` (built from `market-sim/Dockerfile`) | Simulated market data with the same endpoints as Fauxnance. Prices are made up, worked out from the symbol and the time, so it stores nothing. Not published. To use a real Fauxnance instead, change `MARKET_DATA_BASE_URL` and `MARKET_DATA_API_KEY` |
| `kafka` | `apache/kafka:3.9.1` | Broker |
| `postgres` | `postgres:16-alpine` | Database; runs `schema.sql` + `sample-data.sql` + `auth_test_data.sql` + `efe_demo_history.sql` (a year of made-up past trades for `efe`, for the performance chart; run it by hand to add it to an existing database, see its header) + `staff_users.sql` (the `analyst` and `admin` logins) + `reporting/analytics_database.sql` (creates the `analytics` database) once, only when its volume is empty |
| `redis` | `redis:7.4-alpine` | Fauxnance price cache, daily price history (the dashboard's performance chart, kept until midnight UTC) and daily call count, the Finnhub news cache, and the last hour's trades as they go through (the trade page's live list); password-protected, kept on disk (append-only file) so the count survives a restart |
| `auth-service` | `trading-platform-auth` (built from `auth-service/Dockerfile`) | Login, registration and token refresh; signs JWTs with `JWT_SHARED_SECRET`. Uses the `users` and `refresh_tokens` tables |
| `api` | `trading-platform-app` | `POST /accounts/{accountId}/trades` → `trade.submitted` |
| `validation` | same | `trade.submitted` → `trade.validated` |
| `recording` | same | `trade.validated` → `trade.recorded` |
| `execution` | same | `trade.recorded` → `trade.accepted` (fulfilled trades only), `trade.finished` |
| `frontend` | `trading-platform-frontend` (built from `platform-ui/Dockerfile`) | The web app (nginx); forwards `/v1`, `/auth` and `/api` to `api`, `auth-service` and `reporting-api` |
| `reporting-api` | `trading-platform-reporting` (built from `reporting/Dockerfile`) | The analyst and admin pages' API (`api.py`); checks login tokens with `JWT_SHARED_SECRET`. Not published: reached through `frontend` at `/api` |
| `reporting-sync` | same | `sync.py` every hour: copies the trading database into `analytics` (everything the first time) |
| `reporting-consumer` | same | `consumer.py`: adds trades to `analytics` as they happen, from `trade.recorded`, `trade.accepted` and `trade.finished` |
| `redis-insight` | `redis/redisinsight:3.8` | Web UI for Redis, already connected to `redis`. `tools` profile only |
| `kafka-ui` | `ghcr.io/kafbat/kafka-ui:v1.5.0` | Web UI for Kafka topics, messages and consumer groups (read-only). `tools` profile only |
| `adminer` | `adminer:6.1.2` | Web UI for Postgres: browse tables, run SQL. Log in with `POSTGRES_USER` / `POSTGRES_PASSWORD`. `tools` profile only |
| `ready` | `alpine:3.20` | Waits until every other service is up, prints where to find the app (`docker compose logs ready`), then idles. It stays running because `docker compose up --wait` counts any container that exits as a failure |

All four app services use the one image built from `Dockerfile`; each switches on only its own stage. Scale one with `docker compose up -d --scale execution=3` (at most 8 per stage get work, one per partition). Reset the database with `docker compose down -v`.

Once every service is up and answering, the `ready` service prints the app's addresses: in the log of a plain `docker compose up`, or with `docker compose logs ready` after `docker compose up -d --wait`.

`docker compose up -d` leaves out the three web UIs; `docker compose --profile tools up -d` starts them too. Redis Insight and Kafka UI have no login.

### `.env` (read by compose and the reporting scripts)

| Variable | Used for |
|---|---|
| `HOST_IP` | Linux machine's private IP: ports are published on it, Kafka advertises it |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Database name and login |
| `POSTGRES_PORT` | Host port for Postgres (`8082`) |
| `API_PORT` | Host port for the `api` service (`8081`) |
| `MARKET_DATA_BASE_URL`, `MARKET_DATA_API_KEY` | Price API: `market-sim` by default (`http://market-sim:8090/v1`), with any long random key |
| `MARKET_DATA_CACHE_TTL` | How long a price stays cached (`30s`) |
| `MARKET_DATA_DAILY_LIMIT` | Price API calls allowed a day (`1000000` for `market-sim`, which has no quota) |
| `FINNHUB_API_KEY` | Finnhub key for the dashboard's market news (optional: without it there is no news) |
| `REDIS_PORT`, `REDIS_PASSWORD` | Host port (`8083`) and password for Redis |
| `JWT_SHARED_SECRET` | Secret login tokens are signed (auth-service, as its `JWT_SECRET`) and checked (the app and the reporting API) with, at least 32 characters |
| `AUTH_PORT` | Host port for auth-service (`8084`) |
| `REDIS_INSIGHT_PORT`, `KAFKA_UI_PORT`, `ADMINER_PORT` | Host ports for the web UIs (`8086`, `8087`, `8088`) |
| `MISSION_DB_URL`, `ANALYTICS_DB_URL` | Reporting run by hand (`reporting/scripts`): trading database (read-only) and analytics database. Compose sets both itself from the Postgres settings |

### Environment variables set on the app containers

| Variable | Value |
|---|---|
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `${HOST_IP}:9092` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/${POSTGRES_DB}` |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | from `.env` |
| `SPRING_SQL_INIT_MODE` | `never` (Postgres creates the schema; `schema.sql` drops every table) |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `5` (Postgres allows 100 connections) |
| `MARKET_DATA_BASE_URL`, `MARKET_DATA_API_KEY`, `MARKET_DATA_CACHE_TTL` | from `.env` |
| `NEWS_API_KEY` | `${FINNHUB_API_KEY}`, empty if unset |
| `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT` | `redis`, `6379` |
| `SPRING_DATA_REDIS_PASSWORD` | `${REDIS_PASSWORD}` |
| `MANAGEMENT_HEALTH_REDIS_ENABLED` | `false` (Redis is only a cache, so it doesn't decide whether the app is UP) |
| `JWT_SHARED_SECRET` | from `.env` |
| `TRADE_STAGE_{API,VALIDATION,RECORDING,EXECUTION}_ENABLED` | `true` for the service's own stage, `false` for the rest |

## `application.properties` (running the app outside Docker)

Note that every property can also be set as an environment variable (`spring.kafka.bootstrap-servers` → `SPRING_KAFKA_BOOTSTRAP_SERVERS`).

| Property | Meaning |
|---|---|
| `server.port` | HTTP port (`8081` on the VM) |
| `spring.kafka.bootstrap-servers` | `10.18.64.177:9092` |
| `spring.datasource.url` / `.username` / `.password` | `jdbc:postgresql://10.18.64.177:8082/trading` |
| `spring.sql.init.mode` | `always` drops and reloads every table on start. Use `never` against the shared database |
| `spring.sql.init.data-locations` | `classpath:sample-data.sql` (with `always`) |
| `market.data.base-url`, `market.data.api-key` | Fauxnance |
| `market.data.cache-ttl` | How long a price stays in the Redis cache, e.g. `5m` |
| `news.api-key` | Finnhub key for market news (optional: without it there is no news) |
| `news.cache-ttl` | How long news stays in the Redis cache (default `10m`) |
| `spring.data.redis.host` / `.port` / `.password` | `10.18.64.177`, `8083`, and `REDIS_PASSWORD` from `.env` |
| `management.health.redis.enabled` | `false`: if Redis is down, prices come straight from Fauxnance |
| `jwt.shared-secret` | Same value as `JWT_SHARED_SECRET` in `.env`. Every endpoint except `/actuator/health` needs a login token signed with it |
| `trade.stage.{api,validation,recording,execution}.enabled` | Which stages run; unset counts as `true` |
| `spring.kafka.listener.concurrency` | Optional: listener threads per stage (default 1) |
| `trade.tracker.ttl` | Optional: how long a trade stays in the trade page's live list after its last step (default `1h`) |

## Kafka

| Topic | Partitions | Producer → consumer group |
|---|---|---|
| `trade.submitted` | 8 | API → `validation-service`, `trade-tracker` |
| `trade.validated` | 8 | validation → `recording-service`, `trade-tracker` |
| `trade.recorded` | 8 | recording → `execution-service`, `trade-tracker`, `analytics-service` |
| `trade.accepted` | 8 | execution, for fulfilled trades only → `analytics-service` (`reporting/scripts/consumer.py`) |
| `trade.finished` | 8 | execution → `trade-tracker`, `analytics-service` (`reporting/scripts/consumer.py`) |
| `trade.rejected` | 8 | validation, for a trade that failed it (after retries) → `trade-tracker` |

Created by `KafkaTopicConfig` at startup (`PARTITIONS = 8`, replication 1). The first three are keyed by account ID, so each account's trades stay in order; `trade.accepted` and `trade.finished` are keyed by trade ID and `trade.rejected` by external trade ID. `trade-tracker` runs in the `api` container and keeps each trade's steps in Redis for `GET /v1/accounts/{accountId}/trades/live`.

## CI (Jenkins, `Jenkinsfile`)

Stages: Build Image → Smoke-Test → (Code-Coverage, Static-Analysis, Reporting-Tests in parallel) → SonarQube.

On the public server, Jenkins and SonarQube run from `../docker-compose.ci.yml` (`docker compose -f docker-compose.ci.yml up -d --build`), as their own compose project on the platform's network. Caddy serves them at `jenkins.SITE_DOMAIN` and `sonar.SITE_DOMAIN`. Jenkins is set up from `../ci/jenkins`: `plugins.txt`, and `casc.yaml` for the settings, the logins and the `ftrade` job, which looks for new commits every five minutes, builds every branch of the repo (never pull requests from forks) and reports each build on its commit on GitHub. Anyone can look at builds; only the admin login can change anything. The table below still describes the first setup, on the Linux machine.

| `.env` (CI only) | Meaning |
|---|---|
| `DOCKER_GID` | The server's docker group (`getent group docker \| cut -d: -f3`), so Jenkins may use the Docker socket |
| `JENKINS_ADMIN_USER`, `JENKINS_ADMIN_PASSWORD` | The Jenkins admin login |
| `JENKINS_GITHUB_TOKEN` | GitHub token for this repo: Contents read, Commit statuses read and write, Pull requests read |
| `SONAR_DB_PASSWORD` | Password of SonarQube's own Postgres |
| `SONAR_TOKEN` | Analysis token made in SonarQube after its first start |

Once on the server, before the first start: `sudo mkdir -p /var/jenkins_home && sudo chown 1000:1000 /var/jenkins_home` (Jenkins's files, at the same path as in the container) and `echo 'vm.max_map_count=524288' | sudo tee /etc/sysctl.d/99-sonarqube.conf && sudo sysctl --system` (SonarQube's search engine).

The smoke test joins the platform's network (`trading-platform_default`) to reach Kafka, so the platform has to be running.

| Item | Value |
|---|---|
| Jenkins tools | Maven `Maven3`, SonarQube scanner `Sonar`, server `SonarQube` |
| Image | `fintech-five:<branch>` |
| Smoke test | Throwaway `postgres:17-alpine` + the image with `SPRING_SQL_INIT_MODE=always`, Kafka listeners off (`SPRING_KAFKA_LISTENER_AUTO_STARTUP=false`), dummy market data and JWT secret; waits up to four minutes for `/actuator/health` UP |
| Static analysis | Checkstyle (`config/checkstyle.xml`) + SpotBugs; new issues mark the build UNSTABLE |
| Coverage | JaCoCo (`target/site/jacoco/jacoco.xml`) |
| SonarQube | Project key `fintech-five`, at `http://10.18.64.177:8085` |
| Reporting tests | pytest in a `python:3.14` container |
