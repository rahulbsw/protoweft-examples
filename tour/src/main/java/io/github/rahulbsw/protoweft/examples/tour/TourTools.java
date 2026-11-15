package io.github.rahulbsw.protoweft.examples.tour;

import io.github.rahulbsw.protoweft.core.tool.AgentTool;
import io.github.rahulbsw.protoweft.core.tool.ToolParam;
import io.github.rahulbsw.protoweft.rap.RapCallContext;
import io.github.rahulbsw.protoweft.rap.RapSubscription;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

/**
 * The tools of the tour. Plain methods: the Protoweft starter serves them over MCP, UTCP and RAP,
 * and the agent uses them over A2A and AG-UI. The weather is made up and the same on every run, so
 * the tour (and its recordings) never change.
 */
@Component
public class TourTools {

    // tour:begin tools
    @AgentTool(description = "Current temperature in a city, in degrees Celsius", readOnly = true)
    public double temperature(@ToolParam(description = "City name, e.g. Oslo") String city) {
        return celsius(city, 0);
    }

    public record Day(int day, double celsius, String sky) {
    }

    @AgentTool(description = "Weather forecast for the next days", readOnly = true)
    public List<Day> forecast(@ToolParam(description = "City name") String city,
                              @ToolParam(description = "Number of days, 1 to 5") int days) {
        return IntStream.range(0, Math.max(1, Math.min(days, 5)))
                .mapToObj(d -> new Day(d, celsius(city, d), SKIES[(city.length() + d) % SKIES.length]))
                .toList();
    }
    // tour:end

    // tour:begin updates
    /** A RAP subscription: answers at once, then keeps sending events after the call returned. */
    @AgentTool(description = "Streams weather updates for a city (RAP subscription)", readOnly = true)
    public String updates(@ToolParam(description = "City name") String city, RapCallContext rap) {
        if (!rap.available()) {
            return "Live updates for " + city + " need a RAP subscription";
        }
        RapSubscription subscription = rap.subscribe();
        Thread producer = new Thread(() -> {
            subscription.emit(city + ": " + celsius(city, 0) + " °C, " + SKIES[0]);
            subscription.emit(city + ": " + (celsius(city, 0) - 2.5) + " °C, showers moving in");
            subscription.close(city + ": that's all for today", false);
        }, "weather-updates");
        producer.setDaemon(true);
        producer.start();
        return "Watching " + city;
    }
    // tour:end

    private static final String[] SKIES = {"sunny", "cloudy", "showers", "windy"};

    /** A stable, made-up temperature: 12 to 24 °C depending on the city name and the day. */
    static double celsius(String city, int day) {
        int seed = Math.abs(city.trim().toLowerCase().hashCode());
        return 12 + (seed + day * 3) % 25 / 2.0;
    }
}
