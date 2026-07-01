# Cài đặt hệ thống — Đồ án 2

Ghi lại những gì đã cài và cấu hình theo thứ tự. Dùng để viết báo cáo và tái tạo lại hệ thống nếu cần.

---

## 1. Jenkins (máy local)

**Môi trường:** WSL2 Ubuntu trên Windows, Docker Desktop.

**Cách cài:** Chạy Jenkins LTS bằng Docker:

```bash
docker run -d \
  --name jenkins-lts \
  -p 8081:8080 \
  -p 50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  jenkins/jenkins:lts
```

**Lưu ý quan trọng:** Jenkins container cần Docker CLI để build/push image. Docker socket được mount nhưng Docker CLI không có sẵn trong image. Cần copy thủ công:

```bash
docker cp /usr/bin/docker jenkins-lts:/usr/local/bin/docker
docker exec jenkins-lts chmod +x /usr/local/bin/docker
```

Kiểm tra:
```bash
docker exec jenkins-lts docker version
```

**Fix này không bền vững** — nếu container bị xóa/recreate thì phải copy lại. Cách lâu dài: build custom Jenkins image có sẵn Docker CLI.

### Plugins đã cài

- Pipeline
- Git
- Docker Pipeline
- Multibranch Pipeline
- Timestamper
- Build Discarder

### Credentials đã tạo

| Credential ID | Type | Dùng cho |
|---|---|---|
| `dockerhub-credentials` | Username/Password | Push image lên Docker Hub |
| `github-credentials` | Username/Password | Clone gitops-yas, push commit |

**Lưu ý:** `github-credentials` cần quyền write vào repo `thu2005/gitops-yas`. Dùng GitHub Personal Access Token (PAT) làm password.

### Jobs đã tạo

**Job 1: YAS-Microservices-CI** (Multibranch Pipeline)
- Branch Source: `https://github.com/thu2005/yas.git`
- Credentials: `github-credentials`
- Script Path: `Jenkinsfile`
- Build on push: tự động qua webhook hoặc poll SCM

**Job 2: developer_build** (Pipeline)
- Definition: Pipeline script from SCM
- SCM: Git → `https://github.com/thu2005/gitops-yas.git`
- Branch: `*/main`
- Script Path: `jenkins/Jenkinsfile.developer_build`
- Credentials: `github-credentials`

**Job 3: developer_build_cleanup** (Pipeline)
- Definition: Pipeline script from SCM
- SCM: Git → `https://github.com/thu2005/gitops-yas.git`
- Branch: `*/main`
- Script Path: `jenkins/Jenkinsfile.cleanup`
- Credentials: `github-credentials`

---

## 2. GKE Cluster

**GCP Project:** `yas-devops-project2`  
**Zone:** `us-east1-b` (us-central1-a bị GCE_STOCKOUT)

**Lệnh tạo cluster:**

```bash
gcloud container clusters create yas-gke \
  --project yas-devops-project2 \
  --zone us-east1-b \
  --num-nodes 2 \
  --machine-type e2-standard-4 \
  --disk-size 50
```

**Lấy credentials:**

```bash
gcloud container clusters get-credentials yas-gke \
  --zone us-east1-b \
  --project yas-devops-project2
```

---

## 3. Argo CD

**Namespace:** `argocd`  
**Version:** stable (latest khi cài)

```bash
kubectl create namespace argocd
kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl rollout status deployment/argocd-server -n argocd --timeout=120s
```

Expose UI qua NodePort:
```bash
kubectl patch svc argocd-server -n argocd \
  -p '{"spec":{"type":"NodePort"}}'
```

Lấy initial admin password:
```bash
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d
```

Apply AppProject và Applications:
```bash
kubectl apply -f gitops-yas/argocd/project.yaml
kubectl apply -f gitops-yas/argocd/applications/yas-dev.yaml
kubectl apply -f gitops-yas/argocd/applications/yas-staging.yaml
```

---

## 4. Infrastructure trên GKE

Tất cả Helm chart nằm tại `yas/k8s/deploy/`. Chạy từ thư mục đó:

```bash
cd ~/project2/yas/k8s/deploy
```

Thêm Helm repos một lần:
```bash
helm repo add postgres-operator-charts https://opensource.zalando.com/postgres-operator/charts/postgres-operator
helm repo add strimzi https://strimzi.io/charts/
helm repo add elastic https://helm.elastic.co
helm repo update
```

### 4.1 PostgreSQL

Dùng Zalando postgres-operator. Service name kết quả: `postgresql.postgres`.

```bash
helm upgrade --install postgres-operator \
  postgres-operator-charts/postgres-operator \
  --create-namespace --namespace postgres

kubectl wait --for=condition=Available deployment/postgres-operator \
  -n postgres --timeout=120s

helm upgrade --install postgres ./postgres/postgresql \
  --create-namespace --namespace postgres \
  --set replicas=1 \
  --set username=yasadminuser \
  --set password=admin
```

Kiểm tra:
```bash
kubectl get pods -n postgres
# postgresql-0   1/1   Running
```

### 4.2 Kafka

Dùng Strimzi KRaft mode (Strimzi 1.1.0 — không có ZooKeeper).

**Bug đã fix trong chart `yas/k8s/deploy/kafka/kafka-cluster`:**
- Thêm `KafkaNodePool` resource (bắt buộc từ Strimzi 1.x).
- Đổi Kafka version sang `4.3.0` (Strimzi 1.1.0 chỉ support 4.2.0, 4.2.1, 4.3.0).
- Bọc Debezium Connect/Connector trong `{{- if .Values.debeziumConnect.enabled }}` vì không cần cho demo cơ bản.

```bash
helm upgrade --install kafka-operator \
  strimzi/strimzi-kafka-operator \
  --create-namespace --namespace kafka

kubectl wait --for=condition=Available deployment/strimzi-cluster-operator \
  -n kafka --timeout=120s

helm upgrade --install kafka-cluster ./kafka/kafka-cluster \
  --create-namespace --namespace kafka \
  --set kafka.replicas=1 \
  --set zookeeper.replicas=1 \
  --set postgresql.username=yasadminuser \
  --set postgresql.password=admin
```

Kiểm tra:
```bash
kubectl get pods -n kafka
# kafka-cluster-combined-0   1/1   Running
```

Broker address: `kafka-cluster-kafka-brokers.kafka:9092`

### 4.3 Redis

Dùng Bitnami Redis. Service name kết quả: `redis-master.redis`.

```bash
helm install redis \
  --set auth.password=redis \
  oci://registry-1.docker.io/bitnamicharts/redis \
  -n redis --create-namespace
```

### 4.4 Elasticsearch

Dùng Elastic ECK operator.

```bash
helm upgrade --install elastic-operator elastic/eck-operator \
  --create-namespace --namespace elasticsearch

kubectl wait --for=condition=Established \
  crd/elasticsearches.elasticsearch.k8s.elastic.co --timeout=120s

helm upgrade --install elasticsearch-cluster \
  ./elasticsearch/elasticsearch-cluster \
  --create-namespace --namespace elasticsearch \
  --set elasticsearch.replicas=1
```

### 4.5 Keycloak

```bash
kubectl create namespace keycloak --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/26.0.2/kubernetes/keycloaks.k8s.keycloak.org-v1.yml
kubectl apply -f https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/26.0.2/kubernetes/keycloakrealmimports.k8s.keycloak.org-v1.yml
kubectl apply -f https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/26.0.2/kubernetes/kubernetes.yml -n keycloak

helm upgrade --install keycloak ./keycloak/keycloak \
  --namespace keycloak \
  --set hostname=identity.yas.local.com \
  --set postgresql.username=yasadminuser \
  --set postgresql.password=admin \
  --set bootstrapAdmin.username=admin \
  --set bootstrapAdmin.password=admin \
  --set backofficeRedirectUrl=http://backoffice.yas.local.com \
  --set storefrontRedirectUrl=http://storefront.yas.local.com
```

---

## 5. Credentials và cấu hình quan trọng

| Thứ | Giá trị |
|---|---|
| Docker Hub username | `thu2005` |
| PostgreSQL username | `yasadminuser` |
| PostgreSQL password | `admin` |
| Elasticsearch username | `yas` |
| Elasticsearch password | `LarUmB3A49NTg9YmgW4=` |
| Redis password | `redis` |
| Keycloak admin username | `admin` |
| Keycloak admin password | `admin` |
| GCP project | `yas-devops-project2` |
| GKE cluster | `yas-gke` |
| GKE zone | `us-east1-b` |
| ArgoCD namespace | `argocd` |
| YAS dev namespace | `yas-dev` |
| YAS staging namespace | `yas-staging` |
