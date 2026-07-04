# Đồ án 2 - Roadmap nhóm

Tài liệu này để cả nhóm theo dõi công việc. Nội dung tập trung vào kế hoạch, trạng thái, và evidence cần chụp cho báo cáo.

## 1. Kiến trúc đã chốt

Hướng làm chính: **GitOps-first với Argo CD trên GKE**.

```text
yas/ (source repo — github.com/thu2005/yas)
 └─ Jenkins CI (Multibranch Pipeline — máy local)
     → phát hiện service thay đổi
     → Maven build + Docker build
     → push image lên Docker Hub  (tag = 8-char commit SHA)

gitops-yas/ (GitOps repo — github.com/thu2005/gitops-yas)
 └─ Jenkins developer_build (máy local)
     → developer nhập branch muốn test cho từng service
     → resolve branch → commit SHA → image tag
     → cập nhật helm/yas/values-dev.yaml
     → commit + push gitops-yas (bởi jenkins-bot)

GKE cluster (GCP project: yas-devops-project2, zone: us-east1-b)
 └─ Argo CD (namespace: argocd)
     → yas-dev    theo gitops-yas/main    → namespace yas-dev
     → yas-staging theo gitops-yas/staging → namespace yas-staging
```

## 2. Phạm vi yêu cầu

### Bắt buộc (6đ)

- [x] CI: branch developer build image theo commit id và push Docker Hub.
- [x] Jenkins job `developer_build` đã chạy được.
- [x] Developer nhập branch theo từng service (param `TAX_SERVICE_BRANCH`...).
- [x] GitOps repo được cập nhật image tag tương ứng (jenkins-bot commit xác nhận).
- [x] Kubernetes cluster (GKE) đang chạy.
- [x] Argo CD sync `yas-dev` — phần lớn pods Running.
- [x] Deploy Redis, Elasticsearch, Keycloak thành công.
- [x] Fix backoffice-bff và storefront-bff (ExternalName service `identity`).
- [x] search pod Running (ES env vars trong values.yaml, Argo CD sync).
- [x] 14/14 pods Running ổn định.
- [x] Truy cập web qua Istio Gateway với domain `*.yas.local`.
- [x] Storefront checkout flow tạo order thành công.
- [x] Backoffice load được dashboard và order mới sau checkout.
- [ ] Payment PayPal capture success: chưa demo được do PayPal sandbox yêu cầu OTP số điện thoại.
- [ ] In URL NodePort cho developer test (không ưu tiên nữa vì đang dùng Istio Gateway domain).
- [x] Tạo và test Jenkins cleanup job.
- [ ] Có evidence đầy đủ cho báo cáo.

### Nâng cao (2đ — Argo CD dev/staging)

- [x] Argo CD app `yas-dev` tồn tại và sync.
- [x] Argo CD app `yas-staging` sync ổn định.
- [x] Flow release: merge `gitops-yas/main` → `staging` → Argo CD tự sync.

### Nâng cao (2đ — Service Mesh)

- [x] Istio mTLS (PeerAuthentication STRICT).
- [x] Istio AuthorizationPolicy cho ingressgateway truy cập storefront/backoffice/swagger UI.
- [x] Istio Gateway + VirtualService cho storefront/backoffice/swagger qua `*.yas.local`.
- [x] Istio retry/timeout (VirtualService).
- [ ] Kiali topology screenshot.
- [ ] Test plan và log curl allow/deny/retry.

## 3. Trạng thái hiện tại

### Infrastructure trên GKE (namespace riêng)

| Namespace     | Service          | Status         |
|---------------|------------------|----------------|
| postgres      | postgresql-0     | ✅ Running     |
| kafka         | kafka-cluster    | ✅ Running     |
| redis         | redis-master     | ✅ Running     |
| elasticsearch | elasticsearch    | ✅ Running     |
| keycloak      | keycloak         | ✅ Running     |
| argocd        | argocd-server    | ✅ Running     |

### YAS application (namespace yas-dev)

| Pod             | Status           | Ghi chú                                            |
|-----------------|------------------|----------------------------------------------------|
| storefront-ui   | ✅ Running       |                                                    |
| backoffice-ui   | ✅ Running       |                                                    |
| swagger-ui      | ✅ Running       |                                                    |
| sampledata      | ✅ Running       |                                                    |
| cart            | ✅ Running       | Đã fix lỗi 403 AuthorizationPolicy khi thêm vào giỏ hàng           |
| order           | ✅ Running       | Đã fix migration `order.customer_id` để checkout tạo order với Keycloak UUID |
| product         | ✅ Running       |                                                    |
| customer        | ✅ Running       |                                                    |
| inventory       | ✅ Running       |                                                    |
| media           | ✅ Running       | Đã fix lỗi 403 AuthorizationPolicy để tải hình ảnh                  |
| tax             | ✅ Running       | Đã fix mTLS & Retry policy (3 lần) thành công                      |
| payment         | ✅ Running       | Đã deploy service payment, fix Liquibase seed `enabled`, API payment providers OK |
| storefront-bff  | ✅ Running       | Fix: ExternalName svc `identity` + Keycloak hostname=identity      |
| backoffice-bff  | ✅ Running       | Fix: ExternalName svc `identity` + Keycloak hostname=identity      |
| search          | ✅ Running       | Đã rollback Kafka/Strimzi theo sample, Debezium Ready, search/filter bằng keyword thật chạy được |

### Repos và jobs

- `thu2005/yas` — CI pipeline chạy được, images push DockerHub.
- `thu2005/gitops-yas` — GitOps values chuẩn, jenkins-bot commit xác nhận.
- Jenkins job `developer_build` — chạy thành công.
- Jenkins job `developer_build_cleanup` — chạy được.
- Argo CD `yas-dev` — Synced + Healthy.
- Argo CD `yas-staging` — Synced + Healthy.

### Web demo status

Đã test được các phần sau trên web:

- Storefront mở được qua `http://storefront.yas.local`.
- Backoffice mở được qua `http://backoffice.yas.local`.
- Register/login customer mới trên storefront.
- Search/filter product chạy được.
- Add to cart chạy được.
- Checkout chọn địa chỉ chạy được.
- `PROCESS TO PAYMENT` tạo order thành công, order được ghi vào DB và backoffice thấy order mới.

Trạng thái DB sau khi test checkout:

```text
order DB:
- Có order mới, ví dụ email thu@gmail.com
- status = ACCEPTED
- payment_status = PENDING

payment DB:
- Chưa có payment record vì chưa capture PayPal thành công
```

Ghi chú quan trọng về payment:

- Nếu chọn `COD`, code gốc/sample đang cố ý báo `COD payment feature is under construction`, nên COD không thể success.
- Nếu chọn `PAYPAL`, app redirect sang PayPal sandbox đúng hướng, nhưng tài khoản test hiện bị PayPal yêu cầu OTP qua số điện thoại. Vì không nhận OTP thật được nên chưa hoàn tất capture, chưa chuyển `payment_status` sang `COMPLETED`.
- Đây là giới hạn tài khoản PayPal sandbox/demo, không phải lỗi CD/GitOps hay order service.

Các image custom đang dùng cho checkout/payment:

```text
thu2005/yas-payment:6fb564e1-payment
thu2005/yas-order:6fb564e1-order-customer-varchar
```

Source repo `yas` có commit local cần push để source khớp image:

```text
34550955 fix: align checkout database migrations
```

GitOps repo đã push commit deploy image order mới:

```text
209845f fix: deploy order image with customer id migration
```

## 4. Việc còn lại

File mô tả đề nằm ở `project2.md`. Nhóm đang chia scope như sau:

- **Phần mình phụ trách:** 8 điểm gồm CD bắt buộc 6đ + Argo CD dev/staging nâng cao 2đ.
- **Phần teammate phụ trách:** service mesh và observability, gồm Istio, Kiali, Grafana, Prometheus.

### Phần mình — CD + Argo CD 8đ

Các chức năng chính đã chạy được. Việc còn lại chủ yếu là chụp evidence, chuẩn hóa báo cáo và đảm bảo source/GitOps đã push đầy đủ.

**A. Source/Git cần hoàn tất**

- [ ] Push branch source `yas/dev_tax_service_test` lên GitHub.
- [ ] Đảm bảo các commit source sau đã có trên remote:
  - `34550955 fix: align checkout database migrations`
  - `9c9aae6f update docs`
  - `af2f660c fix: restore kafka debezium deployment`
- [ ] Kiểm tra repo `gitops-yas/main` đã có commit:
  - `209845f fix: deploy order image with customer id migration`
- [ ] Không commit các file local/sample nếu không cần cho báo cáo:
  - `.codex/`
  - `Jenkinsfile*_sample`
  - `Jenkinsfile_old`
  - `project1.md`, `project2.md` nếu đây chỉ là file đề/local note

**B. Evidence CI: branch build image theo commit id**

- [ ] Chụp Jenkins Multibranch/CI job build branch developer, ví dụ `dev_tax_service_test`.
- [ ] Chụp log CI thể hiện service đổi được build Maven/Docker.
- [ ] Chụp DockerHub có image tag theo commit id, ví dụ:
  - `thu2005/yas-tax:<commit>`
  - `thu2005/yas-order:6fb564e1-order-customer-varchar`
  - `thu2005/yas-payment:6fb564e1-payment`
- [ ] Ghi vào báo cáo: tag image dùng commit id 8 ký tự của branch; service không đổi dùng `main` hoặc `latest`.

**C. Evidence CD: Jenkins `developer_build`**

- [ ] Chụp trang `developer_build` có parameters theo từng service branch.
- [ ] Chụp build log `developer_build` thành công.
- [ ] Chụp log thể hiện Jenkins resolve branch → commit SHA → update GitOps values.
- [ ] Chụp commit `jenkins-bot` hoặc commit GitOps do job tạo trong `gitops-yas`.
- [ ] Chụp `gitops-yas/helm/yas/values-dev.yaml` sau CD, có tag image của service cần test.
- [ ] Chụp Argo CD `yas-dev` tự sync sau khi GitOps repo đổi.

**D. Evidence cleanup job**

- [ ] Chụp Jenkins job `developer_build_cleanup`.
- [ ] Chụp Build with Parameters, ví dụ:
  - `RESET_TAX=true`
  - `DRY_RUN=false`
  - `CONFIRM=true`
- [ ] Chụp cleanup log thành công.
- [ ] Chụp `values-dev.yaml` sau cleanup, service được reset về `main` hoặc tag default.
- [ ] Chụp Argo CD sync lại sau cleanup.

**E. Evidence Argo CD dev/staging 2đ**

- [ ] Chụp Argo CD UI có 2 app:
  - `yas-dev`
  - `yas-staging`
- [ ] Chụp CLI:
  ```bash
  kubectl get applications -n argocd
  ```
  Kỳ vọng: `yas-dev` và `yas-staging` đều `Synced` + `Healthy`.
- [ ] Chụp `kubectl get pods -n yas-dev` tất cả Running.
- [ ] Chụp `kubectl get pods -n yas-staging` tất cả Running.
- [ ] Chụp hoặc ghi log flow release:
  ```bash
  cd ~/project2/gitops-yas
  git checkout staging
  git merge main
  git push origin staging
  kubectl get applications -n argocd
  ```
- [ ] Trong báo cáo giải thích: Argo CD thay Jenkins deploy trực tiếp; Jenkins chỉ update GitOps repo, Argo CD reconcile cluster.

**F. Evidence truy cập web/demo YAS**

Trạng thái: đã test được qua Istio Gateway.

File `hosts` đang dùng:
```text
35.190.132.23  identity.yas.local
35.190.132.23  storefront.yas.local
35.190.132.23  backoffice.yas.local
35.190.132.23  swagger.yas.local
```

URL demo:
- `http://storefront.yas.local` → storefront
- `http://backoffice.yas.local` → backoffice
- `http://swagger.yas.local` → swagger-ui

Evidence cần chụp:

- [ ] Storefront mở được.
- [ ] Backoffice mở được.
- [ ] Search/filter product chạy được.
- [ ] Add to cart chạy được.
- [ ] Checkout chọn địa chỉ chạy được.
- [ ] `PROCESS TO PAYMENT` tạo order thành công.
- [ ] Backoffice latest orders có order mới.
- [ ] Ghi chú payment: PayPal sandbox yêu cầu OTP nên chưa capture success; order vẫn tạo thành công với `payment_status=PENDING`.

**G. Evidence DB/API khi cần chứng minh checkout**

Nếu cần chứng minh order đã tạo, dùng:

```bash
kubectl exec -n postgres postgresql-0 -- bash -lc \
  'PGPASSWORD="$PGPASSWORD_SUPERUSER" psql -U postgres -d order -c "select id,email,status,payment_status,checkout_id,created_on from \"order\" order by id desc limit 5;"'
```

Kỳ vọng:

```text
status = ACCEPTED
payment_status = PENDING
```

Ghi chú báo cáo:

- `COD` trong code gốc/sample đang `under construction`, nên không dùng để demo payment success.
- `PAYPAL` redirect đúng hướng, nhưng sandbox account hiện bị OTP số điện thoại, nên chưa capture được payment record.
- Đây không phải lỗi của CD/GitOps.

### Phần teammate — Service Mesh + Observability

Teammate phụ trách phần nâng cao service mesh và observability. Phần này tách khỏi 8 điểm CD + Argo CD của mình.

**A. Istio / Service Mesh**

- [ ] Xác nhận namespace YAS đã bật sidecar injection và pod chạy `2/2`.
- [ ] Chụp:
  ```bash
  kubectl get pods -n yas-dev
  kubectl get peerauthentication -n yas-dev
  kubectl get authorizationpolicy -n yas-dev
  kubectl get virtualservice -n yas-dev
  kubectl get destinationrule -n yas-dev
  ```
- [ ] Evidence mTLS STRICT:
  - YAML `PeerAuthentication`
  - screenshot pod `2/2`
  - nếu có Kiali, bật security badge/traffic mTLS
- [ ] Evidence AuthorizationPolicy:
  - curl từ pod được phép → HTTP 200
  - curl từ pod không được phép → RBAC denied / 403
- [ ] Evidence retry/timeout:
  - VirtualService có retry policy cho `tax`/`product`
  - log hoặc test chứng minh retry xảy ra khi service lỗi tạm thời
- [ ] Chuẩn bị phần giải thích flow service-to-service: storefront-ui → storefront-bff → product/cart/order/payment/customer/location/media/search.

**B. Kiali**

- [ ] Cài Kiali nếu chưa có.
- [ ] Chụp topology YAS namespace.
- [ ] Chụp traffic graph khi thao tác web:
  - search product
  - add to cart
  - checkout tạo order
- [ ] Chụp edge traffic có mTLS/healthy.
- [ ] Viết mô tả ngắn cho báo cáo: Kiali dùng để quan sát service topology và traffic trong mesh.

**C. Prometheus / Grafana**

Đề chính CD không bắt buộc observability, nhưng teammate có scope observability nên cần evidence riêng.

- [ ] Xác nhận Prometheus đang scrape metrics:
  ```bash
  kubectl get pods -A | grep -E 'prometheus|grafana'
  kubectl get servicemonitor -A
  ```
- [ ] Chụp Prometheus targets hoặc ServiceMonitor liên quan YAS.
- [ ] Chụp Grafana dashboard:
  - cluster/pod health
  - request rate/latency nếu có
  - JVM/Spring metrics nếu có
- [ ] Ghi rõ nếu metrics nào chưa hoàn chỉnh để tránh demo quá phạm vi.

**D. Deliverables teammate cần nộp vào docs/**

- [ ] YAML/manifests chính cho Istio hoặc link file trong `gitops-yas`.
- [ ] Screenshot Kiali topology.
- [ ] Test plan curl allow/deny/retry.
- [ ] Log kết quả test.
- [ ] Screenshot Grafana/Prometheus.
- [ ] Một đoạn giải thích ngắn: mTLS, AuthorizationPolicy, retry policy, observability dashboard.

## 5. Phân công

| Việc | Người |
|---|---|
| CI pipeline (Jenkins) | ✅ Xong |
| CD developer_build (Jenkins + GitOps) | ✅ Xong |
| GKE + Argo CD | ✅ Xong |
| Infrastructure (PG, Kafka, Redis, ES, Keycloak) | ✅ Xong |
| 14/14 YAS pods Running | ✅ Xong |
| Truy cập web qua Istio Gateway | ✅ Xong |
| Test cleanup job | ✅ Xong |
| Test staging flow | ✅ Xong |
| Chụp evidence CD + Argo CD 8đ | Bạn làm |
| Viết phần báo cáo CD + Argo CD | Bạn làm |
| Istio Service Mesh (mTLS, AuthPolicy, VirtualService retry) | Teammate làm |
| Kiali topology + curl allow/deny/retry evidence | Teammate làm |
| Observability Prometheus/Grafana evidence | Teammate làm |

## 6. Quy tắc

- Mọi repo/image dùng namespace DockerHub: `thu2005`.
- Jenkins chạy trên máy local, workload YAS/Argo chạy trên GKE.
- Không để GKE cluster chạy qua đêm không cần thiết (tốn GCP credit).
- `docs/` chỉ ghi roadmap, không ghi log debug chi tiết.

## 7. Fix Flow Order & Search

**Nguyên nhân lỗi:** Storefront search không ra Product do luồng đồng bộ Product -> Kafka/Debezium -> Elasticsearch bị đứt. Trước đó Kafka chart đã bị migrate sang Strimzi mới (`v1`, KRaft/Kafka 4.3.0) và tắt Debezium mặc định, trong khi repo gốc/sample dùng Kafka + ZooKeeper, `apiVersion: kafka.strimzi.io/v1beta2`, và image Debezium gốc.

**Quyết định:** Không build custom Debezium image nữa. Đưa Kafka chart về giống repo gốc/sample để giảm thay đổi business/infra không cần thiết.

Các file Kafka chart đã được revert về gốc/sample:

- `k8s/deploy/kafka/kafka-cluster/templates/kafka-cluster.yaml`
- `k8s/deploy/kafka/kafka-cluster/templates/debezium-connect-cluster.yaml`
- `k8s/deploy/kafka/kafka-cluster/templates/debezium-connector-postgresql-product-db.yaml`
- `k8s/deploy/kafka/kafka-cluster/values.yaml`

Strimzi version đã chốt cho GKE:

```text
Strimzi operator: quay.io/strimzi/operator:0.45.2
Kafka chart API: kafka.strimzi.io/v1beta2
Kafka metadata state: ZooKeeper
Debezium image: ghcr.io/nashtech-garage/debezium-connect-postgresql:latest
```

Lý do dùng `0.45.2`:

- `0.44.0` support chart gốc nhưng crash trên GKE hiện tại do Kubernetes API trả field `emulationMajor`.
- `0.51.0` chạy được trên GKE nhưng không còn support Kafka + ZooKeeper, yêu cầu KafkaNodePool/KRaft.
- `0.45.2` là bản cao nhất trước khi Strimzi bỏ ZooKeeper, chạy được trên GKE và vẫn support chart gốc/sample.

Trạng thái sau khi rollback:

```text
namespace kafka:
- strimzi-cluster-operator: 1/1 Running
- kafka-cluster-zookeeper-0: 1/1 Running
- kafka-cluster-kafka-0: 1/1 Running
- kafka-cluster-entity-operator: 2/2 Running
- debezium-connect-cluster-connect-0: 1/1 Running
- kafkaconnector debezium-connector-postgresql-product-db: Ready=True

Elasticsearch:
- index product co 14 documents
- search API voi keyword that tra ve product list
```

Ghi chú demo: endpoint search gốc khi `keyword` rỗng trả list rỗng vì code gốc/sample dùng `multi_match` với keyword rỗng. Khi test bằng keyword thật, ví dụ `iPhone`, API trả product list bình thường.
