# Kế hoạch triển khai — Đồ án 2 DevOps (YAS GitOps + Service Mesh)

> **Bối cảnh**: Toàn bộ CI/CD, GKE cluster, 14/14 YAS pods, Argo CD đã được **một thành viên setup sẵn**.
> Tài liệu này là hướng dẫn cho **các thành viên tiếp theo** kết nối vào hệ thống đã có và hoàn thành các phần còn lại.
> Xem thêm `docs/team-onboarding.md` để biết cách onboard nhanh.

---

## 1. Tổng quan trạng thái dự án

### ✅ Đã hoàn thành (setup sẵn bởi nhóm — KHÔNG cần làm lại)

| Hạng mục | Chi tiết | File/Bằng chứng |
|---|---|---|
| **CI Pipeline** | Jenkins Multibranch phát hiện thay đổi → Maven build → Docker build → push Docker Hub (tag = 8-char commit SHA). Chỉ push khi branch ≠ `main`. | `yas/Jenkinsfile` |
| **CD `developer_build`** | Pipeline nhận branch input per-service → resolve SHA qua `git ls-remote` → cập nhật `values-dev.yaml` → jenkins-bot commit+push `gitops-yas`. Có `DRY_RUN` mode. | `gitops-yas/jenkins/Jenkinsfile.developer_build` |
| **CD `developer_build_cleanup`** | Pipeline reset từng service hoặc toàn bộ về `main`. Có `DRY_RUN` + `CONFIRM` bảo vệ. | `gitops-yas/jenkins/Jenkinsfile.cleanup` |
| **GitOps repo** | Helm chart đầy đủ cho 14 services, `values-dev.yaml`, `values-staging.yaml`, ArgoCD Project + 2 Application. | `gitops-yas/helm/yas/`, `gitops-yas/argocd/` |
| **GKE Cluster** | `yas-gke` tại `us-east1-b`, `e2-standard-4`, 2 nodes — **đang chạy**. | GCP project `yas-devops-project2` |
| **Argo CD** | App `yas-dev` — **Synced + Healthy**. App `yas-staging` — Application YAML đã tạo, chưa confirm trạng thái. | `gitops-yas/argocd/applications/` |
| **Infra K8S** | PostgreSQL, Kafka, Redis, Elasticsearch, Keycloak — tất cả **Running** trong namespace riêng. | `docs/project2-progress.md` §3 |
| **14/14 YAS Pods** | storefront-ui, backoffice-ui, swagger-ui, sampledata, cart, order, product, customer, inventory, media, tax, storefront-bff, backoffice-bff, search — tất cả **Running**. | `docs/project2-progress.md` §3 |
| **tax image SHA** | `tax.image.tag = 2094d996` trong `values-dev.yaml` → bằng chứng CI→CD→GitOps hoạt động thực tế. | `gitops-yas/helm/yas/values-dev.yaml` |
| **Istio Helm templates (skeleton)** | 3 file template đã tạo trong `gitops-yas/helm/yas/templates/istio/` nhưng **nội dung rỗng**. | `peer-authentication.yaml`, `authorization-policy.yaml`, `virtual-service.yaml` |
| **Gitleaks scan** | Tích hợp CI pipeline — đánh `unstable` (không block) nếu phát hiện secrets. | `yas/Jenkinsfile` stage `Gitleaks Scan` |

### ❌ Còn phải làm

| Hạng mục | Loại | Người thực hiện |
|---|---|---|
| Test URL NodePort + evidence báo cáo | **Bắt buộc** | Bạn đang làm |
| Jenkins cleanup job chạy thực tế + evidence | **Bắt buộc** | Bạn đang làm |
| Chụp đủ evidence cho báo cáo | **Bắt buộc** | Bạn đang làm |
| Argo CD `yas-staging` ổn định + staging flow | **Nâng cao ~2đ** | Bạn đang làm |
| **Cài Istio lên GKE** | **Nâng cao 2đ** | Giao bạn khác |
| **Điền Istio Helm templates** (mTLS, AuthzPolicy, VirtualService) | **Nâng cao 2đ** | Giao bạn khác |
| **Kiali + Prometheus + Jaeger** | **Nâng cao 2đ** | Giao bạn khác |
| Evidence: Kiali topology, curl allow/deny/retry | **Nâng cao 2đ** | Giao bạn khác |
| `docs/istio-setup.md` | **Nâng cao 2đ** | Giao bạn khác |

---

## 2. Thiết lập môi trường (làm trước tất cả)

> [!NOTE]
> Cluster **đã tồn tại và đang chạy** — không cần tạo mới. Chỉ cần cài tool và kết nối vào.

### 2.1 — Yêu cầu: được cấp quyền GCP

Nhờ người đang quản lý GCP chạy lệnh này để thêm email của bạn:

```bash
gcloud projects add-iam-policy-binding yas-devops-project2 \
  --member="user:email-cua-ban@gmail.com" \
  --role="roles/container.developer"
```

### 2.2 — Cài công cụ (một lần duy nhất)

Cần 4 công cụ: `gcloud`, `kubectl`, `helm`, `istioctl`.

---

#### A. Google Cloud CLI (`gcloud`)

**Windows:** Tải installer tại https://cloud.google.com/sdk/docs/install-sdk#windows → chạy installer → mở terminal mới

**Linux/Mac:**
```bash
curl https://sdk.cloud.google.com | bash
exec -l $SHELL
```

Kiểm tra:
```bash
gcloud version
# Google Cloud SDK 4xx.x.x
```

---

#### B. kubectl

```bash
# Cài qua gcloud (hoạt động trên cả Windows/Linux/Mac)
gcloud components install kubectl

# Kiểm tra
kubectl version --client
# Client Version: v1.xx.x
```

---

#### C. Helm

**Linux/Mac:**
```bash
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

**Windows (PowerShell as Admin):**
```powershell
winget install Helm.Helm
# Hoặc tải binary tại: https://github.com/helm/helm/releases
# Giải nén → thêm thư mục vào PATH
```

Kiểm tra:
```bash
helm version
# version.BuildInfo{Version:"v3.x.x"...}
```

Các lệnh Helm thường dùng để kiểm tra/debug:
```bash
helm list -A                                              # xem tất cả releases
helm status yas-dev -n yas-dev                            # trạng thái chi tiết
helm get values yas-dev -n yas-dev                        # values đang áp dụng
helm template yas-dev ./helm/yas -f values.yaml -f values-dev.yaml  # render template (debug)
```

---

#### D. istioctl (chỉ cần nếu làm phần Istio)

**Linux/Mac:**
```bash
curl -L https://istio.io/downloadIstio | sh -
# Chạy lệnh in ra sau khi tải xong để thêm vào PATH
# Ví dụ: export PATH=$HOME/istio-1.23.4/bin:$PATH
```

**Windows:** File `istio.zip` đã có sẵn trong `yas/istio.zip` (đã tải về trước):
```powershell
Expand-Archive -Path "d:\DEVOPS\yas\istio.zip" -DestinationPath "C:\istio" -Force
# Thêm vào System PATH:
# Control Panel → System → Environment Variables → Path → New → C:\istio\istio-1.23.4\bin
```

Kiểm tra:
```bash
istioctl version --remote=false
# Istio 1.23.x
```

---

### 2.3 — Kết nối vào GKE cluster

```bash
gcloud auth login

gcloud container clusters get-credentials yas-gke \
  --zone us-east1-b \
  --project yas-devops-project2
```

> [!WARNING]
> **Trên Windows PowerShell**, KHÔNG dùng `\` xuống dòng. Viết 1 dòng:
> `gcloud container clusters get-credentials yas-gke --zone us-east1-b --project yas-devops-project2`

### 2.4 — Xác nhận kết nối thành công

```bash
# Kiểm tra nodes
kubectl get nodes
# Thấy 2 nodes Ready → kết nối thành công

# Kiểm tra tổng quan hệ thống (chỉ xem, không sửa gì)
kubectl get namespaces
# Kỳ vọng: yas-dev, postgres, kafka, redis, elasticsearch, keycloak, argocd

kubectl get pods -n yas-dev
# Kỳ vọng: 14/14 pods Running

kubectl get applications -n argocd
# Kỳ vọng: yas-dev Synced + Healthy
```

> [!CAUTION]
> **Đừng tạo GKE cluster mới!** Cluster `yas-gke` đã có sẵn và đang chứa toàn bộ ứng dụng.

> [!NOTE]
> **Jenkins chạy trên máy local của người setup** (`http://localhost:8081`).
> Nếu bạn không ngồi cùng máy đó → nhờ người setup expose port hoặc dùng ngrok/tunnel.

---

## 3. Test NodePort URLs & Evidence (Bắt buộc)

### 3.1 — Lấy External IP của GKE node

```bash
kubectl get nodes -o wide
# Cột EXTERNAL-IP → ghi lại địa chỉ này (ví dụ: 34.xxx.xxx.xxx)
```

### 3.2 — Cấu hình hosts file

```
# Windows: C:\Windows\System32\drivers\etc\hosts (mở Notepad as Admin)
# Linux/Mac: /etc/hosts

<external-ip>  identity.yas.local.com
<external-ip>  storefront.yas.local.com
<external-ip>  backoffice.yas.local.com
```

### 3.3 — NodePort mapping (đã cấu hình trong `values.yaml`)

| Service | NodePort | URL để test |
|---|---|---|
| storefront-ui | 30001 | `http://<node-ip>:30001` |
| storefront-bff | 30002 | `http://<node-ip>:30002` |
| backoffice-ui | 30003 | `http://<node-ip>:30003` |
| backoffice-bff | 30004 | `http://<node-ip>:30004` |
| product | 30005 | `http://<node-ip>:30005` |
| media | 30006 | `http://<node-ip>:30006` |
| customer | 30007 | `http://<node-ip>:30007` |
| cart | 30008 | `http://<node-ip>:30008` |
| order | 30009 | `http://<node-ip>:30009` |
| inventory | 30010 | `http://<node-ip>:30010` |
| tax | 30011 | `http://<node-ip>:30011` |
| search | 30012 | `http://<node-ip>:30012` |
| sampledata | 30013 | `http://<node-ip>:30013` |
| swagger-ui | 30014 | `http://<node-ip>:30014` |

```bash
# Test nhanh qua curl
curl http://<node-ip>:30011/actuator/health

# Mở browser để chụp screenshot
# http://<node-ip>:30001  → storefront UI
# http://<node-ip>:30003  → backoffice UI
# http://<node-ip>:30014  → swagger UI

# Xác nhận đúng image tag đang chạy
kubectl describe pod -n yas-dev -l app=tax | grep Image
# Kỳ vọng: Image: thu2005/yas-tax:2094d996
```

---

## 4. Jenkins Cleanup Job Test (Bắt buộc)

> [!NOTE]
> `Jenkinsfile.cleanup` đã sẵn sàng trong `gitops-yas/jenkins/`. Chỉ cần chạy thực tế để lấy evidence.

### 4.1 — Cách test

Vào Jenkins (`http://localhost:8081`) → job `developer_build_cleanup` → **Build with Parameters**:

| Param | Lần 1 (Preview) | Lần 2 (Thực tế) |
|---|---|---|
| `TARGET_ENV` | `dev` | `dev` |
| `CLEANUP_MODE` | `SELECTIVE` | `SELECTIVE` |
| `RESET_TAX` | `true` | `true` |
| `DRY_RUN` | `true` | `false` |
| `CONFIRM` | `false` | `true` |

### 4.2 — Output mong muốn

```
Stage: Reset image tags
  diff: -    tag: 2094d996
        +    tag: main

Stage: Commit and push
  cleanup: reset dev image tags to main [build #N]
  git push origin HEAD:main
```

Kiểm tra sau khi chạy:
```bash
cat ~/project2/gitops-yas/helm/yas/values-dev.yaml | grep -A3 "^tax:"
# Kỳ vọng: tag: main
```

---

## 5. Argo CD Staging (Nâng cao ~2đ)

### 5.1 — Kiểm tra trạng thái hiện tại

```bash
kubectl get applications -n argocd
# Xem yas-staging có Synced + Healthy chưa
```

### 5.2 — Nếu chưa Synced: push branch staging

```bash
cd ~/project2/gitops-yas
git checkout -b staging   # nếu chưa có branch staging
# hoặc nếu branch đã tồn tại:
git checkout staging
git merge main
git push origin staging
```

Argo CD sẽ tự phát hiện và sync:
```bash
kubectl get applications -n argocd
# Kỳ vọng: yas-staging → Synced + Healthy

kubectl get pods -n yas-staging
# Kỳ vọng: 14 pods Running
```

### 5.3 — Test flow release (merge main → staging)

```bash
cd ~/project2/gitops-yas
git checkout staging
git merge main
git push origin staging
# Argo CD tự detect và sync yas-staging
```

---

## 6. Cài Istio + Kiali lên GKE (Nâng cao 2đ — Giao bạn khác)

> [!CAUTION]
> **Phần này sẽ restart toàn bộ 14 pods trong `yas-dev`.** Thông báo nhóm trước khi thực hiện.

### 6.1 — Kiểm tra Istio chưa được cài

```bash
kubectl get namespace istio-system
# Kỳ vọng: "Error from server (NotFound)" → chưa cài → tiếp tục

kubectl get crd | grep istio
# Không có output → chưa cài
```

### 6.2 — Cài Istio (profile demo)

> `istioctl` đã cài ở Bước 2.2.D — dùng luôn.

```bash
istioctl x precheck
# Kỳ vọng: "No issues found"

istioctl install --set profile=demo -y

kubectl rollout status deployment/istiod -n istio-system --timeout=120s

kubectl get pods -n istio-system
# Kỳ vọng:
# istiod-xxxx              1/1   Running
# istio-ingressgateway     1/1   Running
# istio-egressgateway      1/1   Running
```

### 6.3 — Cài Kiali + Prometheus + Jaeger

```bash
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.23/samples/addons/prometheus.yaml
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.23/samples/addons/kiali.yaml
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.23/samples/addons/jaeger.yaml

kubectl rollout status deployment/kiali -n istio-system --timeout=120s
```

### 6.4 — Enable sidecar injection cho `yas-dev`

> [!WARNING]
> **Thông báo nhóm trước** — pods sẽ restart hết, downtime ~2–5 phút.

```bash
kubectl label namespace yas-dev istio-injection=enabled

kubectl rollout restart deployment -n yas-dev

kubectl get pods -n yas-dev
# Kỳ vọng: READY = 2/2 (app + istio-proxy) cho mỗi pod
```

### 6.5 — Xử lý Argo CD OutOfSync sau inject sidecar

Sau khi inject sidecar, Argo CD sẽ phát hiện sự khác biệt (drift) giữa Git manifest và live resources trên cluster (do `istio-proxy` container và các config volumes tự động thêm vào).

Cần cập nhật cấu hình `yas-dev` Application trong `gitops-yas/argocd/applications/yas-dev.yaml` như sau:

```yaml
spec:
  ignoreDifferences:
    # 1. Bỏ qua container istio-proxy được inject tự động (theo name thay vì index)
    - group: apps
      kind: Deployment
      jqPathExpressions:
        - '.spec.template.spec.containers[] | select(.name == "istio-proxy")'
    # 2. Bỏ qua các config volumes và certs của Istio
    - group: apps
      kind: Deployment
      jqPathExpressions:
        - '.spec.template.spec.volumes[] | select(.name == "istio-envoy" or .name == "istio-certs" or .name == "istio-token")'
    # 3. Bỏ qua các annotations do Istio tự động gắn vào Pod template
    - group: apps
      kind: Deployment
      jqPathExpressions:
        - '.spec.template.metadata.annotations'
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
      - RespectIgnoreDifferences=true  # Ép Argo CD tuân thủ các quy tắc ignore trên khi tự động sync
```

---

## 7. Cấu hình Istio: mTLS (Nâng cao 2đ)

> [!NOTE]
> Các file template đã tạo trong `gitops-yas/helm/yas/templates/istio/` nhưng **đang rỗng (0 bytes)**.
> Điền nội dung rồi enable qua `values.yaml` để Argo CD tự apply.

### 7.1 — File: `templates/istio/peer-authentication.yaml`

```yaml
{{- if .Values.istio.enabled }}
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: {{ .Release.Namespace }}
spec:
  mtls:
    mode: STRICT
{{- end }}
```

### 7.2 — Tạo mới: `templates/istio/destination-rule.yaml`

```yaml
{{- if .Values.istio.enabled }}
---
# Enforce mTLS cho traffic nội bộ yas-dev
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: mtls-all-yas-dev
  namespace: {{ .Release.Namespace }}
spec:
  host: "*.{{ .Release.Namespace }}.svc.cluster.local"
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
---
# Ngoại lệ: Keycloak (Sử dụng service keycloak-service trong namespace keycloak)
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: keycloak-no-tls
  namespace: {{ .Release.Namespace }}
spec:
  host: "keycloak-service.keycloak.svc.cluster.local"
  trafficPolicy:
    tls:
      mode: DISABLE
---
# Ngoại lệ: Kafka (Namespace kafka - cả bootstrap và brokers)
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: kafka-no-tls
  namespace: {{ .Release.Namespace }}
spec:
  host: "*.kafka.svc.cluster.local"
  trafficPolicy:
    tls:
      mode: DISABLE
---
# Ngoại lệ: Elasticsearch (Namespace elasticsearch - các services es-http, es-internal-http)
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: elasticsearch-no-tls
  namespace: {{ .Release.Namespace }}
spec:
  host: "*.elasticsearch.svc.cluster.local"
  trafficPolicy:
    tls:
      mode: DISABLE
---
# Ngoại lệ: PostgreSQL (Namespace postgres - cả postgresql và postgresql-repl)
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: postgres-no-tls
  namespace: {{ .Release.Namespace }}
spec:
  host: "*.postgres.svc.cluster.local"
  trafficPolicy:
    tls:
      mode: DISABLE
---
# Ngoại lệ: Redis (Namespace redis - cả redis-master và redis-replicas)
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: redis-no-tls
  namespace: {{ .Release.Namespace }}
spec:
  host: "*.redis.svc.cluster.local"
  trafficPolicy:
    tls:
      mode: DISABLE
{{- end }}
```

> [!IMPORTANT]
> Verify hostname thực tế của infra services trước khi apply:
> ```bash
> kubectl get svc -A | grep -E "keycloak|kafka|elastic|postgres"
> ```

---

## 8. Cấu hình Istio: Authorization Policy (Nâng cao 2đ)

### 8.1 — Kiến trúc phân quyền

```
storefront-bff  → product ✅ | cart ✅ | customer ✅ | tax ✅ | search ✅
backoffice-bff  → product ✅ | order ✅ | inventory ✅ | customer ✅ | tax ✅
order           → tax ✅ | inventory ✅
[test-pod]      → product ✗ DENY (demo deny case)
```

### 8.2 — Cấu hình ServiceAccount cho từng Service trong Helm (Bắt buộc)

> [!IMPORTANT]
> **Kết quả kiểm tra thực tế:** Namespace `yas-dev` hiện tại chỉ có duy nhất ServiceAccount `default`.
> Nếu giữ nguyên, mọi Pod sẽ có cùng identity principal là `cluster.local/ns/yas-dev/sa/default`. Lúc này, Istio `AuthorizationPolicy` sẽ **không thể phân biệt** traffic giữa `storefront-bff`, `backoffice-bff` hay `order` (mọi rule check principal đều vô hiệu hoặc quá rộng).
>
> **Giải pháp:** Cần bổ sung ServiceAccount riêng cho từng service trong Helm chart để có identity riêng.

#### Bước A: Tạo file template `gitops-yas/helm/yas/templates/serviceaccount.yaml`
```yaml
{{- range $serviceName, $serviceConfig := .Values }}
{{- if typeIs "map[string]interface {}" $serviceConfig }}
{{- if hasKey $serviceConfig "enabled" }}
{{- if eq $serviceConfig.enabled true }}
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: {{ $serviceName }}
  namespace: {{ $.Release.Namespace }}
{{- end }}
{{- end }}
{{- end }}
{{- end }}
```

#### Bước B: Cập nhật `gitops-yas/helm/yas/templates/deployment.yaml`
Thêm `serviceAccountName` tương ứng vào spec của Pod template:
```yaml
    spec:
      serviceAccountName: {{ $serviceName }}  # Gắn ServiceAccount riêng cho Pod
      {{- if $serviceConfig.isBackend }}
      volumes:
        - name: yas-configuration
```

Khi Argo CD sync, mỗi Deployment sẽ tự động chạy dưới ServiceAccount mang tên của chính nó (ví dụ: SA `storefront-bff`, SA `order`, SA `product`...).

---

### 8.3 — File: `templates/istio/authorization-policy.yaml`

```yaml
{{- if .Values.istio.enabled }}
---
# Allow storefront-bff và backoffice-bff gọi product
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: allow-storefront-bff-to-product
  namespace: {{ .Release.Namespace }}
spec:
  selector:
    matchLabels:
      app: product
  action: ALLOW
  rules:
  - from:
    - source:
        principals:
        - "cluster.local/ns/{{ .Release.Namespace }}/sa/storefront-bff"
        - "cluster.local/ns/{{ .Release.Namespace }}/sa/backoffice-bff"
---
# Allow order, storefront-bff, backoffice-bff gọi tax
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: allow-to-tax
  namespace: {{ .Release.Namespace }}
spec:
  selector:
    matchLabels:
      app: tax
  action: ALLOW
  rules:
  - from:
    - source:
        principals:
        - "cluster.local/ns/{{ .Release.Namespace }}/sa/order"
        - "cluster.local/ns/{{ .Release.Namespace }}/sa/storefront-bff"
        - "cluster.local/ns/{{ .Release.Namespace }}/sa/backoffice-bff"
---
# Allow health checks từ Kubernetes probes và Argo CD
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: allow-health-check
  namespace: {{ .Release.Namespace }}
spec:
  action: ALLOW
  rules:
  - to:
    - operation:
        paths: ["/actuator/health", "/health", "/"]
---
# Default deny-all — APPLY SAU các allow policies ở trên
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: deny-all
  namespace: {{ .Release.Namespace }}
spec: {}
{{- end }}
```

---

## 9. Cấu hình Istio: Retry Policy (Nâng cao 2đ)

### 9.1 — File: `templates/istio/virtual-service.yaml`

```yaml
{{- if .Values.istio.enabled }}
---
# Retry policy cho tax service
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: tax-retry
  namespace: {{ .Release.Namespace }}
spec:
  hosts:
  - tax
  http:
  - retries:
      attempts: 3
      perTryTimeout: 5s
      retryOn: "5xx,retriable-4xx,connect-failure,reset"
    timeout: 30s
    route:
    - destination:
        host: tax
        port:
          number: 80
---
# Retry policy cho product service
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: product-retry
  namespace: {{ .Release.Namespace }}
spec:
  hosts:
  - product
  http:
  - retries:
      attempts: 3
      perTryTimeout: 5s
      retryOn: "5xx,connect-failure,reset"
    timeout: 30s
    route:
    - destination:
        host: product
        port:
          number: 80
{{- end }}
```

### 9.2 — Enable Istio qua values.yaml (GitOps cách đúng)

Thêm vào `gitops-yas/helm/yas/values.yaml`:
```yaml
istio:
  enabled: false   # mặc định tắt
```

Thêm vào `gitops-yas/helm/yas/values-dev.yaml`:
```yaml
istio:
  enabled: true    # bật cho dev sau khi Istio đã cài lên cluster
```

→ Commit + push `gitops-yas` → Argo CD sẽ tự apply tất cả Istio config.

---

## 10. Test Plan — Istio (Nâng cao 2đ)

### Test 1: Xác nhận sidecar đã inject (READY = 2/2)

```bash
kubectl get pods -n yas-dev
# Kỳ vọng: cột READY = 2/2 cho tất cả pods
```

### Test 2: Kiểm tra mTLS active

```bash
POD=$(kubectl get pod -n yas-dev -l app=product -o jsonpath='{.items[0].metadata.name}')
istioctl authn tls-check "$POD.yas-dev" product.yas-dev.svc.cluster.local
# Kỳ vọng: STATUS=OK, DR CONFIG=ISTIO_MUTUAL
```

### Test 3: Demo DENY (test-pod không có quyền)

```bash
# Tạo test-pod (không có SA được authorize)
kubectl run test-pod -n yas-dev --image=curlimages/curl:latest --restart=Never -- sleep 3600
kubectl wait --for=condition=Ready pod/test-pod -n yas-dev --timeout=60s

# Gọi product từ test-pod → expect DENY
kubectl exec -n yas-dev test-pod -- curl -v http://product/api/products
# Kỳ vọng: RBAC: access denied (403)
# Chụp log làm evidence
```

### Test 4: Demo ALLOW (storefront-bff có quyền)

```bash
STOREFRONT_POD=$(kubectl get pod -n yas-dev -l app=storefront-bff -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n yas-dev $STOREFRONT_POD -c storefront-bff -- curl -v http://product/api/products
# Kỳ vọng: 200 OK với JSON data
```

### Test 5: Demo Retry — Fault Injection

```bash
# Inject lỗi 50% vào tax để trigger retry
kubectl apply -f - <<EOF
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: tax-fault-inject
  namespace: yas-dev
spec:
  hosts:
  - tax
  http:
  - fault:
      abort:
        percentage:
          value: 50
        httpStatus: 500
    retries:
      attempts: 3
      perTryTimeout: 5s
      retryOn: "5xx"
    route:
    - destination:
        host: tax
        port:
          number: 80
EOF

# Gọi từ order pod
ORDER_POD=$(kubectl get pod -n yas-dev -l app=order -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n yas-dev $ORDER_POD -c order -- curl -v http://tax/api/taxes

# Xem retry log trong istio-proxy
TAX_POD=$(kubectl get pod -n yas-dev -l app=tax -o jsonpath='{.items[0].metadata.name}')
kubectl logs -n yas-dev $TAX_POD -c istio-proxy | grep -E "retry|upstream_reset|response_code" | tail -20

# Dọn dẹp fault injection sau khi test xong
kubectl delete virtualservice tax-fault-inject -n yas-dev
```

### Test 6: Kiali Topology Screenshot

```bash
istioctl dashboard kiali
# Hoặc: kubectl port-forward svc/kiali -n istio-system 20001:20001
# Mở browser: http://localhost:20001
# → Graph → Namespace: yas-dev
# → Bật Security overlay → thấy mTLS lock icons
# → Chụp screenshot
```

---

## 11. Thứ tự thực hiện

```
[BƯỚC 0 — Thiết lập môi trường (tất cả thành viên làm trước)]
Step 0.1: Nhờ nhóm cấp quyền GCP cho email của bạn
Step 0.2: Cài gcloud, kubectl, helm (và istioctl nếu làm Istio)
Step 0.3: gcloud auth login + get-credentials yas-gke
Step 0.4: kubectl get nodes → confirm 2 nodes Ready
Step 0.5: kubectl get pods -n yas-dev → confirm 14/14 Running

[PHẦN 1 — Bắt buộc, "Bạn đang làm"]
Step 1.1: kubectl get nodes -o wide → lấy ExternalIP
Step 1.2: Thêm vào hosts file: identity/storefront/backoffice.yas.local.com
Step 1.3: Truy cập NodePort (30001, 30003, 30014) → chụp screenshot evidence
Step 1.4: kubectl describe pod -l app=tax -n yas-dev | grep Image → confirm tag 2094d996
Step 1.5: Test developer_build_cleanup (DRY_RUN trước → rồi CONFIRM=true)

[PHẦN 2 — Nâng cao staging, "Bạn đang làm"]
Step 2.1: kubectl get applications -n argocd → confirm yas-staging status
Step 2.2: Nếu chưa Synced → git checkout staging + merge main + push
Step 2.3: Chụp screenshot yas-staging Synced + Healthy + pods Running

[PHẦN 3 — Nâng cao Istio, "Giao bạn khác"]
Step 3.1:  kubectl get namespace istio-system → confirm chưa cài
Step 3.2:  istioctl x precheck
Step 3.3:  istioctl install --set profile=demo -y
Step 3.4:  Cài Kiali + Prometheus + Jaeger addons
Step 3.5:  Tạo mới templates/serviceaccount.yaml và sửa templates/deployment.yaml để bật SA per-service
Step 3.6:  Điền nội dung 3 file template trong gitops-yas/helm/yas/templates/istio/
Step 3.7:  Tạo mới destination-rule.yaml (ngoại lệ cho keycloak, kafka, elastic, postgres, redis)
Step 3.8:  Thêm istio.enabled: false vào values.yaml và true vào values-dev.yaml
Step 3.9:  Cập nhật ignoreDifferences và RespectIgnoreDifferences vào yas-dev Application spec
Step 3.10: Commit + push gitops-yas → Argo CD tự động apply cấu hình
Step 3.11: label namespace yas-dev istio-injection=enabled
Step 3.12: kubectl rollout restart deployment -n yas-dev → đợi READY=2/2 cho các pods

[PHẦN 4 — Test & Evidence Istio]
Step 4.1: Test DENY (test-pod → product → 403) → chụp log
Step 4.2: Test ALLOW (storefront-bff → product → 200) → chụp log
Step 4.3: Fault inject → test retry → chụp istio-proxy log
Step 4.4: istioctl dashboard kiali → chụp topology screenshot

[PHẦN 5 — Docs & Commit]
Step 5.1: Tạo docs/istio-setup.md
Step 5.2: Cập nhật docs/project2-progress.md (tick checkboxes)
Step 5.3: Commit + push yas repo
```

---

## 12. Files cần commit

### Trong `gitops-yas/`:

```
helm/yas/
├── values.yaml                         # Thêm block: istio.enabled: false
├── values-dev.yaml                     # Thêm: istio.enabled: true
├── templates/deployment.yaml           # Thêm: serviceAccountName: {{ $serviceName }}
├── templates/serviceaccount.yaml       # [NEW] Tạo ServiceAccounts cho từng service
└── templates/istio/
    ├── peer-authentication.yaml        # ← Điền nội dung (mTLS STRICT)
    ├── authorization-policy.yaml       # ← Điền nội dung (allow & deny-all)
    ├── virtual-service.yaml            # ← Điền nội dung (retry tax & product)
    └── destination-rule.yaml           # ← [NEW] Tạo mới (ngoại lệ infra)
argocd/applications/
└── yas-dev.yaml                        # Thêm ignoreDifferences & RespectIgnoreDifferences
```

### Trong `yas/docs/`:

```
docs/
├── istio-setup.md                      # ← [NEW] README Istio step-by-step
└── project2-progress.md                # ← Tick các checkbox đã hoàn thành
```

---

## 13. Evidence cần chụp cho báo cáo

| Hạng mục | Cách lấy | Status |
|---|---|---|
| Jenkins CI build SUCCESS + docker push tag SHA | Jenkins log | ✅ Đã có |
| Docker Hub image `thu2005/yas-tax:2094d996` | hub.docker.com/u/thu2005 | ✅ Đã có |
| Jenkins developer_build SUCCESS + gitops commit jenkins-bot | Jenkins log + GitHub gitops-yas | ✅ Đã có |
| Argo CD yas-dev Synced + Healthy | `kubectl get applications -n argocd` | ✅ Đã có |
| 14/14 pods Running | `kubectl get pods -n yas-dev` | ✅ Đã có |
| NodePort URL accessible từ browser | Mở `http://<node-ip>:30001`, `30003`, `30014` | ❌ Cần làm |
| `kubectl describe pod ... \| grep Image` → tag `2094d996` | Terminal | ❌ Cần làm |
| Jenkins cleanup job SUCCESS + commit reset về `main` | Jenkins log + GitHub commit | ❌ Cần làm |
| Argo CD yas-staging Synced + Healthy + pods Running | `kubectl get applications + pods -n yas-staging` | ❌ Cần làm |
| Pods READY = 2/2 (Istio sidecar injected) | `kubectl get pods -n yas-dev` | ❌ Cần làm |
| mTLS DENY log (test-pod → product → 403) | `kubectl exec test-pod -- curl ...` | ❌ Cần làm |
| mTLS ALLOW log (storefront-bff → product → 200) | `kubectl exec storefront-bff -- curl ...` | ❌ Cần làm |
| Retry log (istio-proxy grep retry) | `kubectl logs ... -c istio-proxy` | ❌ Cần làm |
| Kiali topology screenshot + mTLS lock icons | `istioctl dashboard kiali` | ❌ Cần làm |

---

## 14. Thông tin xác thực từ Cluster (Resolved Checks)

> [!NOTE]
> Các câu hỏi kỹ thuật lớn đã được kiểm tra trực tiếp trên GKE cluster:
>
> 1. **ServiceAccount:** Xác nhận chỉ có SA `default` trên namespace `yas-dev`. Cần cấu hình tạo SA tự động theo mục 8.2 để kích hoạt kiểm tra principal.
> 2. **Infrastructure Hostnames:** Hostname chính xác đã được cập nhật vào DestinationRule ở mục 7.2 (đã bổ sung Redis và cập nhật `keycloak-service`).
> 3. **Argo CD:** Application `yas-dev` sẽ bị loop rollback khi có sidecar. Việc thêm `ignoreDifferences` và `RespectIgnoreDifferences=true` là bắt buộc.
> 4. **Jenkins Access:** Jenkins chạy trên local máy setup (`http://localhost:8081`). Sử dụng tunnel/ngrok nếu truy cập từ xa.
