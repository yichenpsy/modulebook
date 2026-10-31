# Modulebook

Modulebook is a web application for maintaining university module handbooks. It stores module information as structured data, imports existing PDFs, and tracks edits and saved publication versions. It was developed as a bachelor thesis MVP at Berliner Hochschule für Technik (BHT).

## Features

- Create modulebooks for a study program and degree (`BACHELOR` or `MASTER`).
- Import a PDF, review extracted fields, and save or update modules.
- Edit module descriptions, teaching forms, workload, and status.
- Inspect change history, compare versions, and restore saved states.
- Print the current modulebook or a saved version using the browser, including its Save as PDF option.

## Technology

Java 21, Spring Boot 4.0.5, Spring JDBC, PostgreSQL 16, and Flyway. The frontend uses plain HTML, CSS, and JavaScript served by Spring Boot. PDF extraction uses Python (3.12 in the container), PyMuPDF4LLM, and FastAPI. OpenTelemetry provides application instrumentation. GitLab CI tests the Java application and builds and deploys both application images to Kubernetes.

## Start locally

Prerequisites: JDK 21 and Docker with its daemon running. Run commands from the repository root. Maven is provided by `./mvnw`; no separate Maven installation or frontend build is required.

Create the local database container once:

```bash
docker run -d --name modulebook-postgres \
  -e POSTGRES_DB=modulebook \
  -e POSTGRES_USER=modulebook \
  -e POSTGRES_PASSWORD=modulebook \
  -p 127.0.0.1:5433:5432 \
  -v modulebook-postgres-data:/var/lib/postgresql/data \
  postgres:16
```

If the container already exists, use `docker start modulebook-postgres` instead. Check database readiness, repeating this command until it reports accepting connections:

```bash
docker exec modulebook-postgres pg_isready -U modulebook -d modulebook
```

Then start the application:

```bash
./mvnw spring-boot:run
```

Open [http://localhost:8081](http://localhost:8081). Flyway applies database migrations at startup.

`modulebook-postgres` is a chosen container name; `modulebook-postgres-data` is a named volume that preserves data. Database name and credentials match the local defaults in [application.properties](src/main/resources/application.properties). Port `5433` is the host database port; `5432` is the container port. App port `8081` comes from `server.port`. For overrides and troubleshooting, see [Local setup](docs/development/local-setup.md).

This starts module editing, history, and printing. PDF import requires an additional [Python setup and configuration step](docs/development/local-setup.md#2-enable-pdf-import): install the dependencies, start the FastAPI service, and restart Java with its import URL and a writable job directory. The checked-in default import paths are specific to the author's machine.

## Run tests

```bash
./mvnw test
```

See [Testing and evaluation](docs/development/testing.md) for coverage, manual acceptance checks, and the limits of historical thesis results.

## Documentation

| Guide | Purpose |
| --- | --- |
| [Requirements and scope](docs/product/requirements.md) | FR/NFR definitions, Figma prototypes, MVP boundaries, and source differences |
| [Local setup](docs/development/local-setup.md) | Configuration, PDF service, tests, and troubleshooting |
| [Testing and evaluation](docs/development/testing.md) | Automated coverage, acceptance checks, and historical thesis evidence |
| [Architecture](docs/architecture/overview.md) | Components, database model, workload, and API entry points |
| [Versioning](docs/architecture/versioning.md) | Change sets, snapshots, comparison, and restore behavior |
| [PDF import](docs/architecture/pdf-import.md) | Extraction, review, configuration, and limitations |
| [Deployment](docs/deployment.md) | Containers, CI/CD, Kubernetes, and operational checks |
| [Architecture decisions](docs/adr/README.md) | Reasons, alternatives, and trade-offs behind key choices |

## Repository layout

```text
src/main/java/de/bht/modulebook/  Java API, business logic, and repositories
src/main/resources/static/        Browser interface
src/main/resources/db/migration/  Database migration history
src/test/                         Java tests
services/pdf_import_service/      Python HTTP service
tools/pdf_import/module_import_v2/Active PDF extraction pipeline
tools/pdf_import/configs/         Extraction rules and source manifest
k8s/                              Cluster deployment manifests
docs/                             Developer documentation
```

## Current limitations

PDF extraction depends on recognizable table layouts and requires human review. Import saves can partially succeed; inspect the returned errors. Authentication and authorization are not implemented. The checked-in deployment targets the BHT environment and requires environment-specific configuration. See the linked guides before operating on shared data.
