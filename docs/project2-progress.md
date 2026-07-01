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

- [ ] Istio mTLS (PeerAuthentication STRICT).
- [ ] Istio AuthorizationPolicy.
- [ ] Istio retry/timeout (VirtualService).
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
| cart            | ✅ Running       |                                                    |
| order           | ✅ Running       |                                                    |
| product         | ✅ Running       |                                                    |
| customer        | ✅ Running       |                                                    |
| inventory       | ✅ Running       |                                                    |
| media           | ✅ Running       |                                                    |
| tax             | ✅ Running       | ← service đang demo                                |
| storefront-bff  | ✅ Running       | Fix: tạo ExternalName svc `identity` → Keycloak    |
| backoffice-bff  | ✅ Running       | Fix: tạo ExternalName svc `identity` → Keycloak    |
| search          | ✅ Running       | Fix: thêm ES env vars trong values.yaml             |

### Repos và jobs

- `thu2005/yas` — CI pipeline chạy được, images push DockerHub.
- `thu2005/gitops-yas` — GitOps values chuẩn, jenkins-bot commit xác nhận.
- Jenkins job `developer_build` — chạy thành công.
- Jenkins job `developer_build_cleanup` — Jenkinsfile sẵn sàng, chưa test.

## 4. Việc còn lại 

### Sau khi tất cả pods Running

1. Lấy GKE node external IP → ghi vào file `hosts` của máy dev để test.
2. Test Jenkins cleanup job.
3. Chụp evidence cho báo cáo (xem `demo-guide.md`).
4. Deploy staging, test flow merge main→staging.

### Nâng cao 

1. Cài Istio + Kiali.
2. Apply mTLS, AuthorizationPolicy, retry VirtualService.
3. Chụp Kiali topology.

## 6. Quy tắc

- Mọi repo/image dùng namespace DockerHub: `thu2005`.
- Jenkins chạy trên máy local, workload YAS/Argo chạy trên GKE.
- Không để GKE cluster chạy qua đêm không cần thiết (tốn GCP credit).
- `docs/` chỉ ghi roadmap, không ghi log debug chi tiết.
