# Docs

| Path | What |
|---|---|
| [configuration.md](configuration.md) | Where it runs, ports, Docker Compose services, environment variables, `application.properties`, Kafka topics, deploys, CI |
| [api/](api/) | OpenAPI 3 contracts, one per service area |
| [diagrams/](diagrams/) | Mermaid sources (`.mmd`) and rendered images (`.png`) |

## api/

| File | Covers |
|---|---|
| `account-trade-management-service.yaml` | Accounts, trades, holdings, order history |
| `auth-service.yaml` | Register, login, token refresh, logout |
| `clients-service.yaml` | Client information |
| `instruments-service.yaml` | Instruments and current prices |
| `order-management-service.yaml` | Placing and reading trades |

## diagrams/

| File | Shows |
|---|---|
| `order-sequence.mmd` / `.png` | How an order flows through the services (drawn before the Kafka pipeline) |
| `class-diagram.mmd` / `.png` | Domain classes (drawn before the Kafka pipeline) |
| `database-er.mmd` | Database tables and relationships |
