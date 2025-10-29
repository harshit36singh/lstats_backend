package com.example.lstats.auth.controller;

import java.util.*;
import java.util.concurrent.*;

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
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/leaderboard")
@CrossOrigin(origins = "*")
public class leaderboard {

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
public static class Leader {
    int totalSolved;
    String img;
    int e;
    int m;
    int h;
    String clgname;

    public Leader(int e, int m, int h, String img, String clgname) {
        this.e = e;
        this.m = m;
        this.h = h;
        this.img = img;
        this.clgname = clgname;
    }

    public int getpoints() {
        return e * 1 + m * 3 + h * 5;
    }

    public int gettotalsolved() {
        return e + m + h;
    }
}

@Autowired
private UserRepository userRepository;

@Autowired
private RedisTemplate<String, Object> redisTemplate;

private HashOperations<String, String, Leader> hashOps;
private final RestTemplate restTemplate = new RestTemplate();
private static final String CACHE_KEY = "leaderboard";
private final Map<String, Leader> leadercache = new ConcurrentHashMap<>();

// Configurable constants
private static final int MAX_RETRIES = 3;
private static final int BASE_WAIT_MS = 5000;
private static final int BATCH_SIZE = 10;

private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

@PostConstruct
public void init() {
    hashOps = redisTemplate.opsForHash();
    scheduler.scheduleAtFixedRate(this::refreshBatch, 0, 10, TimeUnit.MINUTES);
    scheduler.scheduleAtFixedRate(this::keepAlivePing, 0, 9, TimeUnit.MINUTES);
    scheduler.submit(this::refreshleaderboard);
}

private void keepAlivePing() {
    try {
        restTemplate.getForObject("https://lstats.onrender.com/health", String.class);
        System.out.println("Keep-alive ping sent to lstats.onrender.com");
    } catch (Exception e) {
        System.out.println("Keep-alive ping failed: " + e.getMessage());
    }
}

private Map<String, Object> fetchWithRetry(String url, int maxRetries) {
    Random random = new Random();
    for (int i = 0; i < maxRetries; i++) {
        try {
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            int waitTime = BASE_WAIT_MS * (i + 1) + random.nextInt(2000);
            System.out.println((e.getMessage().contains("429") ? "429 Too Many Requests" : "Error") +
                    " for " + url + ", waiting " + waitTime / 1000 + "s before retry...");
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException ignored) {}
        }
    }
    return null;
}

@Scheduled(fixedRate = 3600000)
@CacheEvict(value = {"globalLeaderboard", "collegeLeaderboard"}, allEntries = true)
void refreshleaderboard() {
    List<User> users = userRepository.findAll();
    Map<String, Leader> newData = new HashMap<>();

    for (User user : users) {
        try {
            String url = "https://lstats.onrender.com/leetcode/" + user.getUsername();
            Map<String, Object> res = fetchWithRetry(url, MAX_RETRIES);

            if (res != null && res.containsKey("easySolved") && res.containsKey("mediumSolved")
                    && res.containsKey("hardSolved") && res.containsKey("profilePic")) {

                int ea = ((Number) res.get("easySolved")).intValue();
                int me = ((Number) res.get("mediumSolved")).intValue();
                int ha = ((Number) res.get("hardSolved")).intValue();
                String image = (String) res.get("profilePic");

                Leader leader = new Leader(ea, me, ha, image, user.getCollegename());
                newData.put(user.getUsername(), leader);
            } else {
                System.out.println("Invalid data for " + user.getUsername());
            }

        } catch (Exception e) {
            System.out.println(" Error fetching for " + user.getUsername() + ": " + e.getMessage());
        }
    }

    if (!newData.isEmpty()) {
        leadercache.putAll(newData);
        hashOps.putAll(CACHE_KEY, newData);
        System.out.println(" Leaderboard updated for " + newData.size() + " users");
    }
}

private void refreshBatch() {
    List<User> users = userRepository.findAll();
    if (users.isEmpty()) return;

    int totalBatches = Math.max(1, (users.size() + BATCH_SIZE - 1) / BATCH_SIZE);
    int startIndex = (int) (System.currentTimeMillis() / (10 * 60 * 1000)) % totalBatches;
    int from = startIndex * BATCH_SIZE;
    int to = Math.min(users.size(), from + BATCH_SIZE);

    List<User> batch = users.subList(from, to);
    System.out.println("Refreshing leaderboard batch: " + from + " - " + to);

    for (User user : batch) {
        updateUserLeaderboard(user.getUsername());
    }
}

@GetMapping("/refresh")
@CacheEvict(value = {"globalLeaderboard", "collegeLeaderboard"}, allEntries = true)
public ResponseEntity<String> manualRefresh() {
    scheduler.submit(this::refreshleaderboard);
    return ResponseEntity.ok("Leaderboard refresh triggered");
}

@GetMapping("/global")
@Cacheable("globalLeaderboard")
public List<Map<String, Object>> globalleaberboard() {
    Map<String, Leader> leaders = hashOps.entries(CACHE_KEY);
    if (leaders == null || leaders.isEmpty()) {
        leaders = new HashMap<>(leadercache);
    }

    List<Map<String, Object>> list = new ArrayList<>();
    for (Map.Entry<String, Leader> entry : leaders.entrySet()) {
        String username = entry.getKey();
        Leader l = entry.getValue();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            Map<String, Object> e = new HashMap<>();
            e.put("id", user.getId());
            e.put("username", username);
            e.put("solved", l.gettotalsolved());
            e.put("avatar", l.img);
            e.put("points", l.getpoints());
            e.put("collgename", l.clgname);
            list.add(e);
        }
    }

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
    if (leaders == null || leaders.isEmpty()) {
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
        Map<String, Object> res = fetchWithRetry(url, MAX_RETRIES);

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
        System.out.println("Error updating leaderboard for " + username + ": " + e.getMessage());
    }
}


}
