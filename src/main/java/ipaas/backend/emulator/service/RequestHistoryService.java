package ipaas.backend.emulator.service;

import ipaas.backend.emulator.model.RequestLog;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RequestHistoryService {

    private static final int MAX_SIZE = 100;

    private final LinkedList<RequestLog> history = new LinkedList<RequestLog>();
    private final AtomicInteger counter = new AtomicInteger(0);

    public int nextCount() {
        return counter.incrementAndGet();
    }

    public int getTotalCount() {
        return counter.get();
    }

    public synchronized void add(RequestLog log) {
        if (history.size() >= MAX_SIZE) {
            history.removeFirst();
        }
        history.addLast(log);
    }

    public synchronized List<RequestLog> getAll() {
        return new ArrayList<RequestLog>(history);
    }

    public synchronized List<RequestLog> getRecent(int limit) {
        List<RequestLog> all = new ArrayList<RequestLog>(history);
        int size = all.size();
        return all.subList(Math.max(0, size - limit), size);
    }

    public synchronized void clear() {
        history.clear();
    }
}
