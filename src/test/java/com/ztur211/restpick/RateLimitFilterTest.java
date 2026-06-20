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
