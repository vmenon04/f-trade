# Configuration

## Where it runs

The platform runs on one public server (an ARM virtual machine on Oracle Cloud, Ubuntu), from the compose files at the repo root. Only ports 22, 80 and 443 are open to the internet, and Caddy, on 80 and 443, is the only way in to the platform:

| Address | What |
|---|---|
| `https://f-trade.duckdns.org` | The web app: Caddy → `frontend` (nginx), which serves platform-ui and forwards `/v1`, `/auth` and `/api` to the trade API, auth-service and the reporting API |
| `https://jenkins.f-trade.duckdns.org` | Jenkins (multibranch job `ftrade`). Anyone can look; changing anything needs the admin login |
| `https://sonar.f-trade.duckdns.org` | SonarQube (project `fintech-five`) |

Everything else is published on the server's `127.0.0.1` only (`HOST_IP` in `.env`). To use one of these from your own machine, forward it over SSH, e.g. `ssh -L 8088:127.0.0.1:8088 ubuntu@<server>` and then open `http://localhost:8088`.

| Port (on `HOST_IP`) | What |
|---|---|
| 8081 | Trade API (`api` container → 8080 inside) |
| 8082 | Postgres (`trading-postgres` → 5432 inside) |
| 8083 | Redis price cache (`trading-redis` → 6379 inside) |
| 8084 | auth-service: login, registration, token refresh (`trading-auth` → 3000 inside) |
| 8086 | Redis Insight, web UI for Redis (`trading-redis-insight`, `tools` profile only) |
| 8087 | Kafbat UI, web UI for Kafka (`trading-kafka-ui`, `tools` profile only) |
| 8088 | Adminer, web UI for Postgres (`trading-adminer`, `tools` profile only) |
| 8089 | Web app (`trading-frontend`, nginx → 80 inside), without Caddy and HTTPS |
| 9092 | Kafka (`trading-kafka`, KRaft; controller on 9093 inside only) |

When developing on your own machine:

| Port | What |
|---|---|
| 8050 | Reporting API run by hand (`reporting/scripts/api.py`, 127.0.0.1) |
| 4200 | platform-ui dev server (`ng serve`): proxies `/v1` and `/auth` to the machine running compose (`HOST_IP` from the root `.env`, or `BACKEND_HOST`; see `platform-ui/proxy.conf.mjs`) and `/api` to 8050 (`REPORTING_HOST` / `REPORTING_PORT`) |

The Bruno collection (`../bruno`) has two environments: `public` (the public server) and `local` (`127.0.0.1:8081` and `:8084`: on the server itself, through an SSH forward, or change them to your `HOST_IP`).

## Docker Compose

| File | What |
|---|---|
| `../docker-compose.yml` | The platform. Enough by itself on any machine: `docker compose up -d` |
| `../docker-compose.prod.yml` | What the public server changes, used on top of the first: `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build` |
| `../docker-compose.ci.yml` | Jenkins and SonarQube, their own compose project (see CI below) |

### Services (`../docker-compose.yml`)

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

All four app services use the one image built from `Dockerfile`, and the three reporting services the one built from `reporting/Dockerfile`; each app service switches on only its own stage. Only `api` and `reporting-api` build their image and the others use it. Built once, an unchanged image keeps its ID, so compose leaves its containers running. Scale one with `docker compose up -d --scale execution=3` (at most 8 per stage get work, one per partition). Reset the database with `docker compose down -v`.

Once every service is up and answering, the `ready` service prints the app's addresses: in the log of a plain `docker compose up`, or with `docker compose logs ready` after `docker compose up -d --wait`.

`docker compose up -d` leaves out the three web UIs; `docker compose --profile tools up -d` starts them too. Redis Insight and Kafka UI have no login.

### On the public server (`../docker-compose.prod.yml`)

| Change | Why |
|---|---|
| `caddy` service (`caddy:2-alpine`, ports 80 and 443) | HTTPS for `SITE_DOMAIN`, `jenkins.SITE_DOMAIN` and `sonar.SITE_DOMAIN` (`../Caddyfile`); gets its certificates from Let's Encrypt by itself |
| `HOST_IP=127.0.0.1` in `.env` | Every published port is only reachable on the server itself |
| Kafka advertises `kafka:9092`, and the app services, `reporting-consumer` and `kafka-ui` connect to it | `127.0.0.1` inside a container is the container itself |
| `JAVA_TOOL_OPTIONS=-Xmx512m` on the app services, `KAFKA_HEAP_OPTS` on Kafka | Each JVM would otherwise allow itself a quarter of the server's memory |
| The app services' health check allows five minutes | The four start at the same time and share the server's cores |

nginx in `frontend` looks the services behind it up again every few seconds, so it follows a container that compose has recreated (see `platform-ui/nginx.conf`).

### `.env` (read by compose and the reporting scripts)

| Variable | Used for |
|---|---|
| `HOST_IP` | The address ports are published on, and the one Kafka advertises: `127.0.0.1` on the public server (where `docker-compose.prod.yml` makes Kafka advertise `kafka:9092` instead), or the machine's own IP to reach the services from other machines |
| `SITE_DOMAIN` | Public server only: the name the web app is served at (`f-trade.duckdns.org`) |
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
| `UI_PORT` | Host port for the web app (`8089`) |
| `LOGO_DEV_CRED_KEY` | logo.dev publishable key for company and coin logos (optional: logos fall back to the ticker's first letter) |
| `MISSION_DB_URL`, `ANALYTICS_DB_URL` | Reporting run by hand (`reporting/scripts`): trading database (read-only) and analytics database. Compose sets both itself from the Postgres settings |

### Environment variables set on the app containers

| Variable | Value |
|---|---|
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `${HOST_IP}:9092` (`kafka:9092` on the public server) |
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
| `server.port` | HTTP port (`8081` in `application.properties.example`) |
| `spring.kafka.bootstrap-servers` | Kafka, at `HOST_IP` of the machine running compose, e.g. `127.0.0.1:9092` |
| `spring.datasource.url` / `.username` / `.password` | Postgres on `POSTGRES_PORT`, e.g. `jdbc:postgresql://127.0.0.1:8082/trading` |
| `spring.sql.init.mode` | `always` drops and reloads every table on start. Use `never` against the shared database |
| `spring.sql.init.data-locations` | `classpath:sample-data.sql` (with `always`) |
| `market.data.base-url`, `market.data.api-key` | The price API. `market-sim` isn't published by compose, so run it by hand (`MARKET_DATA_API_KEY=<the same key> INSTRUMENTS_SQL=src/main/resources/sample-data.sql python market-sim/server.py`, port 8090, so `http://127.0.0.1:8090/v1`) or point this at a real Fauxnance |
| `market.data.cache-ttl` | How long a price stays in the Redis cache, e.g. `5m` |
| `news.api-key` | Finnhub key for market news (optional: without it there is no news) |
| `news.cache-ttl` | How long news stays in the Redis cache (default `10m`) |
| `spring.data.redis.host` / `.port` / `.password` | Redis on `REDIS_PORT`, e.g. `127.0.0.1`, `8083`, and `REDIS_PASSWORD` from `.env` |
| `management.health.redis.enabled` | `false`: if Redis is down, prices come straight from the price API |
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

## Deploys (GitHub Actions, `../.github/workflows/deploy.yml`)

Every push to `main`, or a run started by hand from the Actions tab, deploys to the public server. The run logs in over SSH and, in `~/f-trade`, pulls `main` and runs `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build --wait`: compose rebuilds what changed and recreates only those containers, and the run fails unless every service comes back healthy. The images are built on the server; nothing is pushed to a registry. Each run is listed under Deployments on the repo's page (environment `production`).

| Item | Value |
|---|---|
| Secrets | `DEPLOY_HOST` (the server's IP), `DEPLOY_SSH_KEY` (a private key the server's `ubuntu` user accepts), `DEPLOY_KNOWN_HOSTS` (the server's host key, from `ssh-keyscan -t ed25519 <the IP>`) |
| `.env` | Only on the server, in `~/f-trade`; never in git or in the workflow |
| `BUILDX_NO_DEFAULT_ATTESTATIONS=1` | Stops each build adding a record of how the image was built, which would give an unchanged image a new ID and recreate its containers |
| Caddy | Restarted when `Caddyfile` changed, because compose doesn't notice and the running container still has the old file |

Jenkins and SonarQube are not part of a deploy. After a change to `ci/jenkins` or `docker-compose.ci.yml`, run `docker compose -f docker-compose.ci.yml up -d --build` on the server.

## CI (Jenkins, `../Jenkinsfile`)

Stages: Build Image → Smoke-Test → (Code-Coverage, Static-Analysis, Reporting-Tests in parallel) → SonarQube.

Jenkins and SonarQube run on the public server from `../docker-compose.ci.yml` (`docker compose -f docker-compose.ci.yml up -d --build`), as their own compose project on the platform's network, where Caddy reaches them and the smoke test reaches Kafka. So the platform has to be running first.

Jenkins is set up from `../ci/jenkins`, not by hand: `Dockerfile` (JDK 25, Maven, the Docker client), `plugins.txt`, and `casc.yaml` for the settings, the logins and the `ftrade` job. Changes made on its pages are lost when it restarts. The job looks for new commits every five minutes, builds every branch of the repo (never pull requests from forks), reports each build on its commit on GitHub, and forgets a branch once it is deleted there. Builds run on the server's own Docker, through its socket, one at a time.

| `.env` (CI only) | Meaning |
|---|---|
| `DOCKER_GID` | The server's docker group (`getent group docker \| cut -d: -f3`), so Jenkins may use the Docker socket |
| `JENKINS_ADMIN_USER`, `JENKINS_ADMIN_PASSWORD` | The Jenkins admin login |
| `JENKINS_GITHUB_TOKEN` | GitHub token for this repo: Contents read, Commit statuses read and write, Pull requests read. It expires; Jenkins stops seeing new commits when it does |
| `SONAR_DB_PASSWORD` | Password of SonarQube's own Postgres |
| `SONAR_TOKEN` | Analysis token made in SonarQube (My Account → Security) after its first start. It expires too |

Once on the server, before the first start: `sudo mkdir -p /var/jenkins_home && sudo chown 1000:1000 /var/jenkins_home` (Jenkins's files, at the same path as in the container) and `echo 'vm.max_map_count=524288' | sudo tee /etc/sysctl.d/99-sonarqube.conf && sudo sysctl --system` (SonarQube's search engine).

| Item | Value |
|---|---|
| Jenkins tools | Maven `Maven3` (in the image), SonarQube server `SonarQube` (`http://sonarqube:9000`, inside Docker's network) |
| Image | `fintech-five:<branch>` |
| Smoke test | Throwaway `postgres:17-alpine` + the image with `SPRING_SQL_INIT_MODE=always`, on the platform's network (`trading-platform_default`) for Kafka, with the listeners off (`SPRING_KAFKA_LISTENER_AUTO_STARTUP=false`), dummy market data and JWT secret; waits up to four minutes for `/actuator/health` UP |
| Static analysis | Checkstyle (`config/checkstyle.xml`) + SpotBugs; new issues mark the build UNSTABLE |
| Coverage | JaCoCo (`target/site/jacoco/jacoco.xml`) |
| SonarQube | Project key `fintech-five`, at `https://sonar.f-trade.duckdns.org` (readable without a login) |
| Reporting tests | pytest in a `python:3.14` container |
