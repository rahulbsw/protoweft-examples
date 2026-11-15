package io.github.rahulbsw.protoweft.examples.tour;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 * The Protoweft Tour: one application serving the same tools over MCP, UTCP, A2A, RAP and AG-UI,
 * plus a browser tutorial (at {@code /}) that runs each protocol step and shows its wire traffic.
 */
@SpringBootApplication
public class TourApplication {

    private static final Logger log = LoggerFactory.getLogger(TourApplication.class);

    private final Environment environment;
    private final TourModel model;

    public TourApplication(Environment environment, TourModel model) {
        this.environment = environment;
        this.model = model;
    }

    public static void main(String[] args) {
        SpringApplication.run(TourApplication.class, args);
    }

    /** Says where to go: Gradle's bootRun keeps "EXECUTING" while the server runs, which looks stuck. */
    @EventListener(ApplicationReadyEvent.class)
    void announce() {
        String url = "http://localhost:" + environment.getProperty("local.server.port");
        String line = "─".repeat(58);
        log.info("\n┌{}┐\n│  Protoweft Tour is running → open {}{}│\n│  Model: {}{}│\n│  Press Ctrl+C to stop.{}│\n└{}┘",
                line, url, pad(35 + url.length()), model.describe(), pad(9 + model.describe().length()),
                pad(22), line);
    }

    private static String pad(int used) {
        return " ".repeat(Math.max(1, 58 - used));
    }
}
