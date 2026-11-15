package io.github.rahulbsw.protoweft.examples.tour.capture;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The protocol traffic the tour shows: every HTTP exchange on a protocol endpoint, in the order
 * the requests arrived. Kept in memory, last {@value #CAPACITY} only.
 */
@Component
public class WireCapture {

    static final int CAPACITY = 500;
    static final int BODY_LIMIT = 256 * 1024;

    /** One request and its response, as shown in the tour's wire panel. */
    public record Exchange(long seq, String protocol, String direction, String method, String path, int status,
                           Map<String, String> requestHeaders, String requestBody,
                           Map<String, String> responseHeaders, String responseBody,
                           long durationMs, boolean complete) {
    }

    /** An exchange being recorded; the filter fills it in. */
    final class Recording {
        private final long seq;
        private final long startNanos = System.nanoTime();
        private final String protocol;
        private final String method;
        private final String path;
        private final Map<String, String> requestHeaders;
        private final boolean longLived;
        private volatile String requestBody = "";
        private volatile int status;
        private volatile Map<String, String> responseHeaders = Map.of();
        private volatile Body responseBody;
        private volatile long durationMs = -1;

        private Recording(long seq, String protocol, String method, String path, Map<String, String> requestHeaders,
                          boolean longLived) {
            this.seq = seq;
            this.protocol = protocol;
            this.method = method;
            this.path = path;
            this.requestHeaders = requestHeaders;
            this.longLived = longLived;
        }

        void requestBody(String body) {
            this.requestBody = body;
        }

        void response(int status, Map<String, String> headers, Body body) {
            this.status = status;
            this.responseHeaders = headers;
            this.responseBody = body;
        }

        void finish() {
            durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            synchronized (WireCapture.this) {
                inFlight--;
                WireCapture.this.notifyAll();
            }
        }

        Exchange view() {
            boolean complete = durationMs >= 0;
            return new Exchange(seq, protocol, path.startsWith("/rap/callback") ? "callback" : "request", method,
                    path, status, requestHeaders, requestBody, responseHeaders,
                    responseBody == null ? "" : responseBody.text(), complete ? durationMs : 0, complete);
        }
    }

    /** A response body as captured so far (it grows while a stream is open). */
    interface Body {
        String text();
    }

    private final AtomicLong seq = new AtomicLong();
    private final Deque<Recording> recordings = new ArrayDeque<>();
    private int inFlight;

    Recording begin(String protocol, String method, String path, Map<String, String> headers, boolean longLived) {
        Recording r = new Recording(seq.incrementAndGet(), protocol, method, path, new LinkedHashMap<>(headers),
                longLived);
        synchronized (this) {
            if (!longLived) {
                inFlight++;
            }
            recordings.addLast(r);
            if (recordings.size() > CAPACITY) {
                recordings.removeFirst();
            }
        }
        return r;
    }

    void finishLongLived(Recording r) {
        r.durationMs = (System.nanoTime() - r.startNanos) / 1_000_000;
        synchronized (this) {
            notifyAll();
        }
    }

    /** The sequence number of the latest exchange; exchanges after it are newer. */
    public long mark() {
        return seq.get();
    }

    /** Exchanges that started after {@code since}, oldest first. */
    public synchronized List<Exchange> since(long since) {
        return recordings.stream().filter(r -> r.seq > since).map(Recording::view).toList();
    }

    /**
     * Waits until no request is in flight (callbacks and streams included, except long-lived
     * notification streams), or until the timeout.
     */
    public synchronized void awaitIdle(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (inFlight > 0) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                return;
            }
            wait(left);
        }
    }

    /**
     * Waits until every exchange after {@code since} has finished, long-lived streams included
     * (a step closes its clients, which ends their streams), or until the timeout.
     */
    public synchronized void awaitFinished(long since, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (recordings.stream().anyMatch(r -> r.seq > since && r.durationMs < 0)) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                return;
            }
            wait(left);
        }
    }

    boolean isLongLived(Recording r) {
        return r.longLived;
    }
}
