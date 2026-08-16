# DemoMart

DemoMart is a learning-oriented online phone store built as Spring Boot microservices with a React interface. The first version runs locally as staging; the same deployable JARs will later run on EC2. Docker and Kubernetes are intentionally the next phase, after the service boundaries and CI/CD flow are understood.

## Architecture

```text
React UI (5174)
       |
       v
API Gateway (8090)
       |
       +------> Identity Service (8091) -- H2 identity database
       +------> Catalog Service  (8092) -- H2 catalog database
       +------> Order Service    (8093) -- H2 order database
                         |
                         +------> discovers and calls Catalog Service

Every backend service registers in Eureka Service Registry (8762).
```

Eureka is the service directory. A service asks Eureka for `catalog-service`; it does not need to know that the current instance is using port 8092. Spring Cloud LoadBalancer selects a registered instance. Kubernetes service discovery will replace this mechanism in the later Kubernetes phase.

## Services

| Component | Responsibility | Local port |
|---|---|---:|
| `service-registry` | Eureka registration and discovery | 8762 |
| `api-gateway` | Single API entrance and service-name routing | 8090 |
| `identity-service` | Registration, login, JWT, addresses, users and roles | 8091 |
| `catalog-service` | Phone search, inventory creation and stock reservation | 8092 |
| `order-service` | Checkout, total, delivery address and COD orders | 8093 |
| `frontend` | React role-based web interface | 5174 |

Each business microservice owns a separate H2 file database under its own `data/` folder. Services do not directly read one another's tables.

## Roles

- `END_USER`: browse/search phones, buy, update delivery address and view orders.
- `INVENTORY_MANAGER`: all end-user actions plus add inventory.
- `ADMIN`: all inventory-manager actions plus view users and change roles.

Newly registered users always receive `END_USER`.

Demo accounts are inserted on first startup:

| Role | Email | Password |
|---|---|---|
| End user | `user@demomart.local` | `User@123` |
| Inventory manager | `inventory@demomart.local` | `Inventory@123` |
| Admin | `admin@demomart.local` | `Admin@123` |

These credentials are only for local learning. Set `SEED_DEMO_USERS=false` in production.

## Run locally

Requirements: JDK 17+, Maven 3.9+, Node.js 20+.

From PowerShell:

```powershell
cd C:\Users\Lenovo\OneDrive\Documents\ChatGPT\Development\DemoMart
powershell -ExecutionPolicy Bypass -File .\scripts\start-local.ps1
```

Then start React in a second terminal:

```powershell
cd C:\Users\Lenovo\OneDrive\Documents\ChatGPT\Development\DemoMart\frontend
npm install
npm run dev
```

Open `http://localhost:5174`. View registered services at `http://localhost:8762`.

Stop the Java services with:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-local.ps1
```

## Security configuration

The identity service creates an eight-hour JWT after a successful password login. The gateway forwards it, and each business service independently checks the signature and role.

Local configuration includes a development JWT secret. For EC2, every service must receive the same strong secret through a protected environment variable:

```text
JWT_SECRET=<at-least-32-random-characters>
INTERNAL_SERVICE_KEY=<a-different-long-random-secret>
```

`INTERNAL_SERVICE_KEY` protects the stock-reservation operation so a browser user cannot reduce inventory without placing an order. Catalog and order services must receive the same value. Do not put either production value in Git, a Jenkinsfile, React, or an `application.yml` file. They will be kept in a root-readable EC2 environment file when production deployment is added.

## Initial purchase flow

1. User logs in through `identity-service` and receives a JWT.
2. React requests phones through the gateway.
3. User chooses a phone and confirms or updates their saved address.
4. `order-service` discovers `catalog-service` through Eureka and uses its private service key to reserve one item.
5. `order-service` uses the catalog price, saves the order and marks payment as cash on delivery.

The first version demonstrates a synchronous microservice transaction. A later version can add events, compensation, Redis sessions/cache, Docker, Kubernetes, autoscaling, monitoring and distributed tracing.
