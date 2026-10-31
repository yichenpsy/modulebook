# ADR 0004: Use OpenTelemetry for application observability

Status: Accepted — application instrumentation implemented; collector infrastructure is external. Recorded retrospectively on 2026-10-31.

## Context

PDF import crosses the Java application and a separate Python HTTP service in Kubernetes. Diagnosing request failures and delays benefits from telemetry from both runtimes. The deployment manifests reference an existing collector in the BHT cluster.

## Decision

Use OpenTelemetry instrumentation in both services and configure them to export to the external collector using OTLP over gRPC.

- Java: load the OpenTelemetry Java agent through the container's `-javaagent` argument.
- Python: configure a trace provider, batch span processor, OTLP gRPC exporter, and FastAPI instrumentation. Exclude `/health` from request tracing.
- Kubernetes: configure service names and the collector destination through environment variables.

The repository configures applications to send telemetry; it does not deploy the collector or a storage and visualization backend.

The thesis (§4.9 and §5.2) reports a BHT deployment where collector output was inspected in OpenObserve. This establishes the historical environment described by the thesis; it does not verify current collection or provide a reusable backend deployment.

## Rationale and alternatives

- A shared instrumentation and export approach gives the two runtimes a common telemetry interface. Separate vendor-specific integrations would couple each service to a particular backend.
- The Java agent provides instrumentation without adding tracing calls throughout the business code. Manual instrumentation could add domain-specific detail but would require additional code and maintenance.
- Python's explicit provider and exporter setup makes its trace-export behavior visible in the service entry point. The exporter is only attached when a collector endpoint is supplied.
- Local logs remain useful, but logs alone do not provide the same request-span structure for investigating work across services.

## Configuration

| Setting | Source and purpose | What can change |
| --- | --- | --- |
| `OTEL_SERVICE_NAME` | Set in each Deployment as `modulebook` or `pdf-import-service`; Python also defaults to `pdf-import-service` | Choose descriptive, distinct service names |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Both Deployments point to `http://hudson-otel-collector.ris-lab.svc.cluster.local:4317` | Replace with a collector address reachable from the target cluster |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | Both Deployments set `grpc`; Java uses agent configuration, while Python selects the gRPC exporter directly in code | Keep the exporter and collector protocols aligned; changing this variable alone does not switch Python's exporter |

The collector hostname identifies the `hudson-otel-collector` Service in namespace `ris-lab`; port `4317` is the configured OTLP gRPC destination. These values describe the existing deployment environment, not a requirement for every installation.

## Consequences

Telemetry adds runtime processing and an external collector dependency. Collector availability, access controls, retention, and dashboards must be handled outside this repository. Application instrumentation alone does not establish that telemetry is arriving or that cross-service trace correlation works; verify these in the target environment.

The Python setup explicitly exports traces; a complete metrics/logs collection stack is not implemented here. The Java agent is enabled in the Docker image, so the ordinary local Maven startup does not load it automatically. Its download uses the latest release, and Python dependencies are unpinned; pin versions when reproducible instrumentation is required.

## Implementation references

- [Java agent setup](../../Dockerfile) and [Python tracing setup](../../services/pdf_import_service/main.py).
- [Java Deployment](../../k8s/deployment.yaml), [Python Deployment](../../k8s/pdf-import-service.yaml), and [Python dependencies](../../tools/pdf_import/requirements.txt).
- [Deployment guide](../deployment.md) for operational checks and environment configuration.
