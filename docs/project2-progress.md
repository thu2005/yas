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

### Việc còn lại / cần chụp evidence

**1. Truy cập YAS từ browser**

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

**2. Test Jenkins cleanup job**

Vào Jenkins → `developer_build_cleanup` → Build with Parameters:
- `RESET_TAX = true`, `DRY_RUN = false`, `CONFIRM = true`

Kiểm tra: `gitops-yas/helm/yas/values-dev.yaml` → `tax.image.tag` phải về `main`.

Trạng thái: đã chạy được. Khi cần evidence, chụp log Jenkins cleanup và diff/tag trong GitOps.

**3. Test staging flow**

```bash
cd ~/project2/gitops-yas
git checkout staging
git merge main
git push origin staging
```

Argo CD sẽ tự sync `yas-staging`. Kiểm tra:
```bash
kubectl get applications -n argocd
# yas-staging → Synced + Healthy
```

Trạng thái: đã test xong, `yas-staging` Synced + Healthy.

**4. Chụp evidence cho báo cáo**

Xem checklist đầy đủ ở `docs/demo-guide.md`. Những screenshot còn thiếu:
- `kubectl get pods -n yas-dev` — tất cả Running
- `kubectl get applications -n argocd` — Synced + Healthy
- Truy cập được storefront/backoffice trên browser
- `kubectl describe pod tax-... | grep Image` — đúng tag `2094d996`
- Storefront: search/filter, add to cart, checkout tạo order
- Backoffice: latest orders có order mới
- DockerHub: image `thu2005/yas-order:6fb564e1-order-customer-varchar`, `thu2005/yas-payment:6fb564e1-payment`
- Ghi chú payment: PayPal sandbox bị OTP nên chưa capture success

### Giao cho các bạn khác — Evidence Service Mesh (2đ nâng cao)

Phần Istio đã được cấu hình gần như 100% trong GitOps: mTLS STRICT, Gateway/VirtualService cho web UI, AuthorizationPolicy cho 14 backend services, VirtualService retry/timeout cho tax/product. Xem `docs/team-onboarding.md` để biết cách kết nối vào cluster.

Công việc cần làm để quay video/chụp ảnh báo cáo:
1. Cài Kiali, chụp topology screenshot
2. Test: curl allow/deny/retry, ghi log vào `docs/`

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
| Chụp evidence báo cáo | Bạn đang làm |
| Istio Service Mesh (AuthPolicy, VirtualService retry) | ✅ Xong |
| Quay video/chụp Kiali + test curl Istio | Giao bạn khác |

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
