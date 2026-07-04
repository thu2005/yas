# Demo Guide — Đồ án 2 CD/GitOps

Hướng dẫn cách demo từng phần, output mong muốn sau mỗi bước, và giải thích luồng để cả nhóm nắm trước khi đứng demo.

---

## Tổng quan luồng

```
Developer push code lên branch feature (ví dụ: dev_tax_service_test)
        │
        ▼
Jenkins CI  (chạy trên máy local, địa chỉ: http://localhost:8081)
  ├─ Detect Changes: tìm service nào thay đổi
  ├─ Maven build JAR
  ├─ Docker build image
  └─ Docker push → Docker Hub   tag = 8 ký tự đầu commit SHA
        │
        ▼
Docker Hub: thu2005/yas-tax:2094d996
        │
        ▼
Developer chạy Jenkins job "developer_build"
  ├─ Nhập branch muốn test: TAX_SERVICE_BRANCH=dev_tax_service_test
  ├─ Resolve branch → lấy commit SHA từ GitHub (git ls-remote)
  ├─ Cập nhật gitops-yas/helm/yas/values-dev.yaml  (tax.image.tag=2094d996)
  └─ Commit + push lên github.com/thu2005/gitops-yas  (bởi jenkins-bot)
        │
        ▼
GitHub: gitops-yas/main có commit mới
        │
        ▼
Argo CD (chạy trên GKE, namespace: argocd)
  ├─ Phát hiện values-dev.yaml thay đổi
  ├─ Chạy helm upgrade cho yas-dev
  └─ Cập nhật pod tax dùng image mới
        │
        ▼
GKE namespace yas-dev: pod tax chạy với thu2005/yas-tax:2094d996
Developer truy cập http://<node-ip>:30011 để test
```

---

## Phần 1 — CI: Build và Push Image

### Mục đích

Mỗi khi developer push code lên bất kỳ branch nào trong repo `yas`, Jenkins tự động build Docker image và push lên Docker Hub. Mỗi commit có 1 image riêng với tag là commit SHA — đảm bảo có thể rollback về bất kỳ phiên bản nào.

### Cách chạy khi demo

**Kịch bản:** Developer sửa code trong service `tax` ở branch `dev_tax_service_test`.

1. Push code lên branch (hoặc đã push trước):
   ```bash
   git push origin dev_tax_service_test
   ```

2. Jenkins tự trigger. Hoặc vào Jenkins UI → `YAS-Microservices-CI` → chọn branch `dev_tax_service_test` → **Build with Parameters**:
   - `BUILD_ALL` = `false` (chỉ build service thay đổi)
   - `RUN_FEATURE_BRANCH_TESTS` = `false`

### Output mong muốn

**Jenkins build log:**
```
Affected Maven modules: tax
Affected Docker services: tax
[INFO] BUILD SUCCESS
Building thu2005/yas-tax:2094d996
docker push thu2005/yas-tax:2094d996
Pipeline SUCCESS
```

**Docker Hub** (`hub.docker.com/u/thu2005`):  
Image `thu2005/yas-tax` có tag `2094d996`.

### Giải thích luồng

- Jenkinsfile (`yas/Jenkinsfile`) chạy stage `Detect Changes`: so sánh với commit trước, tìm file thay đổi → suy ra service nào cần rebuild.
- Stage `Build`: chỉ Maven build service bị ảnh hưởng, không build lại toàn bộ.
- Stage `Build and Push Docker Images`: chỉ push cho **branch không phải `main`**. Image tag = `git rev-parse --short=8 HEAD`.
- Branch `main` dùng tag cố định `main` và không push theo commit.

---

## Phần 2 — CD: Developer Build Job

### Mục đích

Developer muốn test code trên môi trường `dev` (GKE). Job này:
1. Lấy commit SHA của branch đang làm.
2. Ghi vào file GitOps: "service tax dùng image tag 2094d996".
3. Argo CD tự phát hiện và deploy image mới lên GKE — không cần người thao tác thêm.

### Cách chạy khi demo

1. Vào Jenkins → job `developer_build` → **Build with Parameters**.

2. Điền params:

   | Param | Giá trị | Ý nghĩa |
   |---|---|---|
   | `TARGET_ENV` | `dev` | Deploy vào namespace `yas-dev` |
   | `TAX_SERVICE_BRANCH` | `dev_tax_service_test` | Branch tax đang test |
   | (các service còn lại) | `main` | Dùng image stable |
   | `DRY_RUN` | `false` | Thực sự push GitOps commit |

3. Bấm **Build**.

### Output mong muốn

**Jenkins console:**
```
Stage: Resolve branches to image tags
  tax-service   dev_tax_service_test   2094d996

Stage: Update GitOps values
  Updating helm/yas/values-dev.yaml: tax.image.tag=2094d996
  diff:
  -    tag: main
  +    tag: 2094d996

Stage: Commit and push GitOps change
  developer_build: update dev image tags [build #N]
  git push origin HEAD:main

GitOps changes pushed. Argo CD will sync the affected application.
```

**GitHub `gitops-yas`:**  
Có commit mới từ `jenkins-bot`:
```
developer_build: update dev image tags [build #N]
```

Nội dung `helm/yas/values-dev.yaml` sau commit:
```yaml
tax:
  enabled: true
  image:
    tag: 2094d996    ← đổi từ main
```

### Giải thích luồng

- Script `jenkins/scripts/resolve-branch-tags.sh` gọi `git ls-remote https://github.com/thu2005/yas.git refs/heads/dev_tax_service_test` → lấy commit SHA, không cần checkout code.
- Script `jenkins/scripts/update-values.sh` dùng `awk` để thay đúng dòng `tag:` trong `values-dev.yaml`.
- `DRY_RUN=true` chỉ in diff, không commit. Dùng để preview trước.
- Sau khi push, Argo CD phát hiện thay đổi trong `gitops-yas` và tự sync.

---

## Phần 3 — Argo CD Sync

### Mục đích

Argo CD theo dõi repo `gitops-yas` liên tục. Khi `values-dev.yaml` thay đổi (do `developer_build` push), Argo CD phát hiện và chạy `helm upgrade` tự động — không cần ai thao tác trên cluster.

### Kiểm tra khi demo

```bash
# Xem trạng thái apps
kubectl get applications -n argocd

# Xem pods trong yas-dev
kubectl get pods -n yas-dev

# Xem image tag đang chạy của pod tax
kubectl describe pod -n yas-dev -l app=tax | grep Image
```

### Output mong muốn

```
NAME          SYNC STATUS   HEALTH STATUS
yas-dev       Synced        Healthy
yas-staging   Synced        Healthy
```

```
NAME                              READY   STATUS    RESTARTS   AGE
tax-<hash>                        1/1     Running   0          30s
...
```

```
Image: thu2005/yas-tax:2094d996
```

### Lấy URL để test

```bash
# Lấy external IP của GKE node
kubectl get nodes -o wide

# Các NodePort đã cấu hình:
# storefront-ui    → http://<node-ip>:30001
# storefront-bff   → http://<node-ip>:30002
# backoffice-ui    → http://<node-ip>:30003
# backoffice-bff   → http://<node-ip>:30004
# product          → http://<node-ip>:30005
# media            → http://<node-ip>:30006
# customer         → http://<node-ip>:30007
# cart             → http://<node-ip>:30008
# order            → http://<node-ip>:30009
# inventory        → http://<node-ip>:30010
# tax              → http://<node-ip>:30011
# search           → http://<node-ip>:30012
# sampledata       → http://<node-ip>:30013
# swagger-ui       → http://<node-ip>:30014
```

Thêm vào file `hosts` của máy dev:
```
<node-external-ip>  identity.yas.local.com
```

---

## Phần 4 — Cleanup Job

### Mục đích

Sau khi developer test xong, reset service về image `main` để môi trường dev trở về stable.

### Cách chạy

1. Jenkins → job `developer_build_cleanup` → **Build with Parameters**.
2. Tick `RESET_TAX = true` (chỉ reset service vừa test).
3. `DRY_RUN = false`, `CONFIRM = true`.
4. Bấm Build.

### Output mong muốn

- `values-dev.yaml`: `tax.image.tag` đổi lại thành `main`.
- Commit jenkins-bot: `cleanup: reset dev image tags [build #N]`.
- Argo CD sync, pod tax dùng image `thu2005/yas-tax:main`.

---

## Checklist evidence cần chụp cho báo cáo

### CI
- [x] Jenkins `YAS-Microservices-CI / dev_tax_service_test` — build SUCCESS.
- [x] Jenkins log thấy `docker push thu2005/yas-tax:2094d996`.
- [x] Docker Hub: image `thu2005/yas-tax:2094d996` tồn tại.

### CD developer_build
- [x] Jenkins `developer_build` — build SUCCESS.
- [x] Jenkins log thấy resolve `dev_tax_service_test → 2094d996`.
- [x] Jenkins log thấy `git push`.
- [x] GitHub `gitops-yas` — commit của jenkins-bot với diff `tag: main → 2094d996`.

### GKE + Argo CD
- [x] GKE cluster đang chạy (`kubectl get nodes`).
- [x] Argo CD apps tồn tại (`kubectl get applications -n argocd`).
- [ ] `yas-dev` Synced + Healthy (đang Progressing, chờ infra đủ).
- [ ] `kubectl get pods -n yas-dev` — tất cả Running.
- [ ] `kubectl describe pod ... | grep Image` — đúng image tag `2094d996`.
- [ ] Truy cập được YAS storefront qua NodePort.

### Cleanup
- [ ] Jenkins cleanup job — build SUCCESS.
- [ ] GitHub commit reset về `main`.
- [ ] Pod tax restart với image `main`.

### Istio/Kiali (nâng cao)
- [ ] Pods có 2 container (app + istio-proxy).
- [ ] Kiali topology screenshot.
- [ ] curl test: allow/deny/retry logs.
