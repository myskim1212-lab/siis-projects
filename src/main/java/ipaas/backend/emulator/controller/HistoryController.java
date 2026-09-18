package ipaas.backend.emulator.controller;

import ipaas.backend.emulator.model.RequestLog;
import ipaas.backend.emulator.service.RequestHistoryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/history")
public class HistoryController {

    private final RequestHistoryService historyService;

    public HistoryController(RequestHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public List<RequestLog> getHistory(@RequestParam(required = false) Integer limit) {
        if (limit != null && limit > 0) {
            return historyService.getRecent(limit);
        }
        return historyService.getAll();
    }

    @DeleteMapping
    public Map<String, Object> clearHistory() {
        int total = historyService.getTotalCount();
        historyService.clear();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("result", "cleared");
        result.put("total_requests", total);
        return result;
    }
}
