# Cluster Setup and RBAC

This project requires permissions to create Kubernetes Job and CronJob resources
along with reading associated Pods for log streaming.

Minimal Role example:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: quartzkube-job-runner
rules:
  - apiGroups: ["batch"]
    resources: ["jobs", "cronjobs"]
    verbs: ["create", "get", "watch", "list"]
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "watch", "list"]
```

Bind the Role to the service account running the scheduler:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: quartzkube-job-runner
subjects:
  - kind: ServiceAccount
    name: default
    namespace: default
roleRef:
  kind: Role
  name: quartzkube-job-runner
  apiGroup: rbac.authorization.k8s.io
```

Ensure your cluster has sufficient resources and that the scheduler can reach the
Kubernetes API (in-cluster or via kubeconfig).
