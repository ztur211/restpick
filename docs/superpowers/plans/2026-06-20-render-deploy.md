# Render Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make restpick a Dockerized, platform-ready jar and add the Render config so it deploys to a free Render web service at `https://restpick.onrender.com` with GitHub push-to-deploy.

**Architecture:** Switch packaging `war`→`jar` (embedded Tomcat) so the app runs standalone, bind the platform's `$PORT`, read the Google key from a `GOOGLE_PLACES_API_KEY` env var (local `.env` fallback), and add a `Dockerfile` (JDK 25 base) + `render.yaml` blueprint so Render builds and runs the container.

**Tech Stack:** Java 25, Spring Boot 4.0.5, Maven (`./mvnw`), Docker (built by Render), Render free web service.

## Global Constraints

- Java 25. Build env every shell (state does not persist):
  ```bash
  cd /workspace && export JAVA_HOME=/home/node/.local/jdk-25.0.3+9 && export PATH="$JAVA_HOME/bin:$PATH"
  ```
- `mvnw` is now executable (mode 100755); `./mvnw` works.
- All **40 tests** must stay green.
- Build artifact: `target/restpick-0.0.1-SNAPSHOT.jar` (reference as `target/*.jar`).
- Prod key env var: `GOOGLE_PLACES_API_KEY`; local fallback: `.env` `key=`.
- Port: bind `$PORT` (default 8080).
- No Docker in the sandbox — the jar is verified locally; the `Dockerfile` is validated by inspection + Render's build.

---

### Task 1: Platform-ready jar (war→jar, $PORT, key env var)

**Files:**
- Modify: `pom.xml` (packaging + tomcat scope)
- Delete: `src/main/java/com/ztur211/restpick/ServletInitializer.java`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Produces: an executable `target/*.jar` that starts embedded Tomcat, binds `$PORT`, and reads the key from `GOOGLE_PLACES_API_KEY` (or local `.env` `key=`).

- [ ] **Step 1: Switch packaging to jar**

In `pom.xml`, change:
```xml
	<packaging>war</packaging>
```
to:
```xml
	<packaging>jar</packaging>
```

- [ ] **Step 2: Bundle Tomcat in the jar (un-provide it)**

In `pom.xml`, change the Tomcat dependency from:
```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-tomcat</artifactId>
			<scope>provided</scope>
		</dependency>
```
to (remove the `<scope>` line):
```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-tomcat</artifactId>
		</dependency>
```

- [ ] **Step 3: Delete the war-only ServletInitializer**

```bash
git rm src/main/java/com/ztur211/restpick/ServletInitializer.java
```

- [ ] **Step 4: Bind $PORT and read the key env var**

In `src/main/resources/application.properties`:
- Add a line: `server.port=${PORT:8080}`
- Change `google.places.api.key=${key}` to `google.places.api.key=${GOOGLE_PLACES_API_KEY:${key:}}`

Resulting file:
```properties
spring.application.name=restpick
spring.thymeleaf.cache=false
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html
spring.config.import=optional:file:.env[.properties]
server.port=${PORT:8080}
google.places.api.key=${GOOGLE_PLACES_API_KEY:${key:}}
google.places.api.url=https://maps.googleapis.com
google.places.api.region=us
google.places.api.language=en
```

- [ ] **Step 5: Run the full test suite — expect 40 green**

```bash
cd /workspace && export JAVA_HOME=/home/node/.local/jdk-25.0.3+9 && export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B test 2>&1 | grep -E "Tests run:.*Skipped, Time|BUILD" | tail -2
```
Expected: `Tests run: 40, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 6: Build the jar**

```bash
cd /workspace && export JAVA_HOME=/home/node/.local/jdk-25.0.3+9 && export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B clean package 2>&1 | grep -E "BUILD|Building jar" | tail -3
ls -1 target/*.jar
```
Expected: `BUILD SUCCESS`; `target/restpick-0.0.1-SNAPSHOT.jar` exists.

- [ ] **Step 7: Run the jar standalone on a custom $PORT — confirm it boots and binds**

```bash
cd /workspace && export JAVA_HOME=/home/node/.local/jdk-25.0.3+9 && export PATH="$JAVA_HOME/bin:$PATH"
PORT=9099 GOOGLE_PLACES_API_KEY=dummy-jar-test java -jar target/*.jar > /tmp/jar-boot.log 2>&1 &
curl -s --retry 40 --retry-delay 2 --retry-connrefused --retry-all-errors -o /dev/null -w "health(9099): %{http_code}\n" http://127.0.0.1:9099/actuator/health
curl -s -o /dev/null -w "homepage(9099): %{http_code}\n" http://127.0.0.1:9099/
grep -E "Tomcat started on port|Started RestpickApplication" /tmp/jar-boot.log | tail -2
pkill -9 -f "[d]ummy-jar-test"
```
Expected: `health(9099): 200`, `homepage(9099): 200`, `Tomcat started on port 9099`. Proves the jar is standalone (embedded Tomcat) and binds `$PORT`.

- [ ] **Step 8: Commit**

```bash
git add pom.xml src/main/resources/application.properties
git commit -m "Make app a standalone jar that binds \$PORT and reads GOOGLE_PLACES_API_KEY"
```
(The `git rm` from Step 3 is already staged.)

---

### Task 2: Container + Render config (Dockerfile, render.yaml, .dockerignore)

**Files:**
- Create: `Dockerfile`
- Create: `render.yaml`
- Create: `.dockerignore`

**Interfaces:**
- Consumes: the executable `target/*.jar` from Task 1.
- Produces: repo config that Render reads to build the image and run the service with a `GOOGLE_PLACES_API_KEY` secret and `/actuator/health` health check.

- [ ] **Step 1: Confirm the runtime base image tag exists**

```bash
curl -s "https://hub.docker.com/v2/repositories/library/eclipse-temurin/tags/?name=25-jre&page_size=5" | grep -oE '"name":"25-jre[^"]*"' | head -3
```
Expected: a `25-jre` tag listed. If empty, use `eclipse-temurin:25-jdk` for the runtime stage in Step 2 instead.

- [ ] **Step 2: Create the Dockerfile**

Create `Dockerfile`:
```dockerfile
# --- build stage ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw -B clean package

# --- runtime stage ---
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```
(If Step 1 showed no `25-jre` tag, use `eclipse-temurin:25-jdk` on the runtime `FROM` line.)

- [ ] **Step 3: Create render.yaml**

Create `render.yaml`:
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

- [ ] **Step 4: Create .dockerignore**

Create `.dockerignore`:
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

- [ ] **Step 5: Validate the config files**

```bash
cd /workspace
echo "=== render.yaml parses as valid YAML? ==="
python3 -c "import yaml,sys; d=yaml.safe_load(open('render.yaml')); print('OK', d['services'][0]['name'], d['services'][0]['plan'], d['services'][0]['healthCheckPath'])"
echo "=== .dockerignore does NOT exclude build inputs (.mvn, mvnw, pom.xml, src) ==="
for f in .mvn mvnw pom.xml src; do
  git check-ignore -q "$f" --no-index 2>/dev/null && echo "WARN excluded: $f" || echo "kept: $f"
done
echo "=== Dockerfile references the jar Task 1 produces ==="
grep -q "target/\*.jar" Dockerfile && echo "OK jar path matches" || echo "WARN jar path mismatch"
```
Expected: render.yaml prints `OK restpick free /actuator/health`; all four build inputs print `kept:`; `OK jar path matches`.

> Note: the Docker image itself is **not** built here (no Docker in the sandbox). Render performs the real build on first deploy; the jar it produces was already proven runnable in Task 1.

- [ ] **Step 6: Commit**

```bash
git add Dockerfile render.yaml .dockerignore
git commit -m "Add Dockerfile, render.yaml, and .dockerignore for Render deploy"
```

---

## Self-Review

- **Spec coverage:** war→jar + tomcat scope + delete ServletInitializer → Task 1 Steps 1-3. `$PORT` binding → Task 1 Step 4. Key env var → Task 1 Step 4. Dockerfile → Task 2 Step 2. render.yaml (health check, secret env) → Task 2 Step 3. .dockerignore → Task 2 Step 4. 40 tests green → Task 1 Step 5. jar boots + binds $PORT → Task 1 Step 7. JRE tag hedge → Task 2 Step 1. ✓ Manual Google-bill backstop + Render click-through are user steps delivered at handoff (not repo tasks), per spec. ✓
- **Placeholder scan:** none — all file contents and commands are concrete.
- **Type/name consistency:** `GOOGLE_PLACES_API_KEY` used identically in application.properties (Task 1) and render.yaml (Task 2); `target/*.jar` consistent across Task 1 Step 6/7 and Dockerfile; health path `/actuator/health` consistent in render.yaml and verification. ✓
