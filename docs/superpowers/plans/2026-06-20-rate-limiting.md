# API Rate Limiting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-IP throttling plus a global daily cap to restpick's Google-spending endpoints so the app can go public without bill-runaway risk.

**Architecture:** A single `RateLimitFilter` (`OncePerRequestFilter`, auto-registered as a `@Component`) runs before the controllers. For the five Google-spending endpoints it consumes one token from the caller's per-IP Bucket4j bucket and one from a shared global daily bucket; if either is empty it returns `429` and the request never reaches the controller. Per-IP buckets live in a bounded Caffeine cache. Task 0 first repairs a pre-existing red test suite (okhttp/mockwebserver version conflict) so we build on green.

**Tech Stack:** Java 25, Spring Boot 4.0.5 (WAR), Bucket4j `8.14.0` (`bucket4j_jdk17-core`), Caffeine `3.2.0`, JUnit 5, Spring `MockHttpServletRequest/Response`.

## Global Constraints

- Java version: **25** (`pom.xml` `<java.version>25</java.version>`). Build JDK: `/home/node/.local/jdk-25.0.3+9`.
- Build/test command (env must be set every shell — state does not persist):
  ```bash
  cd /workspace && export JAVA_HOME=/home/node/.local/jdk-25.0.3+9 && export PATH="$JAVA_HOME/bin:$PATH"
  ```
- Package: `com.ztur211.restpick`.
- Test style: plain JUnit 5 (no `@SpringBootTest`, no Spring context); instantiate the class under test directly. Match existing tests.
- Limits (Balanced preset, per spec): per-IP **60 / minute**; global **5,000 / day**.
- Limited paths (context-relative `getServletPath()`): `/autocomplete`, `/pick`, `/resolve-location`, `/map-image`, `/photo`.
- `429` response: header `Retry-After: 60`, content-type `application/json`, body `{"error":"Rate limit exceeded. Please slow down."}`.

---

### Task 0: Repair pre-existing test suite (okhttp/mockwebserver conflict)

**Why:** A clean `mvn test` currently fails with 17 `NoClassDefFound okhttp3/internal/Util` errors. `okhttp` is pinned to `5.0.0-alpha.14` (which dropped `okhttp3.internal.Util`) while `mockwebserver:4.12.0` (used by the service tests) needs it. `okhttp` is **unused in main code** (`grep` confirms), and the tests use the okhttp-4 `MockWebServer` API. Aligning okhttp to the stable `4.12.0` fixes all 17 with one version change and no test-code churn.

**Files:**
- Modify: `pom.xml` (the `okhttp` dependency version)

**Interfaces:**
- Consumes: nothing.
- Produces: a green baseline (`Tests run: 35, Failures: 0, Errors: 0`).

- [ ] **Step 1: Confirm the red baseline**

```bash
cd /workspace && export JAVA_HOME=/home/node/.local/jdk-25.0.3+9 && export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B test 2>&1 | grep -E "Tests run:|NoClassDefFound" | tail -5
```
Expected: `Tests run: 35, Failures: 0, Errors: 17` with `NoClassDefFound okhttp3/internal/Util`.

- [ ] **Step 2: Align okhttp to 4.12.0**

In `pom.xml`, change the okhttp dependency version from `5.0.0-alpha.14` to `4.12.0`:

```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>
```

- [ ] **Step 3: Run the full suite, expect green**

```bash
cd /workspace && export JAVA_HOME=/home/node/.local/jdk-25.0.3+9 && export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B test 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```
Expected: `Tests run: 35, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "Fix test suite: align okhttp to 4.12.0 to match mockwebserver"
```

---

### Task 1: RateLimitFilter (per-IP + global daily cap)

**Files:**
- Modify: `pom.xml` (add Bucket4j + Caffeine dependencies)
- Create: `src/main/java/com/ztur211/restpick/RateLimitFilter.java`
- Test: `src/test/java/com/ztur211/restpick/RateLimitFilterTest.java`

**Interfaces:**
- Consumes: green baseline from Task 0.
- Produces: `RateLimitFilter` — public no-arg constructor (production, 60/min + 5000/day); package-private `RateLimitFilter(int perIpPerMinute, long globalPerDay)` for tests. Extends `OncePerRequestFilter`; overrides `doFilterInternal`.

- [ ] **Step 1: Add dependencies to `pom.xml`**

Add inside `<dependencies>`:

```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j_jdk17-core</artifactId>
    <version>8.14.0</version>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.2.0</version>
</dependency>
```

Verify resolution:
```bash
cd /workspace && export JAVA_HOME=/home/node/.local/jdk-25.0.3+9 && export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B -q dependency:resolve 2>&1 | tail -3 && echo OK
```
Expected: no error, prints `OK`.

- [ ] **Step 2: Write the failing test**

Create `src/test/java/com/ztur211/restpick/RateLimitFilterTest.java`:

```java
package com.ztur211.restpick;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    // Minimal FilterChain that records how many times it was invoked.
    static class CountingChain implements FilterChain {
        int count = 0;
        public void doFilter(ServletRequest req, ServletResponse res) { count++; }
    }

    private MockHttpServletRequest req(String path, String ip) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setServletPath(path);
        r.setRemoteAddr(ip);
        return r;
    }

    @Test
    void perIp_blocksAfterCapacity() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 1000); // 2/min per IP

        for (int i = 0; i < 2; i++) {
            CountingChain chain = new CountingChain();
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req("/pick", "1.2.3.4"), res, chain);
            assertEquals(1, chain.count, "request " + i + " should pass through");
            assertEquals(200, res.getStatus());
        }

        CountingChain chain = new CountingChain();
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req("/pick", "1.2.3.4"), res, chain);
        assertEquals(0, chain.count, "3rd request must be blocked");
        assertEquals(429, res.getStatus());
        assertEquals("60", res.getHeader("Retry-After"));
        assertTrue(res.getContentAsString().contains("Rate limit exceeded"));
    }

    @Test
    void differentIps_areIndependent() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1000); // 1/min per IP

        MockHttpServletResponse a1 = new MockHttpServletResponse();
        filter.doFilter(req("/pick", "10.0.0.1"), a1, new CountingChain());
        assertEquals(200, a1.getStatus());

        MockHttpServletResponse a2 = new MockHttpServletResponse();
        filter.doFilter(req("/pick", "10.0.0.1"), a2, new CountingChain());
        assertEquals(429, a2.getStatus(), "same IP second request blocked");

        MockHttpServletResponse b1 = new MockHttpServletResponse();
        filter.doFilter(req("/pick", "10.0.0.2"), b1, new CountingChain());
        assertEquals(200, b1.getStatus(), "different IP unaffected");
    }

    @Test
    void globalCap_blocksRegardlessOfIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1000, 3); // big per-IP, global 3/day

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req("/photo", "172.16.0." + i), res, new CountingChain());
            assertEquals(200, res.getStatus(), "global request " + i + " allowed");
        }

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req("/photo", "172.16.0.99"), res, new CountingChain());
        assertEquals(429, res.getStatus(), "global cap exhausted");
    }

    @Test
    void nonLimitedPath_alwaysPasses() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1); // tiny limits

        for (int i = 0; i < 3; i++) {
            CountingChain chain = new CountingChain();
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req("/", "9.9.9.9"), res, chain);
            assertEquals(1, chain.count, "home page never throttled");
            assertEquals(200, res.getStatus());
        }
    }
}
```

- [ ] **Step 3: Run the test, verify it fails to compile (class missing)**

```bash
cd /workspace && export JAVA_HOME=/home/node/.local/jdk-25.0.3+9 && export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B test -Dtest=RateLimitFilterTest 2>&1 | grep -E "ERROR|BUILD" | tail -5
```
Expected: compilation failure — `RateLimitFilter` symbol not found.

- [ ] **Step 4: Implement `RateLimitFilter`**

Create `src/main/java/com/ztur211/restpick/RateLimitFilter.java`:

```java
package com.ztur211.restpick;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * Rate-limits the Google-spending endpoints: each client IP gets a per-minute
 * budget, and a single global bucket caps total daily calls so the Google bill
 * stays bounded even under distributed load. Other routes pass through.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/autocomplete", "/pick", "/resolve-location", "/map-image", "/photo");

    private final int perIpPerMinute;
    private final Bucket globalBucket;
    private final Cache<String, Bucket> perIpBuckets;

    /** Production limits: 60 requests/min per IP, 5,000 Google calls/day total. */
    public RateLimitFilter() {
        this(60, 5_000);
    }

    RateLimitFilter(int perIpPerMinute, long globalPerDay) {
        this.perIpPerMinute = perIpPerMinute;
        this.globalBucket = Bucket.builder()
                .addLimit(limit -> limit.capacity(globalPerDay).refillGreedy(globalPerDay, Duration.ofDays(1)))
                .build();
        this.perIpBuckets = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(Duration.ofHours(1))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!LIMITED_PATHS.contains(request.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }

        Bucket ipBucket = perIpBuckets.get(clientIp(request), key -> Bucket.builder()
                .addLimit(limit -> limit.capacity(perIpPerMinute).refillGreedy(perIpPerMinute, Duration.ofMinutes(1)))
                .build());

        if (ipBucket.tryConsume(1) && globalBucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Please slow down.\"}");
        }
    }

    /** Client IP: first hop of X-Forwarded-For when present, else the socket address. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

- [ ] **Step 5: Run the rate-limit tests, expect green**

```bash
cd /workspace && export JAVA_HOME=/home/node/.local/jdk-25.0.3+9 && export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B test -Dtest=RateLimitFilterTest 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```
Expected: `Tests run: 4, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 6: Run the FULL suite, expect all green**

```bash
cd /workspace && export JAVA_HOME=/home/node/.local/jdk-25.0.3+9 && export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B test 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```
Expected: `Tests run: 39, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/main/java/com/ztur211/restpick/RateLimitFilter.java src/test/java/com/ztur211/restpick/RateLimitFilterTest.java
git commit -m "Add API rate limiting (per-IP + global daily cap)"
```

---

## Self-Review

- **Spec coverage:** RateLimitFilter (§Design overview) → Task 1 Step 4. Limited endpoints (§) → `LIMITED_PATHS`. Limits (§) → no-arg constructor `(60, 5000)`. Client IP / X-Forwarded-For (§) → `clientIp()`. Memory safety / Caffeine (§) → `perIpBuckets`. Error response (§) → 429 branch. Testing (§ four cases) → four `@Test` methods. WAR context-path note (§) → `getServletPath()`. Dependencies (§) → Task 1 Step 1. ✓ All covered.
- **Placeholder scan:** none — all code and commands are concrete.
- **Type consistency:** `RateLimitFilter(int, long)` used identically in plan and tests; `doFilterInternal` signature matches `OncePerRequestFilter`; Bucket4j `io.github.bucket4j.Bucket` + lambda `addLimit` verified against 8.14.0. ✓
- **Bonus repair:** Task 0 fixes the pre-existing okhttp red suite so Task 1 Step 6's "39 green" is meaningful.
