# How CI/CD Is Configured in This Application

## 1. Creating a GitLab Runner

A GitLab Runner is a worker that executes CI/CD jobs. In this project, the runner runs inside BHT Kubernetes and connects GitLab with the Kubernetes cluster.

1. Create the runner in GitLab, configured with the tag:

   ```text
   modulebook
   ```

   The runner token is saved for the Kubernetes configuration.

2. Configure the runner values in:

   ```text
   k8s/runner-values.yml
   ```

    runner-values.yml configures how the GitLab Runner is installed into Kubernetes.

3. Install or connect the runner to Kubernetes using Helm, with this command:

   ```bash
   helm upgrade --install gitlab-runner gitlab/gitlab-runner \
   --namespace yizh8704 \
   -f k8s/runner-values.yml
   ```

   After this step, the runner is available in Kubernetes and can receive jobs from GitLab.

## 2. Defining the CI/CD Pipeline

The CI/CD pipeline is defined in the file:

```text
.gitlab-ci.yml
```

When a push to the `main` branch happens, GitLab detects the `.gitlab-ci.yml` file automatically.

GitLab then reads this file and assigns the jobs to runners.

### Pipeline Stages

```yaml
stages:
  - test
  - docker
  - deploy
```

This CI/CD pipeline has three stages:

1. `test`
2. `docker`
3. `deploy`

The runner executes the jobs according to the order of these stages.

### Docker Image Variable

```yaml
variables:
  IMAGE_NAME: "$CI_REGISTRY_IMAGE/modulebook-image"
```

The image repository path is defined in the variable `IMAGE_NAME`.  
This path is later used when building and pushing the Docker image.  
`$CI_REGISTRY_IMAGE` is a predefined GitLab CI variable that contains the container registry path of this GitLab project. By adding `/modulebook-image`, the concrete image repository name is defined for this application.

### Job 1. Build and Test

```yaml
build-and-test:
  stage: test
  tags:
    - modulebook
  image: maven:3.9-eclipse-temurin-21
  script:
    - mvn clean test
```

The first job is named `build-and-test`.  
This job belongs to the `test` stage.  
GitLab assigns it to the GitLab Runner with the tag `modulebook`.  
Inside Kubernetes, the runner creates a pod for this job. In this pod, a container is started with the image:

```text
maven:3.9-eclipse-temurin-21
```

This container provides Maven and Java 21, which are required to build and test the application.  
The following command is executed inside the job container:

```bash
mvn clean test
```

Maven deletes old build artifacts, compiles and execute the java source code and tests code.

### Job 2. Build and push image

```yaml
build-and-push-image:
  stage: docker
  tags:
    - modulebook
  image:
    name: gcr.io/kaniko-project/executor:debug
    entrypoint: [""]
  variables:
    DOCKER_CONFIG: /kaniko/.docker/
  script:
    - mkdir -p /kaniko/.docker
    - echo "{\"auths\":{\"$CI_REGISTRY\":{\"username\":\"$CI_REGISTRY_USER\",\"password\":\"$CI_REGISTRY_PASSWORD\"}}}" > /kaniko/.docker/config.json
    - /kaniko/executor --context "$CI_PROJECT_DIR" --dockerfile "$CI_PROJECT_DIR/Dockerfile" --destination "$IMAGE_NAME:$CI_COMMIT_SHORT_SHA" --destination "$IMAGE_NAME:latest"
  rules:
    - if: "$CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH"
```

The second job is named `build-and-push-image`.  
This job belongs to the `docker` stage.  
GitLab assigns it to the GitLab Runner with the tag `modulebook`.  
Inside Kubernetes, the runner creates a pod for this job. In this pod, a container is started with the image:

```text
gcr.io/kaniko-project/executor:debug
```
Why Kaniko?  
Docker-in-Docker = run Docker daemon inside CI job container.  
Kaniko = build image without Docker daemon. 

A `kaniko` container can build Docker images without privileged access.  
In this job, a Docker image needs to be built inside a container. Normally, building Docker images inside a container requires privileged access, for example when using Docker-in-Docker. However, in the BHT Kubernetes cluster, the GitLab Runner must be configured with:

```toml
privileged = false
```

The runner can only create unprivileged containers.
Because of this restriction, kaniko is used. kaniko can build and push Docker images from inside an unprivileged container, so it works in this Kubernetes environment.

```yaml
variables:
  DOCKER_CONFIG: /kaniko/.docker/
```

This tells kaniko container where the Docker authentication configuration is stored.

```yaml
- mkdir -p /kaniko/.docker
- echo "{\"auths\":{\"$CI_REGISTRY\":{\"username\":\"$CI_REGISTRY_USER\",\"password\":\"$CI_REGISTRY_PASSWORD\"}}}" > /kaniko/.docker/config.json
```

The folder `/kaniko/.docker` is created for the Docker login config.  
The kaniko executor then creates the file:`/kaniko/.docker/config.json`.  
Kaniko container uses this file automatically when it pushes the image to the GitLab Container Registry for the login credentials.

```text
CI_REGISTRY
CI_REGISTRY_USER
CI_REGISTRY_PASSWORD
```

These variables are predefined by GitLab.

```yaml
/kaniko/executor \
--context "$CI_PROJECT_DIR" \
--dockerfile "$CI_PROJECT_DIR/Dockerfile" \
--destination "$IMAGE_NAME:$CI_COMMIT_SHORT_SHA" \
--destination "$IMAGE_NAME:latest"
```

`CI_PROJECT_DIR` is the folder where GitLab Runner downloaded the repository. This tells Kaniko:

```text
Use the project directory as the build context.
Use the Dockerfile from the project directory.
Build a container image.
Push the image with a tag based on the commit SHA.
Also push the same image with the tag latest.
```


### Job 3: deploy-to-kubernetes

In the third stage `deploy`, there is one job named `deploy-to-kubernetes`.
The job is executed by the GitLab Runner with the tag `modulebook`.

The runner creates a temporary Kubernetes Pod for this job.
Inside this Pod, the job runs in a container.
This container is created from this image:

```yaml
alpine/kubectl:latest
```

This image contains the `kubectl` command-line tool.
`kubectl` is used to talk to the Kubernetes cluster.  

This job runs after `build-and-push-image`, because of:
```yaml
needs:
  - build-and-push-image
```
The command
```yml
kubectl apply -f k8s/deployment.yaml
```
Meaning:
> Tells Kubernetes how this app should run, how many app copies to run and which container image to use.


The command
```yml
kubectl apply -f k8s/service.yaml
```
Meaning:
> Gives a stable internal network address to the pod so other parts of the cluster can reach the app.

The command
```yml
kubectl apply -f k8s/ingress.yaml
```
Meaning:
> Sets up external web traffic routing so users outside the cluster can reach the app.


The command
```yml
kubectl set image deployment/modulebook-deployment modulebook-deployment="$IMAGE_NAME:$CI_COMMIT_SHORT_SHA"
```

Meaning:

> Find the Kubernetes Deployment named `modulebook-deployment`.
> ```modulebook-deployment=...```: selects the container inside that Deployment named modulebook-deployment.
> Change this container’s image to the newly built image from this commit.
Example:

```text
registry.bht-berlin.de/ris/modulebook/modulebook-image:a1b2c3d
```

The command:
```yml
kubectl rollout status deployment/modulebook-deployment
```
Meaning:

> Wait and check whether the Deployment update succeeds.
Kubernetes will create a new Pod using the new image.
If the new Pod starts successfully, the rollout succeeds.
If the new Pod cannot start, the command fails or times out.
  
