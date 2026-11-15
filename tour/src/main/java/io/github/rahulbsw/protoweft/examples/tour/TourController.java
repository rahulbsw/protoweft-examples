package io.github.rahulbsw.protoweft.examples.tour;

import io.github.rahulbsw.protoweft.examples.tour.capture.WireCapture;
import io.github.rahulbsw.protoweft.examples.tour.steps.TourRunner;
import io.github.rahulbsw.protoweft.examples.tour.steps.TourSteps;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The page's API: what is running, run a step, and the traffic the page itself caused (AG-UI). */
@RestController
@RequestMapping("/tour/api")
public class TourController {

    private final TourSteps steps;
    private final TourRunner runner;
    private final WireCapture capture;
    private final TourModel model;

    public TourController(TourSteps steps, TourRunner runner, WireCapture capture, TourModel model) {
        this.steps = steps;
        this.runner = runner;
        this.capture = capture;
        this.model = model;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of("mode", "live", "model", model.describe(), "scripted", model.scripted(),
                "baseUrl", steps.baseUrl(),
                "steps", steps.all().stream().map(s -> Map.of("id", s.id(), "chapter", s.chapter(), "title", s.title()))
                        .toList());
    }

    @PostMapping("/steps/{id}/run")
    public ResponseEntity<TourRunner.Run> run(@PathVariable String id) throws InterruptedException {
        var step = steps.find(id);
        if (step.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(runner.run(step.get()));
    }

    /** The latest exchange number, then the exchanges after a given one (the page polls during AG-UI runs). */
    @GetMapping("/exchanges")
    public Map<String, Object> exchanges(@RequestParam(defaultValue = "-1") long since) {
        List<WireCapture.Exchange> list = since < 0 ? List.of() : capture.since(since);
        return Map.of("mark", capture.mark(), "exchanges", list);
    }
}
