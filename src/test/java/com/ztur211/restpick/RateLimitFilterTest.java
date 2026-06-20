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

    @Test
    void rejectionResponse_declaresUtf8() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1000); // 1/min per IP

        // First request consumes the only token.
        filter.doFilter(req("/pick", "5.5.5.5"), new MockHttpServletResponse(), new CountingChain());

        // Second request is rejected — its body must be served as UTF-8.
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req("/pick", "5.5.5.5"), res, new CountingChain());

        assertEquals(429, res.getStatus());
        assertEquals("UTF-8", res.getCharacterEncoding());
        assertTrue(res.getContentType().toLowerCase().contains("utf-8"),
                "Content-Type should declare charset=UTF-8, was: " + res.getContentType());
    }

    // Behind Cloudflare+Render the per-request proxy IP rotates, but CF-Connecting-IP
    // is the stable real client. The bucket must key on CF-Connecting-IP.
    @Test
    void keysOnCfConnectingIp_despiteRotatingForwardedForAndRemoteAddr() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1000); // 1/min per IP

        MockHttpServletRequest r1 = new MockHttpServletRequest();
        r1.setServletPath("/pick");
        r1.setRemoteAddr("10.0.0.1");                       // rotating proxy address
        r1.addHeader("X-Forwarded-For", "172.16.0.1");      // rotating proxy hop
        r1.addHeader("CF-Connecting-IP", "203.0.113.7");    // stable real client
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilter(r1, res1, new CountingChain());
        assertEquals(200, res1.getStatus());

        MockHttpServletRequest r2 = new MockHttpServletRequest();
        r2.setServletPath("/pick");
        r2.setRemoteAddr("10.0.0.2");                       // different proxy address
        r2.addHeader("X-Forwarded-For", "172.16.0.2");      // different proxy hop
        r2.addHeader("CF-Connecting-IP", "203.0.113.7");    // SAME real client
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(r2, res2, new CountingChain());
        assertEquals(429, res2.getStatus(),
                "same CF-Connecting-IP must share one bucket despite different X-Forwarded-For/remoteAddr");
    }

    // On Render, CF-Connecting-IP / X-Forwarded-For are a rotating proxy IP; the real
    // client is in True-Client-IP, which must take precedence.
    @Test
    void prefersTrueClientIp_overCfConnectingIpAndForwardedFor() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1000); // 1/min per IP

        MockHttpServletRequest r1 = new MockHttpServletRequest();
        r1.setServletPath("/pick");
        r1.setRemoteAddr("10.0.0.1");
        r1.addHeader("X-Forwarded-For", "172.16.0.1");      // rotating proxy
        r1.addHeader("CF-Connecting-IP", "172.16.0.1");     // rotating proxy on Render
        r1.addHeader("True-Client-IP", "203.0.113.7");      // stable real client
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilter(r1, res1, new CountingChain());
        assertEquals(200, res1.getStatus());

        MockHttpServletRequest r2 = new MockHttpServletRequest();
        r2.setServletPath("/pick");
        r2.setRemoteAddr("10.0.0.2");
        r2.addHeader("X-Forwarded-For", "172.16.0.2");      // different proxy
        r2.addHeader("CF-Connecting-IP", "172.16.0.2");     // different proxy
        r2.addHeader("True-Client-IP", "203.0.113.7");      // SAME real client
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(r2, res2, new CountingChain());
        assertEquals(429, res2.getStatus(),
                "same True-Client-IP must share one bucket even as CF-Connecting-IP/X-Forwarded-For rotate");
    }
}
