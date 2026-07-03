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
- [ ] In URL NodePort cho developer test (cần GKE node external IP).
- [ ] Tạo và test Jenkins cleanup job.
- [ ] Có evidence đầy đủ cho báo cáo.

### Nâng cao (2đ — Argo CD dev/staging)

- [x] Argo CD app `yas-dev` tồn tại và sync.
- [ ] Argo CD app `yas-staging` sync ổn định.
- [ ] Flow release: merge `gitops-yas/main` → `staging` → Argo CD tự sync.

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
| order           | ✅ Running       | Đã fix lỗi code (Jackson `ProductCheckoutListVm`) ở máy local. Chờ push code để tự động build lại |
| product         | ✅ Running       |                                                    |
| customer        | ✅ Running       |                                                    |
| inventory       | ✅ Running       |                                                    |
| media           | ✅ Running       | Đã fix lỗi 403 AuthorizationPolicy để tải hình ảnh                  |
| tax             | ✅ Running       | Đã fix mTLS & Retry policy (3 lần) thành công                      |
| storefront-bff  | ✅ Running       | Fix: ExternalName svc `identity` + Keycloak hostname=identity      |
| backoffice-bff  | ✅ Running       | Fix: ExternalName svc `identity` + Keycloak hostname=identity      |
| search          | ✅ Running       | Lưu ý: Trống dữ liệu do Kafka Debezium CrashLoopBackOff (sai Image). Service vẫn chạy bình thường. |

### Repos và jobs

- `thu2005/yas` — CI pipeline chạy được, images push DockerHub.
- `thu2005/gitops-yas` — GitOps values chuẩn, jenkins-bot commit xác nhận.
- Jenkins job `developer_build` — chạy thành công.
- Jenkins job `developer_build_cleanup` — Jenkinsfile sẵn sàng, chưa test.

## 4. Việc còn lại

### Mình tự làm (theo thứ tự)

**1. Lấy IP node GKE và test truy cập YAS từ browser**

```bash
kubectl get nodes -o wide
# lấy cột EXTERNAL-IP
```

Thêm vào file `hosts` của máy (Windows: `C:\Windows\System32\drivers\etc\hosts`):
```text
<external-ip>  identity.yas.local
<external-ip>  storefront.yas.local
<external-ip>  backoffice.yas.local
<external-ip>  swagger.yas.local
```

Truy cập thử qua Istio IngressGateway (cần port HTTP của gateway, ví dụ 80 hoặc NodePort tương ứng):
- `http://storefront.yas.local` (hoặc kèm port) → storefront
- `http://backoffice.yas.local` (hoặc kèm port) → backoffice
- `http://swagger.yas.local` (hoặc kèm port) → swagger-ui

**2. Test Jenkins cleanup job**

Vào Jenkins → `developer_build_cleanup` → Build with Parameters:
- `RESET_TAX = true`, `DRY_RUN = false`, `CONFIRM = true`

Kiểm tra: `gitops-yas/helm/yas/values-dev.yaml` → `tax.image.tag` phải về `main`.

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

**4. Chụp evidence cho báo cáo**

Xem checklist đầy đủ ở `docs/demo-guide.md`. Những screenshot còn thiếu:
- `kubectl get pods -n yas-dev` — tất cả Running
- `kubectl get applications -n argocd` — Synced + Healthy
- Truy cập được storefront/backoffice trên browser
- `kubectl describe pod tax-... | grep Image` — đúng tag `2094d996`

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
| Test NodePort, cleanup job, staging flow | Bạn đang làm |
| Chụp evidence báo cáo | Bạn đang làm |
| Istio Service Mesh (AuthPolicy, VirtualService retry) | ✅ Xong |
| Quay video/chụp Kiali + test curl Istio | Giao bạn khác |

## 6. Quy tắc

- Mọi repo/image dùng namespace DockerHub: `thu2005`.
- Jenkins chạy trên máy local, workload YAS/Argo chạy trên GKE.
- Không để GKE cluster chạy qua đêm không cần thiết (tốn GCP credit).
- `docs/` chỉ ghi roadmap, không ghi log debug chi tiết.

## 7. Fix Flow Order & Search

**Nguyên nhân lỗi:** Backoffice không hiển thị Order và Storefront tìm kiếm không ra Product do luồng đồng bộ sự kiện (Kafka/Debezium) bị đứt gãy. Pod `debezium-connect` chết (CrashLoopBackOff) do image `debezium/connect:2.7.3.Final` không tương thích với script khởi động của Strimzi Kafka Operator.

**Cách xử lý:** Cần build một image Debezium mới tương thích với Strimzi và cập nhật vào cấu hình GitOps.

**Bước 1: Tạo Dockerfile chuẩn Strimzi**
Tại thư mục `yas/debezium` (tạo mới), tạo `Dockerfile`:
```dockerfile
FROM quay.io/strimzi/kafka:0.39.0-kafka-3.6.0
USER root:root
RUN mkdir -p /opt/kafka/plugins/debezium
RUN curl -L -o /tmp/debezium.tar.gz https://repo1.maven.org/maven2/io/debezium/debezium-connector-postgres/2.7.3.Final/debezium-connector-postgres-2.7.3.Final-plugin.tar.gz
RUN tar -xzf /tmp/debezium.tar.gz -C /opt/kafka/plugins/debezium --strip-components=1
RUN rm /tmp/debezium.tar.gz
USER 1001
```

**Bước 2: Build và Push Image lên Docker Hub**
```bash
docker build -t thu2005/yas-debezium-connect:latest ./debezium
docker push thu2005/yas-debezium-connect:latest
```

**Bước 3: Cập nhật cấu hình GitOps**
Trong repo `gitops-yas`, sửa `helm/yas/values-dev.yaml`:
```yaml
debeziumConnect:
  enabled: true
  image: "thu2005/yas-debezium-connect:latest"
```
Commit và push lên nhánh `main`. Argo CD sẽ tự động cập nhật, pod Debezium sẽ khởi động thành công và luồng đồng bộ dữ liệu Kafka sẽ hoạt động trở lại.
