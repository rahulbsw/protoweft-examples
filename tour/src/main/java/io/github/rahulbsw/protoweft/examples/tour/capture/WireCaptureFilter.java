package io.github.rahulbsw.protoweft.examples.tour.capture;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records the HTTP traffic of the protocol endpoints for the tour's wire panel. Request bodies are
 * cached as the servlet reads them; response bodies are copied as they are written, without
 * buffering, so streamed responses (SSE) still reach the client chunk by chunk.
 */
public class WireCaptureFilter extends OncePerRequestFilter {

    private static final List<String> REQUEST_HEADERS = List.of("content-type", "accept", "mcp-session-id",
            "mcp-protocol-version", "a2a-version", "last-event-id");
    private static final List<String> RESPONSE_HEADERS = List.of("content-type", "mcp-session-id", "location");

    private final WireCapture capture;

    public WireCaptureFilter(WireCapture capture) {
        this.capture = capture;
    }

    /** The protocol an endpoint path belongs to, or null if it isn't a protocol endpoint. */
    static String protocol(String path) {
        if (path.equals("/mcp") || path.startsWith("/mcp/")) {
            return "MCP";
        }
        if (path.equals("/utcp") || path.startsWith("/utcp/")) {
            return "UTCP";
        }
        if (path.equals("/a2a") || path.startsWith("/a2a/") || path.equals("/.well-known/agent-card.json")) {
            return "A2A";
        }
        if (path.equals("/rap") || path.startsWith("/rap/")) {
            return "RAP";
        }
        if (path.equals("/agui") || path.startsWith("/agui/")) {
            return "AG-UI";
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return protocol(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        String accept = String.valueOf(request.getHeader("accept"));
        // MCP's optional GET stream stays open for server notifications: shown, but never waited for.
        boolean longLived = request.getMethod().equals("GET") && accept.contains("text/event-stream");
        WireCapture.Recording recording = capture.begin(protocol(request.getRequestURI()), request.getMethod(), path,
                headers(REQUEST_HEADERS, request::getHeader), longLived);
        TeeResponse res = new TeeResponse(response);
        // Servlets that go async write through AsyncContext.getResponse(): keep the copying wrapper there too.
        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request, WireCapture.BODY_LIMIT) {
            @Override
            public AsyncContext startAsync() {
                return startAsync(this, res);
            }
        };
        try {
            chain.doFilter(req, res);
        } finally {
            recording.requestBody(new String(req.getContentAsByteArray(), charset(request.getCharacterEncoding())));
            if (request.isAsyncStarted()) {
                recording.response(res.getStatus(), headers(RESPONSE_HEADERS, res::getHeader), res);
                request.getAsyncContext().addListener(new AsyncListener() {
                    @Override
                    public void onComplete(AsyncEvent event) {
                        done(recording, res);
                    }

                    @Override
                    public void onTimeout(AsyncEvent event) {
                        done(recording, res);
                    }

                    @Override
                    public void onError(AsyncEvent event) {
                        done(recording, res);
                    }

                    @Override
                    public void onStartAsync(AsyncEvent event) {
                    }
                });
            } else {
                done(recording, res);
            }
        }
    }

    private void done(WireCapture.Recording recording, TeeResponse res) {
        recording.response(res.getStatus(), headers(RESPONSE_HEADERS, res::getHeader), res);
        if (capture.isLongLived(recording)) {
            capture.finishLongLived(recording);
        } else {
            recording.finish();
        }
    }

    private static Map<String, String> headers(List<String> names, java.util.function.Function<String, String> get) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : names) {
            String value = get.apply(name);
            if (value != null) {
                out.put(name, value);
            }
        }
        return out;
    }

    private static Charset charset(String name) {
        try {
            return name == null ? StandardCharsets.UTF_8 : Charset.forName(name);
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }

    /** Copies everything written to the response into a bounded buffer, passing it through unchanged. */
    static final class TeeResponse extends HttpServletResponseWrapper implements WireCapture.Body {
        private final ByteArrayOutputStream copy = new ByteArrayOutputStream();
        private ServletOutputStream stream;
        private PrintWriter writer;

        TeeResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public synchronized String text() {
            return copy.toString(StandardCharsets.UTF_8);   // the protocols are JSON or SSE, UTF-8; the response may be recycled by now
        }

        private synchronized void keep(byte[] b, int off, int len) {
            int room = WireCapture.BODY_LIMIT - copy.size();
            if (room > 0) {
                copy.write(b, off, Math.min(room, len));
            }
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (stream == null) {
                ServletOutputStream out = super.getOutputStream();
                stream = new ServletOutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        out.write(b);
                        keep(new byte[] {(byte) b}, 0, 1);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        out.write(b, off, len);
                        keep(b, off, len);
                    }

                    @Override
                    public void flush() throws IOException {
                        out.flush();
                    }

                    @Override
                    public boolean isReady() {
                        return out.isReady();
                    }

                    @Override
                    public void setWriteListener(WriteListener listener) {
                        out.setWriteListener(listener);
                    }
                };
            }
            return stream;
        }

        /** Writes go straight to the container's writer (no extra buffering); a UTF-8 copy is kept. */
        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                PrintWriter out = super.getWriter();
                writer = new PrintWriter(new Writer() {
                    @Override
                    public void write(char[] c, int off, int len) {
                        out.write(c, off, len);
                        byte[] b = new String(c, off, len).getBytes(StandardCharsets.UTF_8);
                        keep(b, 0, b.length);
                    }

                    @Override
                    public void flush() {
                        out.flush();
                    }

                    @Override
                    public void close() {
                        out.close();
                    }
                });
            }
            return writer;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Registration {
        @Bean
        FilterRegistrationBean<WireCaptureFilter> wireCaptureFilter(WireCapture capture) {
            FilterRegistrationBean<WireCaptureFilter> bean = new FilterRegistrationBean<>(new WireCaptureFilter(capture));
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
            bean.setAsyncSupported(true);
            return bean;
        }
    }
}
