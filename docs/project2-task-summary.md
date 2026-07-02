# Tổng hợp công việc Đồ án 2

Tài liệu này tổng hợp trạng thái hiện tại của repo `yas` so với yêu cầu đồ án CD/GitOps/Service Mesh. Nội dung được đối chiếu từ file hướng dẫn đồ án, chương trình hiện tại và các tài liệu/pipeline đang có trong repo.

> Lưu ý: PDF đề bài nằm ngoài repo tại `C:\Users\PHUC\Downloads\Project02_HKII_25_26.pdf`. Máy hiện không có công cụ trích PDF chuyên dụng, nên phần yêu cầu được tổng hợp theo nội dung đề bài đã phản ánh trong các tài liệu hiện có: `docs/project2-progress.md`, `docs/demo-guide.md`, `docs/implementation_plan.md`, `docs/installation.md` và pipeline Jenkins.

## 1. Tóm tắt yêu cầu cần đạt

### Phần bắt buộc

- CI phải build được service khi developer push branch.
- Docker image phải được push lên Docker Hub, tag theo commit id ngắn.
- Developer chọn branch/tag của từng service để deploy môi trường test.
- Hệ thống phải chạy được trên Kubernetes.
- Cần có job cleanup/destroy để xóa môi trường test hoặc reset image về bản ổn định.
- Cần có evidence cho báo cáo: Jenkins log, Docker Hub image, Kubernetes pods, URL test, GitOps/Argo CD nếu dùng hướng GitOps.

### Phần nâng cao

- Argo CD/GitOps cho các môi trường `dev` và `staging`.
- Flow promote/release từ `dev` sang `staging`.
- Service Mesh với Istio/Kiali:
  - mTLS.
  - AuthorizationPolicy.
  - Retry/timeout bằng VirtualService.
  - Evidence topology, allow/deny, retry.

## 2. Công việc đã thực hiện

### 2.1. Ứng dụng YAS gốc

Đã có nền tảng microservices tương đối đầy đủ:

- Backend Java/Spring Boot: `cart`, `customer`, `inventory`, `media`, `order`, `product`, `promotion`, `search`, `tax`, `sampledata`, `storefront-bff`, `backoffice-bff`.
- Frontend Next.js: `storefront`, `backoffice`.
- Hạ tầng local: `docker-compose.yml`, `docker-compose.search.yml`, `docker-compose.o11y.yml`.
- K8s/Helm charts: `k8s/charts/*`, `k8s/deploy/*`.
- Observability local: Prometheus, Grafana, Loki, Tempo trong `docker-compose.o11y.yml` và `docker/*`.

### 2.2. CI build và push image

Đã có `Jenkinsfile` ở root repo:

- Detect file thay đổi để xác định module/service bị ảnh hưởng.
- Maven build các module liên quan.
- Chạy Gitleaks scan.
- Build và push Docker image lên Docker Hub namespace `thu2005`.
- Image tag dùng `git rev-parse --short=8 HEAD`.
- Chỉ push image cho branch không phải `main` và không phải PR.

Các service được build/push trong Jenkinsfile hiện tại:

```text
backoffice
backoffice-bff
storefront
storefront-bff
cart
customer
inventory
media
order
product
promotion
search
tax
sampledata
```

Ngoài Jenkins, repo vẫn còn GitHub Actions CI gốc trong `.github/workflows`, nhưng đồ án hiện đang tập trung vào Jenkins.

### 2.3. Jenkins developer build

Đã có `Jenkinsfile.build` và Job DSL `jenkins/job-dsl/developer_build.groovy`.

Job `developer_build` hiện làm được:

- Nhận branch cho từng service qua params `TAG_CART`, `TAG_TAX`, `TAG_ORDER`, ...
- Resolve branch sang short commit SHA bằng `git ls-remote`.
- Tạo namespace test theo người chạy và service thay đổi, ví dụ `test-user-tax`.
- Clone repo GitOps `https://github.com/thu2005/gitops-yas.git`.
- Deploy base infrastructure bằng Helm/kubectl: PostgreSQL, Redis, Kafka, Elasticsearch, Keycloak.
- Deploy shared config và các YAS services.
- Service có branch khác `main` sẽ dùng image tag commit SHA.
- In NodePort URL sau khi deploy thành công.

Đây là hướng "developer test namespace" trực tiếp bằng Helm, không phải hướng cập nhật `values-dev.yaml` rồi chờ Argo CD sync.

### 2.4. Jenkins destroy/cleanup

Đã có `Jenkinsfile.destroy` và Job DSL `jenkins/job-dsl/developer_destroy.groovy`.

Job `developer_destroy` hiện làm được:

- Nhận `TARGET_NAMESPACE`.
- Chỉ cho phép xóa namespace dạng `test-*`.
- Xóa Kafka/Keycloak/OpenTelemetry/Grafana resources nếu có.
- Xóa PVC.
- Xóa namespace và cố gắng gỡ finalizer nếu namespace bị kẹt terminating.

Trạng thái: file/job đã có, nhưng cần chạy thực tế và chụp evidence.

### 2.5. Script bootstrap image baseline

Đã có `scripts/build-cd-baseline-images.sh`:

- Build các Java modules cần cho CD baseline.
- Build và push Docker images với tag mặc định `main`.
- Dùng Docker Hub namespace mặc định `thu2005`.

Script này hữu ích trước khi chạy `developer_build`, vì các service không override branch sẽ dùng image tag `main`.

### 2.6. GKE, Argo CD và hạ tầng

Đã có tài liệu triển khai trong `docs/installation.md`, `docs/project2-progress.md`, `docs/team-onboarding.md`:

- GCP project: `yas-devops-project2`.
- GKE cluster: `yas-gke`.
- Zone: `us-east1-b`.
- Namespace hạ tầng: `postgres`, `kafka`, `redis`, `elasticsearch`, `keycloak`, `argocd`.
- Namespace ứng dụng: `yas-dev`, `yas-staging`.
- Đã ghi nhận `yas-dev` và phần lớn/tất cả pods chạy ổn định trong tài liệu tiến độ.
- Đã có hướng dẫn lấy Node external IP, sửa hosts file và truy cập UI/Swagger qua NodePort.

### 2.7. Tài liệu demo và onboarding

Đã có:

- `docs/demo-guide.md`: mô tả luồng demo CI/CD, Argo CD sync, cleanup, evidence.
- `docs/developer-cd.md`: mô tả developer build/destroy namespace test.
- `docs/installation.md`: ghi lại cách cài Jenkins, GKE, Argo CD, infra.
- `docs/team-onboarding.md`: hướng dẫn thành viên khác kết nối cluster và làm tiếp.
- `docs/implementation_plan.md`: kế hoạch chi tiết cho Istio/Kiali.

## 3. Công việc chưa thực hiện hoặc cần xác nhận lại

### 3.1. Cần thống nhất lại hướng CD chính

Hiện tài liệu và pipeline có điểm lệch:

- `docs/demo-guide.md` mô tả hướng GitOps: `developer_build` cập nhật `values-dev.yaml`, commit vào `gitops-yas`, Argo CD sync vào `yas-dev`.
- `Jenkinsfile.build` thực tế lại deploy trực tiếp bằng Helm vào namespace test riêng `test-*`, không commit GitOps values và không phụ thuộc Argo CD cho bước developer test.

Cần chọn một hướng chính để báo cáo/demo:

- Hướng A: GitOps-first với Argo CD `yas-dev`/`yas-staging`.
- Hướng B: Developer namespace trực tiếp bằng Jenkins + Helm, còn Argo CD dùng cho môi trường dev/staging ổn định.

Khuyến nghị: dùng Hướng B cho phần developer test vì khớp code hiện tại, đồng thời trình bày Argo CD/GitOps như phần môi trường dev/staging nâng cao nếu nhóm đã có repo `gitops-yas` chạy ổn.

### 3.2. Chưa có evidence đầy đủ

Cần chụp lại evidence mới nhất:

- Jenkins CI build success cho một branch demo.
- Log `Affected Maven modules` và `Affected Docker services`.
- Log Docker push image `thu2005/yas-<service>:<short-sha>`.
- Docker Hub có image tag tương ứng.
- Jenkins `developer_build` success.
- Namespace test được tạo.
- Pods trong namespace test Running.
- URL NodePort truy cập được.
- Jenkins `developer_destroy` success.
- Namespace test đã bị xóa.

### 3.3. Chưa xác nhận cleanup/destroy chạy thực tế

`Jenkinsfile.destroy` đã sẵn sàng, nhưng cần test:

```bash
kubectl get ns
# Chọn namespace dạng test-*
```

Chạy job `developer_destroy` với `TARGET_NAMESPACE=<namespace-test>`, sau đó kiểm tra:

```bash
kubectl get ns <namespace-test>
# Kỳ vọng: NotFound
```

### 3.4. Argo CD staging chưa ổn định hoặc chưa có evidence

Theo `docs/project2-progress.md`, phần còn lại:

- `yas-staging` cần sync ổn định.
- Cần test flow merge/promote từ `main` sang `staging` trong repo GitOps.
- Cần evidence `kubectl get applications -n argocd` cho cả `yas-dev` và `yas-staging`.

### 3.5. Service Mesh chưa được implement trong repo

Hiện chưa có thư mục `k8s/istio`.

`docs/implementation_plan.md` đã có kế hoạch chi tiết, nhưng chưa có manifest/script thực tế:

- `PeerAuthentication` mTLS STRICT.
- `DestinationRule` mTLS và ngoại lệ cho infra ngoài mesh.
- `AuthorizationPolicy` allow/deny.
- `VirtualService` retry/timeout.
- Script cài Istio/Kiali.
- Script test allow/deny/retry.
- Evidence Kiali topology.

### 3.6. Một số tài liệu có thể bị lỗi thời

`docs/team-onboarding.md` có đoạn nói `13/14 YAS pods Running` và `search` còn chờ fix, trong khi `docs/project2-progress.md` ghi `14/14 pods Running`. Cần kiểm tra cluster thực tế rồi cập nhật tài liệu cuối cùng theo một trạng thái duy nhất.

## 4. Hướng dẫn hoàn thiện phần bắt buộc

### Bước 1: Xác nhận môi trường và công cụ

Trên Jenkins agent/máy chạy demo cần có:

```bash
git --version
docker version
kubectl version --client
helm version
mvn -version
```

Kiểm tra cluster:

```bash
gcloud config set project yas-devops-project2
gcloud container clusters get-credentials yas-gke --zone us-east1-b --project yas-devops-project2
kubectl get nodes -o wide
```

### Bước 2: Push baseline images tag `main`

Chạy một lần nếu Docker Hub chưa có tag `main` cho các service:

```bash
docker login
DOCKERHUB_NAMESPACE=thu2005 IMAGE_TAG=main ./scripts/build-cd-baseline-images.sh
```

Evidence cần chụp:

- Terminal/Jenkins log build success.
- Docker Hub có các image `thu2005/yas-*:main`.

### Bước 3: Test CI branch build

Tạo hoặc dùng branch demo, ví dụ `dev_tax_service_test`, sửa nhỏ trong service `tax`, push branch:

```bash
git checkout -b dev_tax_service_test
git push origin dev_tax_service_test
```

Chạy Jenkins multibranch job `YAS-Microservices-CI` cho branch đó.

Kỳ vọng log:

```text
Affected Maven modules: tax
Affected Docker services: tax
Building thu2005/yas-tax:<short-sha>
docker push thu2005/yas-tax:<short-sha>
Pipeline SUCCESS
```

### Bước 4: Test developer build namespace

Chạy Jenkins job `developer_build`.

Ví dụ params:

```text
TAG_TAX=dev_tax_service_test
TAG_CART=main
TAG_ORDER=main
TAG_PRODUCT=main
...
```

Sau khi job success, ghi lại:

- Namespace được tạo, ví dụ `test-phuc-tax`.
- Các URL NodePort Jenkins in ra.
- Cleanup URL Jenkins in ra.

Kiểm tra bằng kubectl:

```bash
kubectl get pods -n <namespace-test>
kubectl get svc -n <namespace-test>
```

Nếu pod chưa Running, xem log:

```bash
kubectl describe pod -n <namespace-test> <pod-name>
kubectl logs -n <namespace-test> <pod-name> --all-containers --tail=100
```

### Bước 5: Test truy cập ứng dụng

Lấy node IP:

```bash
kubectl get nodes -o wide
```

Nếu dùng hosts file, thêm:

```text
<node-ip> api.yas.local.com
<node-ip> storefront.yas.local.com
<node-ip> backoffice.yas.local.com
<node-ip> identity.yas.local.com
```

Mở các URL Jenkins in ra, hoặc kiểm tra bằng curl:

```bash
curl http://<node-ip>:<node-port>
```

Evidence cần chụp:

- UI Storefront/Backoffice hoặc Swagger mở được.
- Pod service demo đang dùng image tag commit SHA:

```bash
kubectl describe pod -n <namespace-test> -l app=tax | grep Image
```

### Bước 6: Test destroy/cleanup

Chạy Jenkins job `developer_destroy`:

```text
TARGET_NAMESPACE=<namespace-test>
```

Kiểm tra:

```bash
kubectl get ns <namespace-test>
```

Kỳ vọng namespace đã bị xóa. Chụp Jenkins log success và output kubectl.

## 5. Hướng dẫn hoàn thiện Argo CD/GitOps nâng cao

### Bước 1: Xác nhận Argo CD apps

```bash
kubectl get applications -n argocd
kubectl get pods -n yas-dev
kubectl get pods -n yas-staging
```

Kỳ vọng:

```text
yas-dev      Synced   Healthy
yas-staging  Synced   Healthy
```

### Bước 2: Nếu dùng GitOps repo cho dev/staging

Trong repo `gitops-yas`, kiểm tra:

- Application `yas-dev` theo branch/path dev.
- Application `yas-staging` theo branch/path staging.
- Values image tag cho từng môi trường.

Test promote:

```bash
git checkout staging
git merge main
git push origin staging
```

Sau đó:

```bash
kubectl get applications -n argocd
kubectl get pods -n yas-staging
```

Evidence cần chụp:

- Git commit/merge vào branch `staging`.
- Argo CD `yas-staging` Synced + Healthy.
- Pods `yas-staging` Running.

### Bước 3: Cập nhật tài liệu cho khớp pipeline

Nếu chọn hướng developer namespace, cần sửa `docs/demo-guide.md` để không nói `developer_build` commit `values-dev.yaml` trừ khi nhóm thật sự có job khác làm việc đó.

Nội dung nên ghi:

- Jenkins CI build/push image.
- Jenkins `developer_build` deploy namespace test bằng Helm.
- Argo CD quản lý môi trường `yas-dev`/`yas-staging` riêng.
- Jenkins `developer_destroy` xóa namespace test sau khi demo.

## 6. Hướng dẫn hoàn thiện Service Mesh

### Bước 1: Tạo cấu trúc file

Tạo các file sau:

```text
k8s/istio/
├── install-istio.sh
├── mtls/
│   ├── peer-authentication.yaml
│   ├── destination-rule-mtls.yaml
│   └── destination-rule-external.yaml
├── authz/
│   ├── authz-default-deny.yaml
│   └── authz-allow-policies.yaml
├── retry/
│   ├── virtual-service-tax.yaml
│   └── virtual-service-product.yaml
└── test/
    └── test-plan.sh
```

Có thể lấy nội dung manifest mẫu từ `docs/implementation_plan.md`.

### Bước 2: Cài Istio và Kiali

Trước khi cài:

```bash
kubectl get namespace istio-system
kubectl get crd | grep istio
istioctl x precheck
```

Cài:

```bash
istioctl install --set profile=demo -y
kubectl rollout status deployment/istiod -n istio-system --timeout=120s
```

Cài addon:

```bash
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.23/samples/addons/prometheus.yaml
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.23/samples/addons/kiali.yaml
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.23/samples/addons/jaeger.yaml
```

### Bước 3: Inject sidecar

Chọn namespace test hoặc `yas-dev`. Khuyến nghị dùng namespace test trước để tránh ảnh hưởng demo chung.

```bash
kubectl label namespace <namespace> istio-injection=enabled
kubectl rollout restart deployment -n <namespace>
kubectl get pods -n <namespace>
```

Kỳ vọng cột READY là `2/2`.

### Bước 4: Apply mTLS, AuthorizationPolicy, Retry

```bash
kubectl apply -f k8s/istio/mtls/ -n <namespace>
kubectl apply -f k8s/istio/authz/ -n <namespace>
kubectl apply -f k8s/istio/retry/ -n <namespace>
```

Lưu ý quan trọng: nếu service trong mesh gọi Keycloak/Kafka/Elasticsearch/PostgreSQL ở namespace khác nhưng các infra đó không có sidecar, cần có `DestinationRule` ngoại lệ `tls.mode: DISABLE` cho đúng hostname thực tế.

Xác nhận hostname bằng:

```bash
kubectl get svc -A
```

### Bước 5: Test evidence Service Mesh

Kiali:

```bash
kubectl port-forward svc/kiali -n istio-system 20001:20001
```

Mở:

```text
http://localhost:20001
```

Evidence cần chụp:

- Kiali graph namespace test hoặc `yas-dev`.
- Pods READY `2/2`.
- mTLS lock/security overlay.
- Curl từ pod không được phép bị deny.
- Curl từ service được phép trả 200.
- Retry log hoặc metric khi inject fault.

## 7. Checklist cuối trước khi nộp

- [ ] Tài liệu demo thống nhất với pipeline thật.
- [ ] Jenkins CI branch build success.
- [ ] Docker Hub có image tag commit SHA.
- [ ] Jenkins `developer_build` success.
- [ ] Namespace test có pods Running.
- [ ] URL NodePort truy cập được.
- [ ] Jenkins `developer_destroy` success.
- [ ] Nếu báo cáo Argo CD: `yas-dev` và `yas-staging` có evidence Synced/Healthy.
- [ ] Nếu làm Service Mesh: có manifest trong `k8s/istio`, pods `2/2`, Kiali screenshot, allow/deny/retry evidence.
- [ ] Cập nhật lại `docs/project2-progress.md` theo trạng thái cuối cùng.

## 8. File cần ưu tiên sửa tiếp

- `docs/demo-guide.md`: sửa lại cho khớp `Jenkinsfile.build` hiện tại hoặc ghi rõ có hai hướng CD.
- `docs/project2-progress.md`: cập nhật trạng thái thật sau khi chạy lại cluster.
- `docs/team-onboarding.md`: sửa `13/14` hoặc `14/14` theo kết quả mới nhất.
- `k8s/istio/*`: thêm manifest Service Mesh nếu nhóm làm phần nâng cao.
