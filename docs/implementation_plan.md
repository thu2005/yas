# Kế hoạch triển khai Service Mesh (Istio + Kiali) — Nâng cao 2đ

---

## Tổng quan trạng thái dự án

### ✅ Đã hoàn thành (bạn trong nhóm đã làm)

| Hạng mục | Chi tiết |
|---|---|
| CI Pipeline | Jenkins tự detect thay đổi → build → push Docker Hub (tag = commit SHA) |
| CD `developer_build` | Nhập branch → resolve SHA → cập nhật `values-dev.yaml` → jenkins-bot commit |
| GitOps repo | `github.com/thu2005/gitops-yas` hoạt động, jenkins-bot commit xác nhận |
| GKE Cluster | `yas-gke` tại `us-east1-b`, machine type `e2-standard-4`, 2 nodes |
| Argo CD | App `yas-dev` Synced + Healthy |
| Infra K8S | PostgreSQL, Kafka, Redis, Elasticsearch, Keycloak — tất cả Running |
| 14/14 YAS Pods | Namespace `yas-dev` ổn định hoàn toàn |

### ❌ Chưa làm (bạn sẽ thực hiện)

| Hạng mục | Điểm |
|---|---|
| Test URL NodePort + evidence báo cáo | Bắt buộc |
| Jenkins cleanup job test | Bắt buộc |
| Argo CD `yas-staging` ổn định | Nâng cao ~0.5đ |
| **Toàn bộ Service Mesh (Istio + Kiali)** | **Nâng cao 2đ** |

---

## PHẦN 0 — Thiết lập môi trường & phối hợp nhóm

> [!IMPORTANT]
> **Đây là bước BẮT BUỘC phải làm trước.** Không làm phần này thì các bước sau sẽ bị lỗi môi trường.
> Phần này đảm bảo bạn kết nối được vào đúng GKE cluster mà nhóm đang dùng, không tạo cluster mới hay xung đột.

### 0.1 — Yêu cầu thành viên đã setup cấp cho bạn

Liên hệ người đã setup GKE cluster, nhờ họ chạy lệnh này để cấp quyền cho email GCP của bạn:

```bash
# Người có quyền Owner chạy lệnh này
gcloud projects add-iam-policy-binding yas-devops-project2 \
  --member="user:EMAIL_CUA_BAN@gmail.com" \
  --role="roles/container.developer"
```

Bạn cũng cần **xác nhận với nhóm** các thông tin sau trước khi bắt đầu:

| Thông tin cần hỏi | Giá trị đã biết |
|---|---|
| GCP Project ID | `yas-devops-project2` |
| GKE cluster name | `yas-gke` |
| GKE zone | `us-east1-b` |
| Machine type của nodes | `e2-standard-4` (4 vCPU, 16GB RAM) |
| Cluster hiện tại đang chạy không? | Cần confirm — **đừng tự tạo cluster mới** |
| Argo CD có đang sync không? | Cần confirm trạng thái `yas-dev` |

> [!WARNING]
> **Đừng tạo GKE cluster mới!** Cluster `yas-gke` đã tồn tại và đang chứa toàn bộ ứng dụng.
> Tạo cluster mới sẽ tốn GCP credit và gây xung đột. Chỉ connect vào cluster cũ.

---

### 0.2 — Cài công cụ trên máy Windows (một lần duy nhất)

Mở **PowerShell as Administrator** và chạy từng bước:

#### Bước A: Cài Google Cloud CLI (gcloud)

Tải installer tại: https://cloud.google.com/sdk/docs/install-sdk#windows

Sau khi cài xong, mở terminal mới và kiểm tra:
```powershell
gcloud version
# Kỳ vọng: Google Cloud SDK 4xx.x.x
```

#### Bước B: Cài kubectl qua gcloud

```powershell
gcloud components install kubectl
kubectl version --client
# Kỳ vọng: Client Version: v1.xx.x
```

#### Bước C: Cài Helm

```powershell
# Dùng winget (Windows Package Manager)
winget install Helm.Helm

# Hoặc tải tại: https://github.com/helm/helm/releases
# Giải nén và thêm vào PATH

helm version
# Kỳ vọng: version.BuildInfo{Version:"v3.x.x"...}
```

#### Bước D: Cài istioctl (dùng để cài Istio lên K8S)

```powershell
# Tải istioctl 1.23.x (version stable, tương thích GKE)
# Truy cập: https://github.com/istio/istio/releases/tag/1.23.4
# Tải file: istio-1.23.4-win.zip

# Giải nén → thêm thư mục bin/ vào PATH Windows
# Hoặc dùng lệnh:
$ISTIO_VERSION="1.23.4"
Invoke-WebRequest -Uri "https://github.com/istio/istio/releases/download/$ISTIO_VERSION/istio-$ISTIO_VERSION-win.zip" -OutFile "istio.zip"
Expand-Archive -Path "istio.zip" -DestinationPath "C:\istio"
# Thêm "C:\istio\istio-1.23.4\bin" vào System PATH

istioctl version --remote=false
# Kỳ vọng: Istio 1.23.4
```

---

### 0.3 — Kết nối vào GKE cluster (đã tồn tại)

```powershell
# 1. Đăng nhập Google account
gcloud auth login
# → Browser mở → đăng nhập bằng email đã được cấp quyền

# 2. Set project
gcloud config set project yas-devops-project2

# 3. Lấy kubeconfig của cluster đã tồn tại
gcloud container clusters get-credentials yas-gke \
  --zone us-east1-b \
  --project yas-devops-project2

# 4. Kiểm tra kết nối
kubectl get nodes
# Kỳ vọng: 2 nodes ở trạng thái Ready
# NAME                                     STATUS   ROLES    AGE
# gke-yas-gke-default-pool-xxxx-xxxx      Ready    <none>   Xd
# gke-yas-gke-default-pool-xxxx-yyyy      Ready    <none>   Xd
```

---

### 0.4 — Xác nhận trạng thái cluster (KHÔNG thay đổi bất cứ gì ở bước này)

Chỉ quan sát, không sửa gì:

```powershell
# Xem tổng quan tất cả namespaces
kubectl get namespaces
# Kỳ vọng: thấy yas-dev, postgres, kafka, redis, elasticsearch, keycloak, argocd

# Xem pods YAS
kubectl get pods -n yas-dev
# Kỳ vọng: 14/14 pods Running

# Xem Argo CD apps
kubectl get applications -n argocd
# Kỳ vọng: yas-dev Synced + Healthy

# Xem resource còn lại trên nodes (quan trọng cho Istio)
kubectl describe nodes | grep -A5 "Allocated resources"
# Ghi lại số CPU và Memory còn available để đảm bảo đủ cho Istio
```

> [!NOTE]
> **Kiểm tra resource trước khi cài Istio:**
> - Istio control plane (istiod) cần: ~500m CPU + 2GB RAM
> - Kiali cần: ~100m CPU + 256MB RAM
> - Node `e2-standard-4` có 4 vCPU + 16GB RAM — đủ resource nếu cluster không quá tải

---

### 0.5 — Kiểm tra xung đột: Istio đã được cài chưa?

```powershell
# Kiểm tra namespace istio-system đã tồn tại chưa
kubectl get namespace istio-system
# Nếu thấy: "Error from server (NotFound)" → chưa cài → tiếp tục bình thường
# Nếu thấy namespace tồn tại → Istio đã được cài → BÁO NGAY cho nhóm trước khi làm gì thêm

# Kiểm tra CRD của Istio
kubectl get crd | grep istio
# Nếu không có output → chưa cài → tiếp tục bình thường
```

---

### 0.6 — Phối hợp với nhóm: Thống nhất trước khi cài Istio

> [!CAUTION]
> **Cài Istio sẽ RESTART toàn bộ 14 pods trong namespace `yas-dev`.** Pods sẽ downtime khoảng 2-5 phút.
> Phải thông báo cho nhóm trước khi thực hiện bước này để tránh làm gián đoạn khi nhóm đang test.

**Checklist phối hợp nhóm:**
- [ ] Thống nhất thời điểm cài Istio (không cài khi nhóm đang demo/test)
- [ ] Backup `values-dev.yaml` hiện tại (Argo CD có thể phát hiện drift sau khi Istio inject annotations)
- [ ] Người quản lý Argo CD sẵn sàng xử lý nếu `yas-dev` bị OutOfSync sau khi inject sidecar

**Giải pháp tránh xung đột Argo CD:**
Sau khi inject sidecar, Argo CD có thể báo OutOfSync vì pods có thêm container `istio-proxy`.
Cần thêm annotation ignore vào Argo CD application (nhóm thực hiện hoặc bạn nếu có quyền):

```yaml
# Thêm vào yas-dev ArgoCD Application spec
spec:
  ignoreDifferences:
  - group: apps
    kind: Deployment
    jsonPointers:
    - /spec/template/metadata/annotations
    - /spec/template/spec/containers
```

---

## PHẦN 1 — Cài Istio lên GKE

> [!NOTE]
> Bắt đầu phần này **sau khi hoàn thành PHẦN 0** và đã thông báo cho nhóm.

### 1.1 — Kiểm tra tương thích trước khi cài

```powershell
# Kiểm tra version K8S của GKE
kubectl version --short
# GKE thường chạy K8S 1.27-1.30 → tương thích với Istio 1.23.x

# Kiểm tra istioctl đã kết nối được cluster
istioctl x precheck
# Kỳ vọng: "No issues found when checking the cluster"
# Nếu có warning → đọc kỹ và fix trước
```

### 1.2 — Cài Istio với profile `demo`

```powershell
# Cài Istio (profile demo bao gồm cả Jaeger và Kiali addons)
istioctl install --set profile=demo -y

# Chờ istiod sẵn sàng
kubectl rollout status deployment/istiod -n istio-system --timeout=120s

# Kiểm tra
kubectl get pods -n istio-system
# Kỳ vọng:
# istiod-xxxx                     1/1   Running
# istio-ingressgateway-xxxx        1/1   Running
# istio-egressgateway-xxxx         1/1   Running
```

### 1.3 — Cài Kiali + Prometheus + Jaeger (addons)

```powershell
# Lấy đường dẫn samples của Istio đã tải về
# (thư mục istio-1.23.4/samples/addons/)

kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.23/samples/addons/prometheus.yaml
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.23/samples/addons/kiali.yaml
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.23/samples/addons/jaeger.yaml

# Chờ Kiali sẵn sàng
kubectl rollout status deployment/kiali -n istio-system --timeout=120s

# Kiểm tra
kubectl get pods -n istio-system
# Kỳ vọng: kiali, prometheus, jaeger — tất cả Running
```

### 1.4 — Enable sidecar injection cho namespace `yas-dev`

> [!WARNING]
> **Bước này sẽ restart toàn bộ 14 pods trong `yas-dev`.** Thông báo nhóm trước!

```powershell
# Label namespace để Istio tự inject sidecar vào mọi pod mới
kubectl label namespace yas-dev istio-injection=enabled

# Restart tất cả deployments để pods được inject sidecar ngay
kubectl rollout restart deployment -n yas-dev

# Chờ pods restart xong (khoảng 3-5 phút)
kubectl get pods -n yas-dev --watch
# Kỳ vọng: tất cả pods có READY = 2/2 (app + istio-proxy)

# Xác nhận sidecar đã inject
kubectl get pods -n yas-dev -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{range .spec.containers[*]}{.name}{" "}{end}{"\n"}{end}'
# Kỳ vọng: mỗi pod có 2 containers: <app-name> istio-proxy
```

---

## PHẦN 2 — Cấu hình mTLS (PeerAuthentication + DestinationRule)

### File: `k8s/istio/mtls/peer-authentication.yaml`

```yaml
# Bật mTLS STRICT toàn namespace yas-dev
# Tất cả traffic trong namespace PHẢI dùng mTLS, không chấp nhận plain text
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: yas-dev
spec:
  mtls:
    mode: STRICT
```

### File: `k8s/istio/mtls/destination-rule-mtls.yaml`

```yaml
# Enforce TLS khi gọi ra các service trong yas-dev
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: mtls-all-yas-dev
  namespace: yas-dev
spec:
  host: "*.yas-dev.svc.cluster.local"
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
```

### Apply và kiểm tra

```powershell
kubectl apply -f k8s/istio/mtls/peer-authentication.yaml
kubectl apply -f k8s/istio/mtls/destination-rule-mtls.yaml

# Kiểm tra mTLS đã active
istioctl authn tls-check <pod-name>.yas-dev product.yas-dev.svc.cluster.local
# Kỳ vọng: STATUS=OK, DR CONFIG=ISTIO_MUTUAL
```

> [!WARNING]
> **Lưu ý cross-namespace traffic:** Các service trong `yas-dev` gọi tới Keycloak (`keycloak` namespace),
> Kafka (`kafka` namespace), Elasticsearch (`elasticsearch` namespace) sẽ bị STRICT mTLS chặn vì các
> namespace đó không có Istio sidecar.
>
> **Giải pháp:** Tạo DestinationRule ngoại lệ cho từng host bên ngoài:

```yaml
# File: k8s/istio/mtls/destination-rule-external.yaml
# Bỏ qua mTLS cho các service infra bên ngoài mesh
---
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: keycloak-no-tls
  namespace: yas-dev
spec:
  host: "identity.keycloak.svc.cluster.local"
  trafficPolicy:
    tls:
      mode: DISABLE
---
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: kafka-no-tls
  namespace: yas-dev
spec:
  host: "kafka-cluster-kafka-brokers.kafka.svc.cluster.local"
  trafficPolicy:
    tls:
      mode: DISABLE
---
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: elasticsearch-no-tls
  namespace: yas-dev
spec:
  host: "*.elasticsearch.svc.cluster.local"
  trafficPolicy:
    tls:
      mode: DISABLE
---
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: postgres-no-tls
  namespace: yas-dev
spec:
  host: "postgresql.postgres.svc.cluster.local"
  trafficPolicy:
    tls:
      mode: DISABLE
```

---

## PHẦN 3 — Authorization Policy (Giới hạn service-to-service)

### Kiến trúc Authorization

```
storefront-bff  → product ✅ | cart ✅ | customer ✅ | tax ✅ | search ✅
backoffice-bff  → product ✅ | order ✅ | inventory ✅ | customer ✅ | tax ✅
order           → tax ✅ | inventory ✅
[test-pod]      → product ✗ DENY | cart ✗ DENY  (demo deny case)
```

### File: `k8s/istio/authz/authz-default-deny.yaml`

```yaml
# Deny all traffic không được khai báo (default deny)
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: deny-all
  namespace: yas-dev
spec: {}
  # spec rỗng = deny tất cả
```

### File: `k8s/istio/authz/authz-allow-policies.yaml`

```yaml
# Cho phép storefront-bff gọi các service cần thiết
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: allow-storefront-bff
  namespace: yas-dev
spec:
  selector:
    matchLabels:
      app: product
  action: ALLOW
  rules:
  - from:
    - source:
        principals:
        - "cluster.local/ns/yas-dev/sa/storefront-bff"
        - "cluster.local/ns/yas-dev/sa/backoffice-bff"
---
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: allow-order-to-tax
  namespace: yas-dev
spec:
  selector:
    matchLabels:
      app: tax
  action: ALLOW
  rules:
  - from:
    - source:
        principals:
        - "cluster.local/ns/yas-dev/sa/order"
        - "cluster.local/ns/yas-dev/sa/storefront-bff"
        - "cluster.local/ns/yas-dev/sa/backoffice-bff"
---
# Cho phép Argo CD và health checks
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: allow-health-check
  namespace: yas-dev
spec:
  action: ALLOW
  rules:
  - to:
    - operation:
        paths: ["/actuator/health", "/health", "/"]
```

### Apply

```powershell
# CẢNH BÁO: Apply deny-all trước sẽ chặn MỌI traffic ngay lập tức
# Nên apply allow policies TRƯỚC, sau đó mới apply deny-all

kubectl apply -f k8s/istio/authz/authz-allow-policies.yaml
kubectl apply -f k8s/istio/authz/authz-default-deny.yaml

# Kiểm tra
kubectl get authorizationpolicy -n yas-dev
```

---

## PHẦN 4 — Retry Policy (VirtualService)

### File: `k8s/istio/retry/virtual-service-tax.yaml`

```yaml
# Nếu tax service trả lỗi 5xx → Istio tự retry tối đa 3 lần
# Mỗi lần retry timeout 5 giây, tổng timeout 30 giây
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: tax-retry
  namespace: yas-dev
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
          number: 8080
```

### File: `k8s/istio/retry/virtual-service-product.yaml`

```yaml
# Retry policy cho product service
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: product-retry
  namespace: yas-dev
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
          number: 8080
```

### Apply và kiểm tra

```powershell
kubectl apply -f k8s/istio/retry/virtual-service-tax.yaml
kubectl apply -f k8s/istio/retry/virtual-service-product.yaml

kubectl get virtualservice -n yas-dev
```

---

## PHẦN 5 — Test Plan

### Test 1: Kiểm tra mTLS đang hoạt động

```powershell
# Tạo test pod (không có trong service mesh hierarchy)
kubectl run test-pod -n yas-dev \
  --image=curlimages/curl:latest \
  --restart=Never \
  -- sleep 3600

# Chờ pod sẵn sàng
kubectl wait --for=condition=Ready pod/test-pod -n yas-dev --timeout=60s

# Test gọi product service từ test-pod (không có service account được authorize)
kubectl exec -n yas-dev test-pod -- curl -v http://product:8080/api/products
# Kỳ vọng: RBAC: access denied (403) hoặc Connection refused
# → Chứng minh AuthorizationPolicy đang hoạt động (DENY case)
```

### Test 2: Kiểm tra allow từ service được phép

```powershell
# Exec vào pod storefront-bff (được phép gọi product)
kubectl exec -n yas-dev -it \
  $(kubectl get pod -n yas-dev -l app=storefront-bff -o jsonpath='{.items[0].metadata.name}') \
  -c storefront-bff \
  -- curl -v http://product:8080/api/products
# Kỳ vọng: 200 OK với JSON data
# → Chứng minh AuthorizationPolicy ALLOW hoạt động
```

### Test 3: Retry evidence — Inject fault và quan sát retry

```powershell
# Inject lỗi 50% request vào tax service để trigger retry
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
          number: 8080
EOF

# Gọi tax từ authorized pod và xem Istio access log
kubectl exec -n yas-dev \
  $(kubectl get pod -n yas-dev -l app=order -o jsonpath='{.items[0].metadata.name}') \
  -c order \
  -- curl -v http://tax:8080/api/taxes

# Xem retry log trong istio-proxy sidecar
kubectl logs -n yas-dev \
  $(kubectl get pod -n yas-dev -l app=tax -o jsonpath='{.items[0].metadata.name}') \
  -c istio-proxy | grep -E "retry|upstream_reset|response_code" | tail -20

# Sau khi test xong, xóa fault injection
kubectl delete virtualservice tax-fault-inject -n yas-dev
# Reapply policy retry bình thường
kubectl apply -f k8s/istio/retry/virtual-service-tax.yaml
```

### Test 4: Xem Kiali topology

```powershell
# Port-forward Kiali UI ra máy local
kubectl port-forward svc/kiali -n istio-system 20001:20001

# Mở browser: http://localhost:20001
# Login: admin/admin
# → Graph → Namespace: yas-dev
# → Chụp screenshot topology
# → Quan sát: mTLS lock icons, service connections, traffic flow
```

---

## PHẦN 6 — Deliverables cần chuẩn bị

### Files cần tạo trong repo

```
k8s/istio/
├── install-istio.sh                    # Script cài Istio + Kiali
├── mtls/
│   ├── peer-authentication.yaml        # mTLS STRICT
│   ├── destination-rule-mtls.yaml      # Enforce ISTIO_MUTUAL
│   └── destination-rule-external.yaml  # Ngoại lệ cho infra bên ngoài mesh
├── authz/
│   ├── authz-default-deny.yaml         # Default deny all
│   └── authz-allow-policies.yaml       # Allow rules cụ thể
├── retry/
│   ├── virtual-service-tax.yaml        # Retry policy tax
│   └── virtual-service-product.yaml    # Retry policy product
└── test/
    └── test-plan.sh                    # Script test tự động
docs/
└── istio-setup.md                      # README hướng dẫn từng bước
```

### Evidence cần chụp cho báo cáo

| Evidence | Cách lấy |
|---|---|
| Kiali topology screenshot | Mở `http://localhost:20001`, Graph → yas-dev |
| mTLS lock icons trên Kiali | Bật `Security` overlay trong Kiali graph |
| Curl DENY log | Kết quả `kubectl exec test-pod -- curl ...` |
| Curl ALLOW log | Kết quả exec từ authorized pod |
| Retry log | `kubectl logs ... -c istio-proxy | grep retry` |
| Pods có 2 containers | `kubectl get pods -n yas-dev` → cột READY = 2/2 |

---

## Thứ tự thực hiện tổng thể

```
[PHẦN 0 — Thiết lập môi trường]
Step 0.1: Nhờ nhóm cấp quyền GCP cho email của bạn
Step 0.2: Cài gcloud, kubectl, helm, istioctl trên máy Windows
Step 0.3: gcloud auth login + get-credentials yas-gke
Step 0.4: Verify cluster (CHỈ xem, không sửa)
Step 0.5: Kiểm tra Istio chưa được cài
Step 0.6: Thống nhất với nhóm về thời điểm cài

[PHẦN 1 — Cài Istio]
Step 1.1: istioctl x precheck
Step 1.2: istioctl install --set profile=demo -y
Step 1.3: Cài Kiali + Prometheus + Jaeger addons
Step 1.4: Label namespace + restart pods (THÔNG BÁO NHÓM)

[PHẦN 2 — mTLS]
Step 2.1: Apply PeerAuthentication STRICT
Step 2.2: Apply DestinationRule (ISTIO_MUTUAL)
Step 2.3: Apply DestinationRule ngoại lệ cho Kafka, ES, Keycloak, Postgres

[PHẦN 3 — Authorization Policy]
Step 3.1: Apply allow policies (TRƯỚC)
Step 3.2: Apply deny-all (SAU)

[PHẦN 4 — VirtualService Retry]
Step 4.1: Apply VirtualService cho tax
Step 4.2: Apply VirtualService cho product

[PHẦN 5 — Test + Evidence]
Step 5.1: Test DENY từ test-pod → chụp log
Step 5.2: Test ALLOW từ authorized pod → chụp log
Step 5.3: Inject fault → test retry → chụp log
Step 5.4: Chụp Kiali topology screenshot
Step 5.5: Dọn dẹp fault injection

[PHẦN 6 — Commit vào repo]
Step 6.1: Tạo tất cả YAML files trong k8s/istio/
Step 6.2: Viết docs/istio-setup.md
Step 6.3: Cập nhật docs/project2-progress.md (tick các checkbox)
Step 6.4: Commit + push lên github.com/thu2005/yas
```

---

## Open Questions

> [!IMPORTANT]
> **Q1 — Cluster có đang chạy không?**
> GKE cluster tốn tiền GCP. Hỏi nhóm xem cluster `yas-gke` đang bật hay đã tắt để tiết kiệm credit.
> Nếu đã tắt, cần nhờ người có quyền Owner khởi động lại.

> [!IMPORTANT]
> **Q2 — Service Account của các pods có tên gì?**
> AuthorizationPolicy dùng ServiceAccount name để identify service.
> Cần kiểm tra thực tế: `kubectl get sa -n yas-dev`
> Nếu pods dùng ServiceAccount mặc định (`default`), cần tạo riêng SA cho từng service hoặc dùng label-based policy thay vì principal-based.

> [!WARNING]
> **Q3 — Argo CD có thể báo OutOfSync sau khi inject sidecar.**
> Cần nhờ người quản lý Argo CD thêm `ignoreDifferences` vào Application spec.
> Nếu không làm, Argo CD sẽ liên tục rollback pods → xóa sidecar → mTLS không hoạt động.

> [!NOTE]
> **Q4 — Cross-namespace mTLS cần xác nhận thực tế.**
> DestinationRule ngoại lệ trong plan trên dùng hostname dự đoán từ docs.
> Cần chạy `kubectl get svc -A` để xác nhận tên services thực tế của Keycloak, Kafka, ES, Postgres trước khi apply.
