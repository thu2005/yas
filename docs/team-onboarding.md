# Hướng dẫn cho thành viên nhóm

Tài liệu này giúp các bạn trong nhóm có thể kết nối vào hệ thống và làm tiếp các phần còn lại mà không cần hỏi lại từ đầu.

---

## Bạn cần có gì

### Công cụ cần cài trên máy

```bash
# 1. Google Cloud CLI
# Tải tại: https://cloud.google.com/sdk/docs/install

# 2. kubectl
gcloud components install kubectl

# 3. Helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# 4. Git (thường đã có)
```

### Quyền truy cập cần được cấp

Nhờ người đang quản lý GCP cấp cho email của bạn quyền vào project:

```
GCP Project: yas-devops-project2
Role cần: Kubernetes Engine Developer (hoặc Viewer để chỉ xem)
```

Cấp quyền bằng lệnh (người có quyền Owner chạy):
```bash
gcloud projects add-iam-policy-binding yas-devops-project2 \
  --member="user:email-cua-ban@gmail.com" \
  --role="roles/container.developer"
```

---

## Kết nối vào GKE cluster

Sau khi được cấp quyền, chạy lần đầu để lấy kubeconfig:

```bash
gcloud auth login
gcloud container clusters get-credentials yas-gke \
  --zone us-east1-b \
  --project yas-devops-project2
```

Kiểm tra:
```bash
kubectl get nodes
# Thấy 2 nodes → kết nối thành công
```

---

## Trạng thái hiện tại của cluster

### Xem nhanh tất cả

```bash
# Pods YAS
kubectl get pods -n yas-dev

# Pods infrastructure
kubectl get pods -n postgres
kubectl get pods -n kafka
kubectl get pods -n redis
kubectl get pods -n elasticsearch
kubectl get pods -n keycloak
kubectl get pods -n argocd

# Argo CD apps
kubectl get applications -n argocd
```

### Những gì đang chạy

- ✅ PostgreSQL (`postgres` namespace)
- ✅ Kafka (`kafka` namespace)
- ✅ Redis (`redis` namespace)
- ✅ Elasticsearch (`elasticsearch` namespace)
- ✅ Keycloak (`keycloak` namespace)
- ✅ Argo CD (`argocd` namespace)
- ✅ 13/14 YAS pods Running (`yas-dev` namespace)
- ⏳ `search` pod — chờ push `gitops-yas/helm/yas/values.yaml` (đã commit local) để Argo CD sync

---

## Việc cần làm tiếp

### Ưu tiên 1 — Push values.yaml để fix search pod

Commit đã có sẵn ở local. Người có SSH key cho repo `thu2005/gitops-yas` chạy:
```bash
cd ~/project2/gitops-yas
git push origin main
```

Sau đó Argo CD sẽ tự sync, pod `search` sẽ restart với đúng Elasticsearch URL.

Kiểm tra:
```bash
kubectl get pods -n yas-dev
# Kỳ vọng: tất cả 1/1 Running
```

### Ưu tiên 2 — Test truy cập YAS

Lấy external IP của node:
```bash
kubectl get nodes -o wide
# Cột EXTERNAL-IP
```

Thêm vào file `hosts` của máy (Windows: `C:\Windows\System32\drivers\etc\hosts`, Linux: `/etc/hosts`):
```
<external-ip>  identity.yas.local.com
<external-ip>  storefront.yas.local.com
<external-ip>  backoffice.yas.local.com
```

Truy cập:
```
http://<external-ip>:30001   → storefront UI
http://<external-ip>:30003   → backoffice UI
http://<external-ip>:30014   → swagger UI
```

### Ưu tiên 3 — Test cleanup job

Vào Jenkins (`http://localhost:8081`) → job `developer_build_cleanup`:
- Tick `RESET_TAX = true`
- `DRY_RUN = false`
- `CONFIRM = true`
- Bấm Build

Kiểm tra sau khi chạy:
```bash
# values-dev.yaml phải có tax.image.tag = main
cat ~/project2/gitops-yas/helm/yas/values-dev.yaml | grep -A3 "^tax:"
```

### Ưu tiên 4 — Test staging flow

```bash
cd ~/project2/gitops-yas
git checkout staging
git merge main
git push origin staging
```

Argo CD sẽ tự sync `yas-staging`:
```bash
kubectl get applications -n argocd
# yas-staging → Synced
```

### Ưu tiên 5 — Istio/Kiali (nâng cao)

Xem file `docs/demo-guide.md` phần Istio (chưa viết, cần bổ sung sau khi cài).

---

## Repos

| Repo | URL | Mục đích |
|---|---|---|
| Source code | `github.com/thu2005/yas` | Code Java + Jenkinsfile CI |
| GitOps | `github.com/thu2005/gitops-yas` | Helm chart + Argo CD apps + Jenkinsfiles CD |

---

## Jenkins

**URL:** `http://localhost:8081` (chạy trên máy của người setup, không public)

Nếu bạn cần truy cập Jenkins từ xa, người đang chạy Jenkins cần expose port hoặc dùng ngrok/tunnel.

---

## Nếu gặp lỗi phổ biến

**Pod CrashLoopBackOff:**
```bash
kubectl logs -n yas-dev <pod-name> --previous | tail -30
```

**Argo CD OutOfSync mãi không sync:**
```bash
kubectl get application yas-dev -n argocd -o yaml | grep -A5 "conditions"
```

**Helm install fail:**
```bash
helm list -A           # xem release đang tồn tại
helm status <release> -n <namespace>   # xem chi tiết
```
