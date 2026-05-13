### CI/CD

GitLab reads .gitlab-ci.yml, creates pipeline jobs, and the GitLab Runner executes each job in a temporary Kubernetes Pod. 
The test Pod runs Maven tests, the Kaniko Pod builds and pushes the Docker image to the registry, and the deploy Pod uses kubectl to update the Kubernetes Deployment. 
The Deployment then creates the real application Pod, which is exposed through Service and Ingress.