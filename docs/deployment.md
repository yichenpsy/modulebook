# Deployment and operations

The checked-in deployment targets GitLab and the BHT Kubernetes environment. The manifests are an environment-specific baseline; namespace permissions, registry access, DNS, an NGINX ingress controller, and persistent storage must already be available.

## Images

| Build file | Runtime | Port |
| --- | --- | --- |
| `Dockerfile` | Java 21 JRE with the packaged Spring Boot app and OpenTelemetry Java agent | `8081` |
| `Dockerfile.pdf-import-service` | Python 3.12 with FastAPI and the extraction pipeline | `8000` |

Build from the repository root:

```bash
docker build -t modulebook:local .
docker build -f Dockerfile.pdf-import-service -t modulebook-pdf-import:local .
```

The tags ending in `:local` are chosen local image names, not registry addresses. The build context `.` supplies repository files. Java packaging skips tests inside the image build; run `./mvnw test` separately. The Java build downloads the latest OpenTelemetry agent and Python dependencies are unpinned, so builds are not fully reproducible.

## GitLab CI/CD

[.gitlab-ci.yml](../.gitlab-ci.yml) defines three stages, assigned to runners tagged `modulebook`:

1. **test:** Maven on Java 21 runs `mvn clean test`, with a PostgreSQL 16 service.
2. **docker:** Kaniko builds and pushes the Java and Python images without a Docker daemon. Each image receives the commit short SHA and `latest` tags.
3. **deploy:** applies the PostgreSQL, Python service, application, Service, and Ingress manifests; selects both commit-tagged images; waits for both application rollouts.

Image build and deployment currently run on the default branch and on `feature/pdf-import-pipeline`. The test job has no equivalent branch restriction. Review the feature-branch deployment rule before reusing this pipeline for another environment.

| Variable | Source and meaning |
| --- | --- |
| `IMAGE_NAME` | Defined in CI as `$CI_REGISTRY_IMAGE/modulebook-image` |
| `PDF_IMPORT_IMAGE_NAME` | Defined in CI as `$CI_REGISTRY_IMAGE/pdf-import-service-image` |
| `CI_REGISTRY_IMAGE` | GitLab-provided registry path for this project |
| `CI_REGISTRY`, `CI_REGISTRY_USER`, `CI_REGISTRY_PASSWORD` | GitLab-provided registry authentication used by Kaniko |
| `CI_COMMIT_SHORT_SHA` | GitLab-provided commit identifier used as an image tag |
| `CI_PROJECT_DIR`, `CI_DEFAULT_BRANCH` | GitLab-provided checkout directory and default branch |

`k8s/runner-values.yml` describes the Kubernetes runner, including its tag, namespace, service account, and resource limits. Runner registration requires a token issued by GitLab. The tracked values file currently contains a literal registration credential; provision its replacement outside version control and rotate the existing credential before reusing this runner configuration. Do not copy it into documentation or logs. Its configured service account must already have the permissions needed for jobs and deployment.

## Kubernetes configuration

| File | Resources and purpose |
| --- | --- |
| `k8s/postgres.yaml` | PostgreSQL 16, Secret, 1 GiB PVC, and internal Service |
| `k8s/pdf-import-service.yaml` | Python Deployment and Service; `/health` readiness/liveness probes |
| `k8s/deployment.yaml` | Java Deployment and its database, PDF, and telemetry environment |
| `k8s/service.yaml` | Routes internal port `80` to Java port `8081` |
| `k8s/ingress.yaml` | Routes the configured hostname through NGINX to the Java Service |

The Java app connects to `modulebook-postgres:5432` and `http://pdf-import-service:8000/import`. Those hostnames come from Kubernetes Service names. The ingress host is currently `modulebook.project.ris.bht-berlin.de`; changing it requires corresponding DNS and ingress configuration. Image repository paths are BHT-specific and must match your registry.

All manifests omit a namespace. `kubectl` uses its active namespace, including in CI. The runner's job namespace does not by itself establish the intended application namespace; verify the deployment context.

### Required handover adjustments

- Replace the example database credentials consistently in the PostgreSQL Secret and Java configuration; the Java manifest currently uses literal credentials rather than reading that Secret.
- Set Java's `pdf-import.job-root` to a writable container path. The checked-in application default is an author-specific absolute path, and the deployment does not override it. For example, set `JAVA_TOOL_OPTIONS=-Dpdf-import.job-root=/tmp/modulebook-import-jobs` in the Java container environment. The directory name is chosen; use a mounted path if uploads/results must survive replacement of the pod.
- Verify the OTLP collector endpoint, registry pull access, storage class, namespace permissions, and ingress hostname for the destination cluster.

These adjustments are deployment configuration work; the documentation change does not apply them to a live cluster.

## Verification and logs

After selecting the intended cluster context, use an explicit namespace:

```bash
kubectl -n YOUR_NAMESPACE get pods,svc,ingress,pvc
kubectl -n YOUR_NAMESPACE rollout status deployment/modulebook-deployment
kubectl -n YOUR_NAMESPACE rollout status deployment/pdf-import-service
kubectl -n YOUR_NAMESPACE logs deployment/modulebook-deployment --tail=100
kubectl -n YOUR_NAMESPACE logs deployment/pdf-import-service --tail=100
```

Replace `YOUR_NAMESPACE` with your application namespace; the runner values currently use `yizh8704` for CI job pods. Deployment names above come from manifest `metadata.name` values.

Check that migrations completed, the homepage and `/api/modulebooks` respond, the PDF service is reachable from Java, and a reviewed PDF can be saved. A successful rollout alone does not verify this workflow. The Java Deployment has no application readiness/liveness probes.

OpenTelemetry is enabled by the Java agent and FastAPI instrumentation. Both manifests point at an external OTLP gRPC collector on port `4317`; that collector and any dashboards are not provisioned by this repository. The Python service only exports traces when `OTEL_EXPORTER_OTLP_ENDPOINT` is set.

### Verify traces in the external backend

The thesis (§4.9 and §5.2) reports inspection of collected traces in OpenObserve in the BHT environment. OpenObserve is an external inspection backend, not a service deployed by this repository. Obtain its current address and access from the environment owner; the repository does not provide a verified dashboard URL or credentials.

After recording the request time, perform a module read/edit and a PDF normalization. In the environment's trace interface, search that time range for the configured service names `modulebook` and `pdf-import-service`. Inspect request spans, durations, and errors; check whether the Java request and Python import are correlated in the same trace. Seeing two services independently does not prove propagation works. Missing spans require checking application instrumentation/export settings and collector routing. No detailed extraction-stage spans or complete metrics/logging stack are configured by the Python service.

## Data and recovery

PostgreSQL uses a PVC; deleting a pod is different from deleting its storage. No backup/restore automation is included. Establish and verify database backups before shared use or schema changes. Application versions and change sets are stored in the same database and cannot replace a backup.

A previous commit-tagged application image can be selected for rollback, but database migrations are not reversed by an image rollback. Check schema compatibility first. Import jobs use local files without an automated retention policy. JDBC debug/trace logging is enabled in `application.properties`; review its volume and data exposure before shared deployment. The ingress manifest contains no TLS configuration, and the application provides no authentication or authorization.
