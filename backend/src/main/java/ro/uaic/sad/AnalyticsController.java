//AnalyticsController.java
package ro.uaic.sad;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnalyticsController {
    private final AnalyticsService s;
    public AnalyticsController(AnalyticsService s) { this.s = s; }

    @GetMapping("/filters") public Map<String, Object> filters() { return s.filters(); }
    @GetMapping("/overview") public Map<String, Object> overview(@RequestParam Map<String, String> p) { return s.overview(Filters.of(p)); }

    @GetMapping("/trend")
    public Map<String, Object> trend(@RequestParam Map<String, String> p, @RequestParam(defaultValue = "6") int horizon) { return s.trend(Filters.of(p), horizon); }

    @GetMapping("/drill")
    public Map<String, Object> drill(@RequestParam Map<String, String> p, @RequestParam(defaultValue = "category") String level,
                                     @RequestParam(required = false) String parent, @RequestParam(defaultValue = "3") double good, @RequestParam(defaultValue = "-5") double bad) {
        return s.drill(Filters.of(p), level, parent, good, bad);
    }

    @GetMapping("/geo")
    public Map<String, Object> geo(@RequestParam Map<String, String> p, @RequestParam(defaultValue = "province") String level, @RequestParam(required = false) String parent) {
        return s.geo(Filters.of(p), level, parent);
    }

    @GetMapping("/segments") public Map<String, Object> segments(@RequestParam Map<String, String> p, @RequestParam(defaultValue = "gender") String by) { return s.segments(Filters.of(p), by); }
    @GetMapping("/peaks") public Map<String, Object> peaks(@RequestParam Map<String, String> p) { return s.peaks(Filters.of(p)); }
    @GetMapping("/events") public List<Map<String, Object>> events(@RequestParam Map<String, String> p) { return s.events(Filters.of(p)); }
    @GetMapping("/promo/history") public List<Map<String, Object>> promoHistory() { return s.promoHistory(); }

    @GetMapping("/promo/simulate")
    public Map<String, Object> simulate(@RequestParam Map<String, String> p, @RequestParam(defaultValue = "ALL") String sub, @RequestParam(defaultValue = "10") double discount) {
        return s.simulate(Filters.of(p), sub, discount);
    }

    @GetMapping("/supply") public Map<String, Object> supply(@RequestParam Map<String, String> p, @RequestParam(defaultValue = "3") int horizon) { return s.supply(Filters.of(p), horizon); }

    @GetMapping("/decision")
    public Map<String, Object> decision(@RequestParam Map<String, String> p, @RequestParam(defaultValue = "35") double wg, @RequestParam(defaultValue = "35") double wm,
                                        @RequestParam(defaultValue = "15") double ws, @RequestParam(defaultValue = "15") double wmo) {
        return s.decision(Filters.of(p), wg, wm, ws, wmo);
    }

    @GetMapping("/alerts") public List<Map<String, Object>> alerts(@RequestParam Map<String, String> p) { return s.alerts(Filters.of(p)); }
}
