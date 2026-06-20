# Design: Deploy restpick to Render (Dockerized, free tier)

- **Date:** 2026-06-20
- **Status:** Approved (pending spec review)
- **Component:** `com.ztur211.restpick` + repo build/deploy config

## Problem / goal

restpick runs locally but isn't public. We want a public HTTPS URL
(`https://restpick.onrender.com`) with push-to-deploy from GitHub, on a free
tier, while keeping the Google API bill bounded (rate limiter already in place).
The blocker is **Java 25**: most host buildpacks don't support it yet, so we
containerize to guarantee the runtime.

## Approach

Containerize the app with Docker (JDK 25 base) and run it as a **Render free web
service** that builds from the repo `Dockerfile` and auto-redeploys on every push
to `main`. Render terminates HTTPS and provides the `restpick.onrender.com`
subdomain free.

## Repo changes

### 1. `war` → `jar` (pom.xml)

The app is currently `war` packaging with `spring-boot-starter-tomcat` at
`provided` scope — built for deployment into an *external* Tomcat. For a
container we want a self-contained executable jar with embedded Tomcat.

- Change `<packaging>war</packaging>` → `<packaging>jar</packaging>`.
- Change `spring-boot-starter-tomcat` from `provided` scope to **default**
  (remove the `<scope>provided</scope>` line) so embedded Tomcat is bundled in
  the jar. (Removing the dependency entirely would rely on
  `spring-boot-starter-webmvc` bringing Tomcat transitively at compile scope —
  not certain in Boot 4; flipping the scope is the safe, unambiguous change.)
- Delete `src/main/java/com/ztur211/restpick/ServletInitializer.java` — it only
  exists to support war deployment and is unused for a jar.

Verify after the change that the jar boots (`java -jar target/*.jar` starts
Tomcat) and all 40 tests pass.

All 40 tests must stay green after this change (they're unit tests + one
`@SpringBootTest`; none depend on war packaging).

### 2. Bind Render's port + key as env var (application.properties)

- Add `server.port=${PORT:8080}` — Render injects `PORT`; default 8080 locally.
- Change `google.places.api.key=${key}` →
  `google.places.api.key=${GOOGLE_PLACES_API_KEY:${key:}}`.
  Prod reads the `GOOGLE_PLACES_API_KEY` env var; falls back to the local `.env`
  `key=` for dev; defaults to empty (so a missing key no longer crashes startup —
  the app boots and only Google calls fail).

### 3. `Dockerfile` (repo root, multi-stage)

```dockerfile
# --- build ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw -B clean package

# --- run ---
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- Build uses the project's `./mvnw` (now mode 100755, so `RUN ./mvnw` works; the
  exec bit is preserved through `COPY`). `clean package` runs the 40 tests as a
  deploy gate.
- Runtime uses a slim JRE 25 image. If the `eclipse-temurin:25-jre` tag is not
  published, fall back to `eclipse-temurin:25-jdk` (verified during impl).
- `target/*.jar` is version-agnostic (artifact is `restpick-0.0.1-SNAPSHOT.jar`).

### 4. `render.yaml` (repo root — Blueprint)

```yaml
services:
  - type: web
    name: restpick
    runtime: docker
    plan: free
    healthCheckPath: /actuator/health
    envVars:
      - key: GOOGLE_PLACES_API_KEY
        sync: false
```

- `runtime: docker` → Render builds from the `Dockerfile`.
- `healthCheckPath: /actuator/health` reuses the existing actuator endpoint
  (returns 200 when up).
- `sync: false` → Render prompts for the key on first deploy; it is **never**
  committed.

### 5. `.dockerignore` (repo root)

```
.git
target
.env
.env.*
docs
.idea
.vscode
*.iml
```

Keeps the local `.env` (and its dummy/real key), build output, and git history
out of the image. `.mvn/`, `mvnw`, `pom.xml`, `src/` are kept (needed to build).

## Production behavior

- **Per-IP limiting behind the proxy:** Render forwards the client IP in
  `X-Forwarded-For`, which `RateLimitFilter.clientIp()` already reads. Note the
  first-hop value is client-spoofable behind a proxy, so per-IP remains
  *best-effort fairness* — the **global daily cap** (not spoofable) plus the
  Google budget alert are the real bill backstops. (Consistent with the original
  rate-limit design.)
- **Single instance:** Render free tier runs one instance with no autoscaling,
  so the in-memory global daily cap stays meaningful (the design's single-instance
  assumption holds).
- **Cold start:** free tier sleeps after ~15 min idle; first request after sleep
  takes ~30–60s. URL is permanent.

## Google Cloud bill backstop (manual, outside the repo)

The non-spoofable wallet protection:
- Restrict the API key to **Places API (New)** + **Maps Static API** only.
- Set a **budget alert** (~$5–10) on the Cloud project.
- Set a **daily quota cap** on the Places API.

Application (IP) restriction on the key isn't practical on the free tier
(dynamic egress IPs), so API restriction + the rate limiter + budget alert are
the layered defense.

## Deploy flow (user-run, on Render)

1. New → Blueprint → connect the `ztur211/restpick` repo.
2. Render reads `render.yaml`; paste the Google key into the prompted
   `GOOGLE_PLACES_API_KEY` secret.
3. Render builds the Docker image and deploys → `https://restpick.onrender.com`.
4. Every push to `main` auto-redeploys.

Custom domain (later): add it in Render + a DNS record — no code change.

## Verification

- **Local (sandbox, no Docker):** `./mvnw clean package` (JDK 25) produces
  `target/*.jar`; run `PORT=9000 java -jar target/*.jar` with a dummy key and
  confirm it binds 9000 and `/actuator/health` → 200. Validates the jar
  packaging + `$PORT` binding without Docker.
- **Tests:** 40 green after the pom change.
- **Dockerfile:** validated by Render on first deploy (no Docker in sandbox).
- **Post-deploy:** WebFetch the live URL health + homepage; run the rate-limit
  check against the public URL.

## Out of scope (YAGNI)

- Custom domain (later, no-code).
- Distributed/cross-instance rate limiting (single instance).
- Thymeleaf production caching, log-verbosity cleanup.
- CI beyond Render's own build.
