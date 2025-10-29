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
    private static final long MIN_REQUEST_INTERVAL = 15000; // 15 seconds between requests
    private long lastRequestTime = 0;
    private int consecutiveRateLimitHits = 0;
    private static final int BATCH_SIZE = 5; // Process 5 users before taking a break
    private static final long BATCH_PAUSE = 30000; // 30 seconds pause after each batch

    @PostConstruct
    public void init() {
        hashOps = redisTemplate.opsForHash();
    }

    private synchronized Map<String, Object> fetchWithRateLimit(String url, int maxRetries) {
        // Ensure minimum time between requests
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;
        
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
            try {
                long sleepTime = MIN_REQUEST_INTERVAL - timeSinceLastRequest;
                System.out.println("Rate limiting: waiting " + (sleepTime / 1000) + "s before next request...");
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
                // Progressive backoff: increases with both retry attempt and consecutive hits
                int waitTime = (i + 1) * 5000 + (consecutiveRateLimitHits * 10000);
                System.out.println("429 Too Many Requests for " + url + ", waiting " + (waitTime / 1000) + "s before retry...");
                System.out.println("Consecutive rate limit hits: " + consecutiveRateLimitHits);
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                System.out.println("Attempt " + (i + 1) + " failed for " + url + " -> " + e.getMessage());
                try {
                    Thread.sleep(3000);
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
        
        System.out.println("Starting leaderboard refresh for " + users.size() + " users...");
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            
            try {
                String url = "https://lstats.onrender.com/leetcode/" + user.getUsername();
                System.out.println("Fetching data for user " + (i + 1) + "/" + users.size() + ": " + user.getUsername());
                
                Map<String, Object> res = fetchWithRateLimit(url, 3);

                if (res != null && res.containsKey("easySolved") && res.containsKey("mediumSolved")
                        && res.containsKey("hardSolved") && res.containsKey("profilePic")) {

                    int ea = ((Number) res.get("easySolved")).intValue();
                    int me = ((Number) res.get("mediumSolved")).intValue();
                    int ha = ((Number) res.get("hardSolved")).intValue();
                    String image = (String) res.get("profilePic");

                    Leader leader = new Leader(ea, me, ha, image, user.getCollegename());
                    newData.put(user.getUsername(), leader);
                    System.out.println("✓ Successfully fetched data for " + user.getUsername());
                } else {
                    System.out.println("✗ Invalid data for " + user.getUsername());
                }
                
                // Extra pause after each batch
                if ((i + 1) % BATCH_SIZE == 0 && i < users.size() - 1) {
                    System.out.println("═══ Batch " + ((i + 1) / BATCH_SIZE) + " complete (" + (i + 1) + "/" + users.size() + " users), pausing for " + (BATCH_PAUSE / 1000) + " seconds... ═══");
                    Thread.sleep(BATCH_PAUSE);
                }
                
            } catch (Exception e) {
                System.out.println("✗ Error fetching for " + user.getUsername() + ": " + e.getMessage());
            }
        }

        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;
        
        System.out.println("Refresh completed in " + duration + " seconds");
        System.out.println("Successfully fetched: " + newData.size() + "/" + users.size() + " users");

        if (newData.size() == users.size()) {
            leadercache.clear();
            leadercache.putAll(newData);

            redisTemplate.delete(CACHE_KEY);

            hashOps.putAll(CACHE_KEY, newData);
            System.out.println("✓ Leaderboard fully refreshed and saved to Redis (" + users.size() + " users)");
        } else {
            System.out.println("⚠ Partial refresh skipped — got " + newData.size() + "/" + users.size() + " users");
            System.out.println("⚠ Consider increasing delays or checking API rate limits");
        }
    }

    @GetMapping("/refresh")
    @CacheEvict(value = { "globalLeaderboard", "collegeLeaderboard" }, allEntries = true)
    public ResponseEntity<String> manualRefresh() {
        new Thread(() -> refreshleaderboard()).start(); // Run in background to avoid timeout
        return ResponseEntity.ok("Leaderboard refresh triggered (running in background)");
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
            Map<String, Object> res = fetchWithRateLimit(url, 3);

            if (res != null && res.containsKey("easySolved") && res.containsKey("mediumSolved")
                    && res.containsKey("hardSolved") && res.containsKey("profilePic")) {

                int ea = ((Number) res.get("easySolved")).intValue();
                int me = ((Number) res.get("mediumSolved")).intValue();
                int ha = ((Number) res.get("hardSolved")).intValue();
                String image = (String) res.get("profilePic");

                Leader leader = new Leader(ea, me, ha, image, user.getCollegename());

                leadercache.put(username, leader);
                hashOps.put(CACHE_KEY, username, leader);

                System.out.println(" Leaderboard updated for user: " + username);
            }

        } catch (Exception e) {
            System.out.println(" Error updating leaderboard for " + username + ": " + e.getMessage());
        }
    }

}