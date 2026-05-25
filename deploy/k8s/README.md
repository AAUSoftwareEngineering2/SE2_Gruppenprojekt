# Kuhhandel Kubernetes Setup

Dieses Verzeichnis beschreibt den Kubernetes-Zustand für den Kuhhandel-Server.
Echte Secrets gehören nicht ins Repo.

## Architektur

Production:

```text
Android App
    |
    v
Cloudflare Edge
    |
    v
cloudflared Pods
Namespace: cloudflare
    |
    v
Kubernetes Service
kuhhandel-server.kuhhandel.svc.cluster.local:8080
Namespace: kuhhandel
    |
    +--> kuhhandel-server Pod
    +--> kuhhandel-server Pod
    |
    v
Supabase Postgres
```

Staging ist gleich aufgebaut, verwendet aber eigene Namespaces und nur einen
Server-Pod:

```text
Android App / Testclient
    |
    v
Cloudflare Edge
    |
    v
cloudflared Pod
Namespace: cloudflare-staging
    |
    v
Kubernetes Service
kuhhandel-server.kuhhandel-staging.svc.cluster.local:8080
Namespace: kuhhandel-staging
    |
    v
kuhhandel-server Pod
    |
    v
Supabase Postgres
```

## Ordnerstruktur

```text
base/
  app/
    Gemeinsame Dateien für den Spring-Boot-Server.

  tunnel/
    Gemeinsame Dateien für den Cloudflare Tunnel.

overlays/production/
  namespaces.yaml
    Erstellt die Production-Namespaces.

  app/
    Setzt die App-Basis auf Production.

  tunnel/
    Setzt die Tunnel-Basis auf Production.

overlays/staging/
  Gleicher Aufbau wie Production, aber mit eigenen Staging-Namespaces,
  Image-Tag `staging` und weniger Pods.
```

## Wichtige Dateien

```text
base/app/kustomization.yaml
  Sammelt nur die App-Dateien.

base/app/deployment.yaml
  Startet die Spring-Boot-Server-Pods.

base/app/service.yaml
  Gibt den Server-Pods eine stabile interne Adresse.

base/app/pdb.yaml
  Verhindert bei Wartung, dass alle Server-Pods gleichzeitig freiwillig weg sind.

base/app/supabase-ca-configmap.yaml
  Stellt das öffentliche Supabase-CA-Zertifikat bereit.

base/tunnel/kustomization.yaml
  Sammelt nur die Tunnel-Dateien.

base/tunnel/deployment.yaml
  Startet den Cloudflare Tunnel im Cluster.

base/tunnel/pdb.yaml
  Verhindert bei Wartung, dass alle Tunnel-Pods gleichzeitig freiwillig weg sind.

overlays/production/kustomization.yaml
  Ist der Einstiegspunkt für `kubectl apply -k`.

base/*/secret-*.example.yaml
  Nur Vorlagen. Nicht direkt deployen.
```

## Secrets Anlegen

Production:

```bash
kubectl apply -f deploy/k8s/overlays/production/namespaces.yaml

kubectl -n cloudflare create secret generic cloudflared-token \
  --from-literal=token='REPLACE_WITH_CLOUDFLARE_TUNNEL_TOKEN'

kubectl -n kuhhandel create secret generic kuhhandel-db \
  --from-literal=SPRING_DATASOURCE_URL='REPLACE_WITH_SUPABASE_JDBC_URL' \
  --from-literal=SPRING_DATASOURCE_USERNAME='REPLACE_WITH_SUPABASE_USER' \
  --from-literal=SPRING_DATASOURCE_PASSWORD='REPLACE_WITH_SUPABASE_PASSWORD'
```

Staging:

```bash
kubectl apply -f deploy/k8s/overlays/staging/namespaces.yaml

kubectl -n cloudflare-staging create secret generic cloudflared-token \
  --from-literal=token='REPLACE_WITH_STAGING_CLOUDFLARE_TUNNEL_TOKEN'

kubectl -n kuhhandel-staging create secret generic kuhhandel-db \
  --from-literal=SPRING_DATASOURCE_URL='REPLACE_WITH_STAGING_SUPABASE_JDBC_URL' \
  --from-literal=SPRING_DATASOURCE_USERNAME='REPLACE_WITH_STAGING_SUPABASE_USER' \
  --from-literal=SPRING_DATASOURCE_PASSWORD='REPLACE_WITH_STAGING_SUPABASE_PASSWORD'
```

## Deployment

Production:

```bash
kubectl apply -k deploy/k8s/overlays/production
```

Für Staging:

```bash
kubectl apply -k deploy/k8s/overlays/staging
```

## Cloudflare Hostnames

Production zeigt im Production-Tunnel auf:

```text
Public hostname: k3s-api.se-aau.com
Service type:    HTTP
Service URL:     kuhhandel-server.kuhhandel.svc.cluster.local:8080
```

Staging bekommt einen eigenen Tunnel und einen eigenen Public Hostname:

```text
Public hostname: staging-k3s-api.se-aau.com
Service type:    HTTP
Service URL:     kuhhandel-server.kuhhandel-staging.svc.cluster.local:8080
```

Der Pfad bleibt in beiden Fällen leer. Der Staging-Tunnel-Token gehört in das
Secret `cloudflared-token` im Namespace `cloudflare-staging`.

## Prüfen

```bash
kubectl -n kuhhandel get pods -o wide
kubectl -n cloudflare get pods -o wide
kubectl -n kuhhandel rollout status deployment/kuhhandel-server
kubectl -n cloudflare rollout status deployment/cloudflared
curl https://k3s-api.se-aau.com/health

kubectl -n kuhhandel-staging get pods -o wide
kubectl -n cloudflare-staging get pods -o wide
kubectl -n kuhhandel-staging rollout status deployment/kuhhandel-server
kubectl -n cloudflare-staging rollout status deployment/cloudflared
curl https://staging-k3s-api.se-aau.com/health
```

Erwartung:

```text
{"status":"UP"}
```
