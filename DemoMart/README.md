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

## Jenkins local staging pipeline

`Jenkinsfile` builds and tests all backend services, builds the React application, archives the deployable artifacts, deploys them to `C:\Apps\DemoMart`, and verifies every health endpoint. The deployed applications run as independent NSSM Windows services, so Jenkins can finish the job without owning the application processes.

Jenkins prerequisites:

- Maven installation named `Maven3`.
- Jenkins NodeJS plugin and a Node.js installation named `NodeJS22`.
- Secret-text credentials named `demomart-staging-jwt-secret` and `demomart-staging-internal-key`.
- NSSM at `C:\Tools\NSSM\nssm.exe`.

Configure the Pipeline job with:

```text
Repository: git@github.com:mridulmathur-repo/development-workspace.git
Branch: */main
Script path: DemoMart/Jenkinsfile
```

After a successful run, open `http://localhost:5174`. Persistent H2 data and service logs are stored below `C:\Apps\DemoMart`; a new deployment replaces application artifacts but preserves the database folders.

## EC2 production-like deployment

The production-like learning environment runs on one Amazon Linux 2023 `t3.small` instance. The existing file-upload application remains at `/`, while DemoMart uses the same Nginx server below `/demomart/`:

```text
http://<EC2-PUBLIC-IP>/             -> existing file-upload application
http://<EC2-PUBLIC-IP>/demomart/    -> DemoMart
```

Nginx serves the built React files directly and forwards `/demomart/api/` to the private API Gateway on `127.0.0.1:8090`. Node.js is required by Jenkins to build React, but it is not installed or run as a production server on EC2. Ports 8090-8093 and 8762 must remain closed to the internet; only HTTP port 80 and SSH from the administrator's IP are required.

The first EC2 deployment automatically creates:

```text
/opt/demomart/releases/<jenkins-build-number>  immutable application release
/opt/demomart/current                          link to the active release
/var/lib/demomart                              persistent H2 databases
/etc/demomart/demomart.env                     root-only production secrets
/etc/systemd/system/demomart-*.service          five Linux services
/etc/nginx/default.d/demomart.conf              /demomart Nginx routes
```

The `systemd` definitions use deliberately small JVM heaps for the two-GB learning instance. Configure at least one GB of swap before deployment. H2 is retained for the demo, but a real production system should use a managed database such as Amazon RDS.

Jenkins production prerequisites:

- SSH Username with private key credential `ec2-deploy-key`, username `ec2-user`.
- Secret-text credential `demomart-production-jwt-secret`, at least 32 single-line characters.
- Secret-text credential `demomart-production-internal-key`, a different value of at least 32 single-line characters.
- The EC2 public IP or DNS name entered in the build parameter `EC2_HOST`.

To deploy:

1. Run **Build with Parameters** for the `demomart-staging` Jenkins job.
2. Enter the current EC2 address in `EC2_HOST`.
3. Select `DEPLOY_TO_EC2`.
4. Jenkins builds and tests once, deploys the same artifacts to local staging, and verifies staging.
5. Review the staging application, then approve the production input prompt.
6. Jenkins securely copies the release and secrets, installs or updates the Linux services and Nginx route, waits for all internal health checks, and verifies the public `/demomart/` page.

If a replacement release fails its internal checks, the deployment script restores the previous `/opt/demomart/current` release. H2 data is never placed inside a release directory, so deployment and rollback preserve it.

## Security configuration

The identity service creates an eight-hour JWT after a successful password login. The gateway forwards it, and each business service independently checks the signature and role.

Local configuration includes a development JWT secret. For EC2, every service must receive the same strong secret through a protected environment variable:

```text
JWT_SECRET=<at-least-32-random-characters>
INTERNAL_SERVICE_KEY=<a-different-long-random-secret>
```

`INTERNAL_SERVICE_KEY` protects the stock-reservation operation so a browser user cannot reduce inventory without placing an order. Catalog and order services must receive the same value. Do not put either production value in Git, a Jenkinsfile, React, or an `application.yml` file. Jenkins transfers them over SSH into the root-readable `/etc/demomart/demomart.env` file; the production bundle and archived artifacts never contain them.

## Initial purchase flow

1. User logs in through `identity-service` and receives a JWT.
2. React requests phones through the gateway.
3. User chooses a phone and confirms or updates their saved address.
4. `order-service` discovers `catalog-service` through Eureka and uses its private service key to reserve one item.
5. `order-service` uses the catalog price, saves the order and marks payment as cash on delivery.

The first version demonstrates a synchronous microservice transaction. A later version can add events, compensation, Redis sessions/cache, Docker, Kubernetes, autoscaling, monitoring and distributed tracing.
