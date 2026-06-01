# Server-Side Setup Notes

> **Status (as of Sprint 3):** Production and staging now run on the **3-node k3s
> cluster** — public access via **Cloudflare Tunnel**, database via **Supabase**.
> There are no open application ports on the nodes anymore.
>
> **Authoritative deployment docs:** [`deploy/k8s/README.md`](../deploy/k8s/README.md)
> (Kubernetes manifests) and the GitOps repo `SE2_Gruppenprojekt_Deploy` (Flux).
> This file only adds:
> 1. **App-level WebSocket / idle behaviour** — still current, independent of the host.
> 2. **Legacy single-host setup (GCE + Caddy + Docker Compose)** — historical
>    reference only, **no longer** the production path.

## Current network path (k3s)

```
Android client (OkHttp pingInterval 20s)
  → Cloudflare edge (Anycast IPv4/IPv6, free tier — ~100s WebSocket idle timeout)
  → Cloudflare Tunnel (cloudflared pods in k3s — no public app ports on the nodes)
  → Kubernetes Service  kuhhandel-server.kuhhandel.svc.cluster.local:8080
  → Spring Boot server pods
  → Supabase Postgres (sslmode=verify-full, session pooler :5432)
```

TLS is terminated at the Cloudflare edge (no longer Caddy / Let's Encrypt). The
public-hostname → service routes are managed in the Cloudflare dashboard; the
current list lives under "Cloudflare Hostnames" in
[`deploy/k8s/README.md`](../deploy/k8s/README.md). The public backend health
check is reachable at `https://k3s-api.se-aau.com/health`.

## Heartbeats / idle handling (current, app-level)

These settings live in the application code and apply on **any** host (k3s as well
as the old setup):

- `WebSocketHeartbeat` sends a WebSocket ping to every open session every 25 s.
  This both keeps the Cloudflare edge from idling the connection out and lets the
  server detect dead sessions sooner (sends that throw `IOException` evict the
  session from `ConnectionRegistry`).
- `WebSocketConfig.servletServerContainerFactoryBean` raises Tomcat's per-session
  idle timeout to 5 minutes, so the heartbeat has time to fire before Tomcat itself
  would close an "idle" socket.
- `application.yml` `server.tomcat.*` settings keep HTTP keep-alive longer than
  the edge idle window.
- The Android client (`NetworkClientFactory`) already pings every 20 s via OkHttp,
  so the path is symmetric.

## Memory caps (current, k8s)

The server heap and container memory are capped in the Kubernetes Deployment (no
longer via Compose `mem_limit`):

- Container: `resources.requests.memory: 384Mi`, `resources.limits.memory: 512Mi`,
  `resources.limits.cpu: 500m`
- JVM: `JAVA_TOOL_OPTIONS=-Xmx256m -XX:+UseSerialGC`

See [`deploy/k8s/base/app/deployment.yaml`](../deploy/k8s/base/app/deployment.yaml).

---

## Legacy: GCE host + Caddy + Docker Compose (historical)

> ⚠️ **No longer the production path.** Kept for reference / rollback only; the
> Compose files under `deploy/` are marked legacy. The active path is the k3s setup
> described above.

Notes for the old GCE host that fronted the `kuhhandel-server` container. Most of
this config lived outside the repo (Caddy, Docker host).

### Reverse proxy: Caddy

Caddy ran natively on the host (not in a container), terminated TLS via
Let's Encrypt, and reverse-proxied to the local container ports:

```caddyfile
api.se-aau.com {
    reverse_proxy 127.0.0.1:18080
}

staging-api.se-aau.com {
    reverse_proxy 127.0.0.1:18081
}
```

Caddy handled the `Upgrade: websocket` handshake transparently via the default
`reverse_proxy`, so no extra directives were required for WebSocket support.

#### Optional hardening for long-lived WebSockets

If sporadic `Connection reset` / `Failed to connect` reports came back, these
directives made Caddy more tolerant of slow Cloudflare-edge paths (apply only if
needed):

```caddyfile
staging-api.se-aau.com {
    reverse_proxy 127.0.0.1:18081 {
        transport http {
            keepalive 5m
            keepalive_idle_conns_per_host 4
        }
    }
}
```

After editing the Caddyfile, validate and reload (do **not** restart — that drops
in-flight requests):

```bash
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

### Old network path

```
Android client (OkHttp pingInterval 20s)
  → Cloudflare edge
  → Caddy on GCE :443 (Let's Encrypt termination)
  → Spring Boot container (127.0.0.1:18080 prod, 127.0.0.1:18081 staging)
  → Postgres container on the shared `kuhhandel-backend` docker network
```

### Old memory caps

Both Compose files capped JVM heap (`-Xmx`) and Docker `mem_limit` — see
[`deploy/compose.production.yaml`](../deploy/compose.production.yaml) and
[`deploy/compose.staging.yaml`](../deploy/compose.staging.yaml).
