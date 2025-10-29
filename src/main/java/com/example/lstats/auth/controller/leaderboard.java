package com.example.lstats.auth.controller;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import com.example.lstats.model.User;
import com.example.lstats.repository.UserRepository;

import jakarta.annotation.PostConstruct;

class Leader {
    int totalSolved;
    String img;
    int e;
    int m;
    int h;
    String clgname;

    Leader(int e, int m, int h, String img, String clgname) {
        this.e = e;
        this.m = m;
        this.h = h;
        this.img = img;
        this.clgname = clgname;
    }

    int getpoints() {
        return e * 1 + m * 3 + h * 5;
    }

    int gettotalsolved() {
        return e + m + h;
    }

}

@RestController
@RequestMapping("/leaderboard")
@CrossOrigin(origins = "*")
public class leaderboard {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private HashOperations<String, String, Leader> hashOps;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String CACHE_KEY = "leaderboard";
    private final Map<String, Leader> leadercache = new ConcurrentHashMap<>();
    
    // Rate limiting variables
    private static final long MIN_REQUEST_INTERVAL = 20000; // 20 seconds between requests
    private static final long COLD_START_WAIT = 180000; // 3 minutes for cold start
    private long lastRequestTime = 0;
    private int consecutiveRateLimitHits = 0;
    private static final int BATCH_SIZE = 3; // Process 3 users before taking a break
    private static final long BATCH_PAUSE = 45000; // 45 seconds pause after each batch
    private boolean serviceWarmupDone = false;

    @PostConstruct
    public void init() {
        hashOps = redisTemplate.opsForHash();
    }

    private boolean warmupService() {
        System.out.println("🔥 Attempting to warm up lstats.onrender.com service (this may take 2-3 minutes)...");
        
        // Try to ping the service with a simple request
        for (int i = 0; i < 5; i++) {
            try {
                String testUrl = "https://lstats.onrender.com/";
                restTemplate.getForObject(testUrl, String.class);
                System.out.println("✓ Service warmup successful!");
                return true;
            } catch (Exception e) {
                System.out.println("Warmup attempt " + (i + 1) + "/5 - waiting 30s...");
                try {
                    Thread.sleep(30000); // Wait 30 seconds between warmup attempts
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        System.out.println("⚠ Service may still be cold, proceeding with caution...");
        return false;
    }

    private synchronized Map<String, Object> fetchWithRateLimit(String url, int maxRetries) {
        // Ensure minimum time between requests
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;
        
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
            try {
                long sleepTime = MIN_REQUEST_INTERVAL - timeSinceLastRequest;
                System.out.println("⏱ Rate limiting: waiting " + (sleepTime / 1000) + "s before next request...");
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        Map<String, Object> result = fetchWithRetry(url, maxRetries);
        lastRequestTime = System.currentTimeMillis();
        return result;
    }

    private Map<String, Object> fetchWithRetry(String url, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                Map<String, Object> result = restTemplate.getForObject(url, Map.class);
                consecutiveRateLimitHits = 0; // Reset on success
                return result;
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                consecutiveRateLimitHits++;
                
                // If this is the first request and we get 429, the service is likely cold
                if (!serviceWarmupDone && i == 0) {
                    System.out.println("⚠ Service appears to be spun down (cold start). Waiting " + (COLD_START_WAIT / 1000) + "s for warmup...");
                    try {
                        Thread.sleep(COLD_START_WAIT);
                        serviceWarmupDone = true;
                        continue; // Retry immediately after cold start wait
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                // Progressive backoff for subsequent retries
                int waitTime = Math.min(120000, (i + 1) * 20000 + (consecutiveRateLimitHits * 15000));
                System.out.println("429 Too Many Requests for " + url + ", waiting " + (waitTime / 1000) + "s before retry...");
                System.out.println("Consecutive rate limit hits: " + consecutiveRateLimitHits);
                
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            } catch (org.springframework.web.client.HttpServerErrorException e) {
                System.out.println("⚠ Server error (likely still spinning up): " + e.getStatusCode() + " - waiting 60s...");
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                System.out.println("Attempt " + (i + 1) + " failed for " + url + " -> " + e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return null;
    }

    @Scheduled(fixedRate = 3600000)
    @CacheEvict(value = { "globalLeaderboard", "collegeLeaderboard" }, allEntries = true)
    void refreshleaderboard() {
        List<User> users = userRepository.findAll();
        Map<String, Leader> newData = new HashMap<>();
        
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("Starting leaderboard refresh for " + users.size() + " users...");
        System.out.println("═══════════════════════════════════════════════════════");
        
        // Warm up the service first
        if (!serviceWarmupDone) {
            warmupService();
            serviceWarmupDone = true;
            
            // Additional wait after warmup
            System.out.println("⏱ Waiting additional 30s to ensure service is fully ready...");
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            
            try {
                String url = "https://lstats.onrender.com/leetcode/" + user.getUsername();
                System.out.println("\n[" + (i + 1) + "/" + users.size() + "] Fetching: " + user.getUsername());
                
                Map<String, Object> res = fetchWithRateLimit(url, 4); // Increased to 4 retries

                if (res != null && res.containsKey("easySolved") && res.containsKey("mediumSolved")
                        && res.containsKey("hardSolved") && res.containsKey("profilePic")) {

                    int ea = ((Number) res.get("easySolved")).intValue();
                    int me = ((Number) res.get("mediumSolved")).intValue();
                    int ha = ((Number) res.get("hardSolved")).intValue();
                    String image = (String) res.get("profilePic");

                    Leader leader = new Leader(ea, me, ha, image, user.getCollegename());
                    newData.put(user.getUsername(), leader);
                    successCount++;
                    System.out.println("✓ Success! E:" + ea + " M:" + me + " H:" + ha);
                } else {
                    failCount++;
                    System.out.println("✗ Invalid/Empty response for " + user.getUsername());
                }
                
                // Extra pause after each batch
                if ((i + 1) % BATCH_SIZE == 0 && i < users.size() - 1) {
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                    System.out.println("\n═══ Batch " + ((i + 1) / BATCH_SIZE) + " complete (" + (i + 1) + "/" + users.size() + " users) ═══");
                    System.out.println("    Success: " + successCount + " | Failed: " + failCount + " | Time: " + elapsed + "s");
                    System.out.println("    Pausing for " + (BATCH_PAUSE / 1000) + " seconds...\n");
                    Thread.sleep(BATCH_PAUSE);
                }
                
            } catch (Exception e) {
                failCount++;
                System.out.println("✗ Error fetching for " + user.getUsername() + ": " + e.getMessage());
            }
        }

        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;
        
        System.out.println("\n═══════════════════════════════════════════════════════");
        System.out.println("Refresh completed in " + (duration / 60) + "m " + (duration % 60) + "s");
        System.out.println("Results: " + successCount + " successful, " + failCount + " failed");
        System.out.println("═══════════════════════════════════════════════════════\n");

        // Accept partial data if we got at least 50% of users
        double successRate = (double) newData.size() / users.size();
        if (newData.size() == users.size()) {
            leadercache.clear();
            leadercache.putAll(newData);
            redisTemplate.delete(CACHE_KEY);
            hashOps.putAll(CACHE_KEY, newData);
            System.out.println("✓ Leaderboard FULLY refreshed and saved to Redis (" + users.size() + " users)");
        } else if (successRate >= 0.5) {
            // Merge with existing data instead of replacing
            Map<String, Leader> existingData = new HashMap<>(leadercache);
            existingData.putAll(newData);
            leadercache.clear();
            leadercache.putAll(existingData);
            
            hashOps.putAll(CACHE_KEY, newData); // Update only new entries
            System.out.println("⚠ PARTIAL refresh saved (" + newData.size() + "/" + users.size() + " users updated)");
        } else {
            System.out.println("✗ Refresh FAILED - only got " + newData.size() + "/" + users.size() + " users (" + (int)(successRate * 100) + "%)");
            System.out.println("⚠ Keeping old data. Try again later or check API status.");
        }
    }

    @GetMapping("/refresh")
    @CacheEvict(value = { "globalLeaderboard", "collegeLeaderboard" }, allEntries = true)
    public ResponseEntity<Map<String, String>> manualRefresh() {
        new Thread(() -> refreshleaderboard()).start();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "Leaderboard refresh running in background. This may take 5-10 minutes.");
        response.put("note", "Check server logs for progress updates.");
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/global")
    @Cacheable("globalLeaderboard")
    public List<Map<String, Object>> globalleaberboard() {
        Map<String, Leader> leaders = hashOps.entries(CACHE_KEY);

        if (leaders.isEmpty()) {
            leaders = new HashMap<>(leadercache);
        }

        List<Map<String, Object>> list = new ArrayList<>();
        leaders.forEach((username, entry) -> {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                Map<String, Object> e = new HashMap<>();
                e.put("id", user.getId());
                e.put("username", username);
                e.put("solved", entry.gettotalsolved());
                e.put("avatar", entry.img);
                e.put("points", entry.getpoints());
                e.put("collgename", entry.clgname);
                list.add(e);
            }
        });

        list.sort((a, b) -> ((Integer) b.get("points")) - ((Integer) a.get("points")));
        for (int i = 0; i < list.size(); i++) {
            list.get(i).put("rank", i + 1);
        }

        return list;
    }

    @GetMapping("/colleges")
    @Cacheable("collegeLeaderboard")
    public List<Map<String, Object>> clgleaderboard() {
        Map<String, Leader> leaders = hashOps.entries(CACHE_KEY);
        if (leaders.isEmpty()) {
            leaders = new HashMap<>(leadercache);
        }

        Map<String, Integer> collegpoints = new HashMap<>();
        leaders.forEach((username, entry) -> {
            if (entry.clgname != null && !entry.clgname.isEmpty()) {
                collegpoints.merge(entry.clgname, entry.getpoints(), Integer::sum);
            }
        });

        List<Map<String, Object>> list = new ArrayList<>();
        collegpoints.forEach((college, points) -> {
            Map<String, Object> e = new HashMap<>();
            e.put("college", college);
            e.put("points", points);
            list.add(e);
        });

        list.sort((a, b) -> ((Integer) b.get("points")) - ((Integer) a.get("points")));
        for (int i = 0; i < list.size(); i++) {
            list.get(i).put("rank", i + 1);
        }

        return list;
    }

    public void updateUserLeaderboard(String username) {
        try {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null) {
                System.out.println("No user found with username: " + username);
                return;
            }

            String url = "https://lstats.onrender.com/leetcode/" + username;
            Map<String, Object> res = fetchWithRateLimit(url, 4);

            if (res != null && res.containsKey("easySolved") && res.containsKey("mediumSolved")
                    && res.containsKey("hardSolved") && res.containsKey("profilePic")) {

                int ea = ((Number) res.get("easySolved")).intValue();
                int me = ((Number) res.get("mediumSolved")).intValue();
                int ha = ((Number) res.get("hardSolved")).intValue();
                String image = (String) res.get("profilePic");

                Leader leader = new Leader(ea, me, ha, image, user.getCollegename());

                leadercache.put(username, leader);
                hashOps.put(CACHE_KEY, username, leader);

                System.out.println("✓ Leaderboard updated for user: " + username);
            }

        } catch (Exception e) {
            System.out.println("✗ Error updating leaderboard for " + username + ": " + e.getMessage());
        }
    }

}