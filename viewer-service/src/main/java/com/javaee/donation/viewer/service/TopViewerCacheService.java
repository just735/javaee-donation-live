package com.javaee.donation.viewer.service;

import com.javaee.donation.common.model.TopViewerResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TopViewerCacheService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public List<TopViewerResponse> get(String streamerId, Integer limit) {
        String key = streamerId + ":" + limit;
        CacheEntry entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.data;
    }

    public void put(String streamerId, Integer limit, List<TopViewerResponse> data) {
        String key = streamerId + ":" + limit;
        cache.put(key, new CacheEntry(data, System.currentTimeMillis() + CACHE_TTL.toMillis()));
    }

    private static class CacheEntry {

        private final List<TopViewerResponse> data;
        private final long expireAt;

        private CacheEntry(List<TopViewerResponse> data, long expireAt) {
            this.data = data;
            this.expireAt = expireAt;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
