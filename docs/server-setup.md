# Server-Side Setup Notes

Notes for the GCE host that fronts the `kuhhandel-server` container. Most of this
config lives outside the repo (Caddy, Docker host) — this file documents what is
there and how to evolve it if WebSocket stability becomes an issue again.

## Reverse proxy: Caddy

Caddy runs natively on the host (not in a container), terminates TLS via
Let's Encrypt, and reverse-proxies to the local container ports. The current
`/etc/caddy/Caddyfile` is intentionally minimal:

```caddyfile
api.se-aau.com {
    reverse_proxy 127.0.0.1:18080
}

staging-api.se-aau.com {
    reverse_proxy 127.0.0.1:18081
}
```

Caddy handles the `Upgrade: websocket` handshake transparently via the default
`reverse_proxy`, so no extra directives are required for WebSocket support.

### Optional hardening for long-lived WebSockets

If sporadic `Connection reset` / `Failed to connect` reports come back,
the following extra directives make Caddy more tolerant of slow Cloudflare-edge
paths. Apply them only if needed — they are not required for normal operation.

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

## Network path

```
Android client (OkHttp pingInterval 20s)
  → Cloudflare edge (Anycast IPv4/IPv6, free tier — ~100s WebSocket idle timeout)
  → Caddy on GCE :443 (Let's Encrypt termination)
  → Spring Boot container (127.0.0.1:18080 prod, 127.0.0.1:18081 staging)
  → Postgres container on the shared `kuhhandel-backend` docker network
```

## Heartbeats / idle handling

Server-side keepalive is configured in code, not on the proxy:

- `WebSocketHeartbeat` sends a WebSocket ping to every open session every 25 s.
  This both keeps the Cloudflare edge from idling the connection out and lets the
  server detect dead sessions sooner (sends that throw `IOException` evict the
  session from `ConnectionRegistry`).
- `WebSocketConfig.servletServerContainerFactoryBean` raises Tomcat's per-session
  idle timeout to 5 minutes, so the heartbeat has time to fire before Tomcat itself
  would close an "idle" socket.
- `application.yml` `server.tomcat.*` settings keep HTTP keep-alive longer than
  the edge idle window.

The Android client (`NetworkClientFactory`) already pings every 20 s via OkHttp,
so the path is symmetric.

## Memory caps

Both compose files cap JVM heap (`-Xmx`) and Docker `mem_limit`. See
[`deploy/compose.production.yaml`](../deploy/compose.production.yaml) and
[`deploy/compose.staging.yaml`](../deploy/compose.staging.yaml).
