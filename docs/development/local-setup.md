# Local setup

Run commands from the repository root. Use JDK 21 (the version configured in `pom.xml` and CI), Docker for PostgreSQL, and Python 3.12 for PDF import (the version used by its Docker image). Maven is provided by `./mvnw`; the first run needs access to download dependencies. Python dependencies are currently unpinned.

## 1. Start the database and application

Follow the [README quick start](../../README.md#start-locally). The PostgreSQL volume persists across container restarts. It starts empty on first use; Flyway creates the schema when the application starts.

For later runs:

```bash
docker start modulebook-postgres
docker exec modulebook-postgres pg_isready -U modulebook -d modulebook
```

Repeat the readiness check until PostgreSQL accepts connections, then start Spring Boot:

```bash
./mvnw spring-boot:run
```

No frontend build or separate web server is needed.

### Configuration

Defaults are in [application.properties](../../src/main/resources/application.properties).

| Setting | Local default | How to change it |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5433/modulebook` | Set the JDBC URL for your database host, port, and name |
| `DATABASE_USERNAME` | `modulebook` | Match the PostgreSQL user |
| `DATABASE_PASSWORD` | `modulebook` | Match that user's password |
| `SERVER_PORT` | `8081` | Override Spring's `server.port` |
| `PDF_IMPORT_SERVICE_URL` | Empty: use a local Python subprocess | Set the full HTTP import URL, including `/import` |
| `pdf-import.job-root` | Author-specific absolute path | Override with a writable local directory, including in HTTP mode |

The database variables are explicitly referenced in `application.properties`; `SERVER_PORT` uses Spring's environment binding. For example, if host port `5433` is occupied, publish the database on `5434` and start the app with:

```bash
DATABASE_URL=jdbc:postgresql://localhost:5434/modulebook ./mvnw spring-boot:run
```

Changing the database name or user also requires matching PostgreSQL configuration. `POSTGRES_*` variables initialize a new data directory; changing them does not update users or passwords in an existing volume.

## 2. Enable PDF import

The recommended local path uses the same HTTP service separation as Kubernetes.

Create a virtual environment and install dependencies:

```bash
python3.12 -m venv .venv
.venv/bin/python -m pip install -r tools/pdf_import/requirements.txt
.venv/bin/python -m uvicorn services.pdf_import_service.main:app --host 127.0.0.1 --port 8000
```

`.venv` is a chosen local environment directory and is ignored by Git. `services.pdf_import_service.main:app` identifies the existing FastAPI object. Port `8000` matches the PDF service container; you may change it together with the URL below.

Stop any previously running Java instance to free port `8081`. In a second terminal, from the repository root:

```bash
PDF_IMPORT_SERVICE_URL=http://127.0.0.1:8000/import \
  ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--pdf-import.job-root=./tools/pdf_import/data/import_jobs
```

The relative job directory is resolved from the Java working directory, which is the repository root for this command. It overrides the author-specific default without editing tracked configuration and also works when the repository path contains spaces. The Java app still stores uploads and returned JSON there when using HTTP mode.

Verify the services:

```bash
curl --fail http://127.0.0.1:8000/health
curl --fail http://localhost:8081/api/modulebooks
```

Expected results: `{"status":"ok"}` from Python and a JSON array (initially `[]`) from Java. Create a modulebook in the browser, choose **Import PDF**, review the extracted fields, and save. Then check the modules and history.

### Alternative: local Python subprocess

To run extraction directly from Java, install the same Python dependencies, leave `PDF_IMPORT_SERVICE_URL` empty, and override all machine-specific paths:

```bash
PDF_IMPORT_SERVICE_URL= ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--pdf-import.python-executable=$PWD/.venv/bin/python --pdf-import.repo-root=$PWD --pdf-import.job-root=$PWD/tools/pdf_import/data/import_jobs --pdf-import.base-yaml=$PWD/tools/pdf_import/configs/base.yaml --pdf-import.import-manifest=$PWD/tools/pdf_import/configs/import_manifest.yaml"
```

These property names come from `PdfImportProperties` and `application.properties`. `$PWD` expands to your repository path. The executable selects your virtual environment; the repository root is the subprocess working directory; the remaining paths select job storage and extraction configuration. This example assumes the repository path has no spaces; use HTTP mode if it does.

## 3. Run tests

```bash
./mvnw test
```

Java tests cover controllers, module editing, change logging, import mapping/saving, and the HTTP bridge to a stub PDF service. The application context test disables Flyway. These tests do not establish that real PostgreSQL migrations, PDF extraction, browser behavior, or Kubernetes deployment work end to end. CI also provisions PostgreSQL 16.

Before handing over a release, use a disposable database and the [acceptance checklist](testing.md#manual-acceptance-checks) to check migrations, editing, import, comparison, restore, and printing. The [testing guide](testing.md) also distinguishes automated coverage from historical thesis evaluation.

## Optional: inspect database data

Use any PostgreSQL client with host `localhost`, port `5433`, database/user/password `modulebook`, unless overridden. For example:

```bash
docker exec -it modulebook-postgres psql -U modulebook -d modulebook
```

`-U` selects the database user; `-d` selects the database. In pgAdmin, the server display name is arbitrary (for example, `modulebook-local`). A pgAdmin container cannot use `localhost` to reach PostgreSQL in another container; attach both to a shared Docker network and use the PostgreSQL container name with port `5432`.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Docker cannot connect | Start the Docker daemon/Desktop |
| Container name already exists | Start the existing container; do not rerun its creation command |
| Database connection refused | Container readiness, published port, and `DATABASE_URL` |
| Java compilation fails | Confirm `java -version` and `./mvnw -v` use JDK 21 |
| Import fails with a missing path or permission error | Override `pdf-import.job-root`; for subprocess mode, override all five paths above |
| PDF service request fails | Check `/health`, the service terminal, and that the URL ends in `/import` |
| Python reports `No module named ...` | Install the full requirements with `.venv/bin/python -m pip install -r tools/pdf_import/requirements.txt`; use the same interpreter to start the service |
| Import finishes with incomplete fields | Review the report and [extraction limitations](../architecture/pdf-import.md#limitations) |

Flyway startup failures require inspecting the migration error and existing schema. Do not delete migration history or a persistent database to bypass the error. Applied migrations should remain unchanged; add a new migration for schema changes.
