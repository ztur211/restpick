# Design: API Rate Limiting for restpick

- **Date:** 2026-06-20
- **Status:** Approved (pending spec review)
- **Component:** `com.ztur211.restpick`

## Problem

restpick is about to be made public. Several endpoints proxy directly to paid
Google APIs (Places Autocomplete, Nearby Search, Geocoding, Static Maps, Place
Photos). With no rate limiting, a single abuser or a runaway bot loop can drive
an unbounded Google bill. We need to cap abuse before exposing a public URL.

## Goals

- **Per-IP fairness:** no single client can spam the paid endpoints.
- **Global ceiling:** a hard daily cap on total Google-spending calls, so the
  bill is bounded even under distributed load.
- **Graceful failure:** rejected requests return `429` with a clear message;
  the existing frontend already handles error responses on these endpoints.
- **Minimal footprint:** one filter, two dependencies, one test file.

## Non-goals (YAGNI)

- Authentication / API keys / user accounts.
- Distributed or cross-instance limiting (the app is a single Tomcat instance).
- Per-endpoint limit tiers (see "Why one per-IP bucket" below).
- Externalized/tunable config via `@ConfigurationProperties` (constants suffice;
  trivial to externalize later if tuning-without-recompile is ever needed).

## Design overview

A single `RateLimitFilter extends OncePerRequestFilter`, auto-registered as a
`@Component`, runs before the controllers. For requests to a Google-spending
endpoint it consumes one token from the caller's per-IP bucket and one from a
shared global daily bucket. If either is exhausted it short-circuits with `429`
and the request never reaches the controller — so no Google call is made. All
other routes (`/`, static assets, actuator health) pass through untouched.

- **Token buckets:** [Bucket4j](https://bucket4j.com) (in-memory).
- **Per-IP storage:** [Caffeine](https://github.com/ben-manes/caffeine) cache,
  `IP -> Bucket`, with `maximumSize` + `expireAfterAccess` so idle IPs evict and
  memory stays bounded. An unbounded `Map<IP, Bucket>` would itself be a
  memory-exhaustion vector on a public URL; Caffeine closes that in ~3 lines.

## Limited endpoints

```
/autocomplete      POST   Places Autocomplete
/pick              POST   Nearby Search
/resolve-location  POST   Geocoding / Place details
/map-image         GET    Static Maps
/photo             GET    Place Photos
```

Matched against a `Set<String>` using the **context-relative** path
(`request.getServletPath()`, not `getRequestURI()`) — the app is packaged as a
WAR with a `ServletInitializer`, so `getRequestURI()` would include the context
path and break an exact match when deployed under a non-root context. The home
page and static assets are intentionally excluded (no Google spend).

## Limits (Balanced preset)

| Bucket          | Scope    | Capacity / Refill        |
|-----------------|----------|--------------------------|
| Per-IP          | each IP  | 60 tokens, +60 / minute  |
| Global (daily)  | all IPs  | 5,000 tokens, +5,000 / day |

### Why one per-IP bucket (not per-endpoint)

Frontend evidence (`src/main/resources/static/js/index.js`):

- **Autocomplete is already debounced** (300 ms) and only fires at >= 3 chars
  (`index.js:16,23`), so it cannot spam — a few calls while typing at most.
- **A single "Pick" fires a burst of media calls** — `showResult` renders a
  photo carousel with one `/photo` request *per photo* (up to ~10) plus one
  `/map-image`, all at once (`index.js:291,348`).

So the only real burst is a pick-with-photos (~10 calls). A single per-IP
bucket of 60/min absorbs several such picks plus typing per minute — generous
for a human, restrictive for a bot — without the complexity of per-endpoint
tiers.

## Client identification

Bucket key = client IP, read from the first hop of `X-Forwarded-For` when
present, falling back to `request.getRemoteAddr()`. This matters if the app is
ever fronted by a tunnel/proxy. `X-Forwarded-For` is spoofable, so per-IP
limiting is *fairness*, not hard security — the global daily cap is the
backstop against spoofed/distributed abuse.

## Error response

On exhaustion of either bucket:

- Status: `429 Too Many Requests`
- Header: `Retry-After: 60`
- Body: `{"error":"Rate limit exceeded. Please slow down."}` (`application/json`)

Consumption order: per-IP first, then global. If per-IP succeeds but global is
exhausted, one per-IP token is spent on a rejected request — immaterial at
these volumes and not worth a two-phase reservation.

## Dependencies (pom.xml)

- Bucket4j core (JDK 17+ artifact, compatible with the project's Java 25 /
  Spring Boot 4.0.5 baseline).
- Caffeine.

Exact coordinates/versions are pinned during implementation.

## Testing

One focused test class on the filter (matching the project's existing
per-class unit-test style), with Google-calling services mocked so no real API
calls occur:

1. **Per-IP limit trips:** the 61st request from one IP within a minute → `429`.
2. **IPs are independent:** a second IP is unaffected by the first IP's usage.
3. **Global cap trips:** once the global daily total is reached, further
   requests → `429` regardless of IP.
4. **Non-limited path passes:** `/` (or a static asset) is never throttled.

## Rollout notes

- No schema or API contract changes; purely additive.
- Limits are constants in the filter; adjust and rebuild to re-tune.
- After deploy, the global cap can be observed via logs / actuator if a counter
  is later exposed (out of scope here).
