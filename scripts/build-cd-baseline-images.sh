#!/usr/bin/env bash
set -euo pipefail

DOCKERHUB_NAMESPACE="${DOCKERHUB_NAMESPACE:-thu2005}"
IMAGE_TAG="${IMAGE_TAG:-main}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

JAVA_MODULES=(
  cart
  tax
  order
  product
  media
  customer
  inventory
  search
  sampledata
  backoffice-bff
  storefront-bff
)

DOCKER_BUILDS=(
  "cart:yas-cart:cart"
  "tax:yas-tax:tax"
  "order:yas-order:order"
  "product:yas-product:product"
  "media:yas-media:media"
  "customer:yas-customer:customer"
  "inventory:yas-inventory:inventory"
  "search:yas-search:search"
  "sampledata:yas-sampledata:sampledata"
  "backoffice-bff:yas-backoffice-bff:backoffice-bff"
  "storefront-bff:yas-storefront-bff:storefront-bff"
  "backoffice:yas-backoffice:backoffice"
  "storefront:yas-storefront:storefront"
)

printf 'Building Java modules for CD baseline...\n'
if command -v mvn >/dev/null 2>&1; then
  mvn -B -ntp -pl "$(IFS=,; echo "${JAVA_MODULES[*]}")" -am -DskipTests clean package
else
  (
    cd "$REPO_ROOT/cart"
    MAVEN_USER_HOME="$REPO_ROOT/.m2" sh ./mvnw -f "$REPO_ROOT/pom.xml" -B -ntp -pl "$(IFS=,; echo "${JAVA_MODULES[*]}")" -am -DskipTests clean package
  )
fi

printf 'Building and pushing Docker images to %s with tag %s...\n' "$DOCKERHUB_NAMESPACE" "$IMAGE_TAG"
for entry in "${DOCKER_BUILDS[@]}"; do
  IFS=: read -r context image_name display_name <<< "$entry"
  image="${DOCKERHUB_NAMESPACE}/${image_name}:${IMAGE_TAG}"

  printf 'Building %s -> %s\n' "$display_name" "$image"
  if [ "$image_name" == "yas-media" ]; then
    cp -r "$REPO_ROOT/sampledata/images" "$context/images"
  fi
  docker build -t "$image" "$context"
  docker push "$image"
done

printf 'Baseline images pushed successfully.\n'
