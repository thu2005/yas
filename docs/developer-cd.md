# Developer CD Flow

This document covers the Jenkins jobs and the co-working workflow for the developer CD scope.

## Jenkins jobs

### `developer_build`

Purpose: deploy a developer test namespace from branch-specific Docker image tags and print the NodePort URLs.

Suggested parameters:
- `TAG_TAX`: branch name for the tax service, for example `dev_tax_service`
- Other `TAG_*` parameters: keep `main` unless that service should use a branch-specific image

### `developer_destroy`

Purpose: delete the developer test namespace after testing.

Suggested parameters:
- `TARGET_NAMESPACE`: namespace printed by the `developer_build` job, for example `test-user-tax`

## Flow

1. Developer works on a branch such as `dev_tax_service`.
2. The CI job builds and pushes the changed service image to Docker Hub with the short commit id tag.
3. Developer opens `developer_build` and enters the branch name only for the changed service parameter.
4. Jenkins resolves each branch parameter to the expected Docker image tag.
5. Jenkins deploys all YAS services into a generated test namespace, using the branch tag only for the changed service and `main` for the rest.
6. Jenkins prints the access URL in the form `http://<worker-ip>:<nodeport>`.
7. After testing, the `developer_destroy` job deletes the test namespace.

## Service Scope

The developer CD job deploys the services required for the e-commerce and service mesh demo:

```text
product
cart
order
customer
inventory
tax
media
search
storefront-bff
storefront-ui
backoffice-bff
backoffice-ui
swagger-ui
sampledata
```

`sampledata` is used to seed demo data and can be stopped after the initial data load. `swagger-ui` uses the public `swaggerapi/swagger-ui` image; the other source-built services use Docker Hub images under the `thu2005` namespace.

## Prerequisites

- Jenkins credential `dockerhub-credentials` must use the Docker Hub username and an access token/password that can push to the `thu2005` namespace.
- Before the first developer deploy, build and push the baseline `main` images for the source-built services in the service scope, for example `thu2005/yas-tax:main`.
- The Jenkins agent must have `git`, `docker`, `kubectl`, and `helm` available, and it must point to the Kubernetes cluster used for the demo.

To bootstrap the baseline images from a local machine or Jenkins agent:

```bash
docker login
DOCKERHUB_NAMESPACE=thu2005 IMAGE_TAG=main ./scripts/build-cd-baseline-images.sh
```

## Host file setup

Because this assignment does not assume public DNS, each developer should map the virtual domain to the worker node IP in the local hosts file.

Example for a worker node at `192.168.49.2`:

```text
192.168.49.2 api.yas.local.com
192.168.49.2 storefront.yas.local.com
192.168.49.2 backoffice.yas.local.com
192.168.49.2 tax.yas.local.com
```

If you use Minikube, replace the worker IP with the output of `minikube ip`.

## Co-working test scenario

A practical end-to-end test for at least two services:

1. One member deploys a branch for `tax`.
2. Another member keeps `cart` or `product` on `main`.
3. Access the storefront through the mapped domain.
4. Trigger a flow that reads both services, for example browsing products, adding an item to cart, and reaching checkout so the tax logic is exercised.
5. Use the printed NodePort URL to confirm the changed service is actually served from the developer branch.

This is enough to demonstrate that one service can be overridden by branch while the rest stays on the default image tag.
