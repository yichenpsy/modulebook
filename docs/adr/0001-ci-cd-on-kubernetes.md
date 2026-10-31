# ADR 0001: Use GitLab CI for Kubernetes delivery

Status: Accepted — implemented. Recorded retrospectively on 2026-10-31.

## Context

The application runs in the BHT Kubernetes environment. Java and Python require different runtimes and have separate Dockerfiles. The GitLab runner uses the Kubernetes executor with privileged containers disabled.

## Decision

Use GitLab CI to run Java tests, build both images with Kaniko, push them to the GitLab registry, and deploy them with `kubectl`. Tag images with the commit short SHA and `latest`; select the commit tag during deployment.

The Java application calls the Python PDF service over HTTP in Kubernetes. Each runtime has its own Deployment and internal Service.

## Rationale and alternatives

- Kaniko builds images without a Docker daemon and fits the configured unprivileged runner. Docker-in-Docker would require a separate daemon setup and a review of runner privileges.
- Separate images keep Python extraction dependencies out of the Java runtime. A combined image would simplify the number of deployed components but couple their dependencies and lifecycle.
- Automated delivery makes the build/deploy procedure repeatable. Manual builds and deployment would require operators to keep both images and manifests aligned.
- Commit tags identify the source revision used for a rollout. Deploying only `latest` would make that revision less explicit.

## Consequences

The setup depends on cluster permissions, registry access, storage, DNS, and an ingress controller. Two runtime services also introduce an HTTP dependency and separate logs.

Current limitations include environment-specific configuration, unpinned build dependencies, and deployment from both the default branch and `feature/pdf-import-pipeline`. Rollout completion does not prove the full import workflow works. Image rollback does not reverse database migrations.

## Implementation references

- [CI pipeline](../../.gitlab-ci.yml), [Java Dockerfile](../../Dockerfile), and [Python Dockerfile](../../Dockerfile.pdf-import-service).
- [Java Deployment](../../k8s/deployment.yaml) and [Python Deployment/Service](../../k8s/pdf-import-service.yaml).
- [Deployment guide](../deployment.md) for configuration, checks, and outstanding handover adjustments.
