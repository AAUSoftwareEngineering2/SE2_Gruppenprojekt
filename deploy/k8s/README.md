# Kuhhandel Kubernetes Setup

Dieses Verzeichnis beschreibt den Kubernetes-Zustand für den Kuhhandel-Server.
Echte Secrets gehören nicht ins Repo.

## Architektur

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

## Ordnerstruktur

```text
base/
  Gemeinsame Kubernetes-Dateien.

overlays/production/
  Production-Deployment. Verwendet aktuell direkt `base`.

overlays/staging/
  Platzhalter für späteres Staging. Enthält aktuell noch keine Manifeste.
```

## Wichtige Dateien

```text
base/namespace.yaml
  Erstellt die Bereiche `kuhhandel` und `cloudflare`.

base/kustomization.yaml
  Sammelt die gemeinsamen Dateien für die Overlays.

base/kuhhandel-server-deployment.yaml
  Startet die Spring-Boot-Server-Pods.

base/kuhhandel-server-service.yaml
  Gibt den Server-Pods eine stabile interne Adresse.

base/kuhhandel-server-pdb.yaml
  Verhindert bei Wartung, dass alle Server-Pods gleichzeitig freiwillig weg sind.

base/cloudflared-deployment.yaml
  Startet den Cloudflare Tunnel im Cluster.

base/cloudflared-pdb.yaml
  Verhindert bei Wartung, dass alle Tunnel-Pods gleichzeitig freiwillig weg sind.

base/supabase-ca-configmap.yaml
  Stellt das öffentliche Supabase-CA-Zertifikat bereit.

base/secret-*.example.yaml
  Nur Vorlagen. Nicht direkt deployen.
```

## Secrets Anlegen

```bash
kubectl apply -f deploy/k8s/base/namespace.yaml

kubectl -n cloudflare create secret generic cloudflared-token \
  --from-literal=token='REPLACE_WITH_CLOUDFLARE_TUNNEL_TOKEN'

kubectl -n kuhhandel create secret generic kuhhandel-db \
  --from-literal=SPRING_DATASOURCE_URL='REPLACE_WITH_SUPABASE_JDBC_URL' \
  --from-literal=SPRING_DATASOURCE_USERNAME='REPLACE_WITH_SUPABASE_USER' \
  --from-literal=SPRING_DATASOURCE_PASSWORD='REPLACE_WITH_SUPABASE_PASSWORD'
```

## Deployment

```bash
kubectl apply -k deploy/k8s/overlays/production
```

## Prüfen

```bash
kubectl -n kuhhandel get pods -o wide
kubectl -n cloudflare get pods -o wide
kubectl -n kuhhandel rollout status deployment/kuhhandel-server
kubectl -n cloudflare rollout status deployment/cloudflared
curl https://k3s-api.se-aau.com/health
```

Erwartung:

```text
{"status":"UP"}
```
