package io.github.rahulbsw.protoweft.examples.tour.steps;

import io.github.rahulbsw.protoweft.core.ComplianceReport;
import io.github.rahulbsw.protoweft.examples.tour.capture.WireCapture;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.TreeSet;

/**
 * Runs one step at a time and returns what it did together with the wire traffic it caused.
 * One at a time is what makes the traffic attributable: everything captured during the run is
 * the step's (fine for a single-user demo).
 */
@Component
public class TourRunner {

    /** A compliance report as the page shows it. */
    public record Compliance(String protocol, String sdk, List<String> wireRevisions, String negotiated,
                             List<String> supported, List<String> unsupported) {
        static Compliance of(ComplianceReport r) {
            return new Compliance(name(r.protocol().toString()), r.sdkName() + " " + r.sdkVersion(), r.wireRevisions(),
                    r.negotiatedRevision(), List.copyOf(new TreeSet<>(r.supported())),
                    List.copyOf(new TreeSet<>(r.unsupported())));
        }

        private static String name(String id) {
            return id.equalsIgnoreCase("agui") ? "AG-UI" : id.toUpperCase(java.util.Locale.ROOT);
        }
    }

    /** The outcome of one ▶ Run. */
    public record Run(String stepId, boolean ok, String summary, Object data, String error,
                      List<WireCapture.Exchange> exchanges, List<Compliance> compliance) {
    }

    private final TourSteps steps;
    private final WireCapture capture;

    public TourRunner(TourSteps steps, WireCapture capture) {
        this.steps = steps;
        this.capture = capture;
    }

    public synchronized Run run(TourSteps.Step step) throws InterruptedException {
        capture.awaitIdle(2_000);
        long mark = capture.mark();
        TourSteps.Result result = null;
        String error = null;
        try {
            result = step.body().apply(steps.baseUrl());
        } catch (RuntimeException e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        capture.awaitIdle(2_000);   // late callbacks and closing requests belong to this step too
        List<WireCapture.Exchange> exchanges = capture.since(mark);
        if (result == null) {
            return new Run(step.id(), false, "The step failed: " + error, null, error, exchanges, List.of());
        }
        return new Run(step.id(), true, result.summary(), result.data(), null, exchanges,
                result.compliance().stream().map(Compliance::of).toList());
    }
}
