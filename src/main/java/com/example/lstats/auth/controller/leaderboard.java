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

    @PostConstruct
    public void init() {
        hashOps = redisTemplate.opsForHash();
    }

    private Map<String, Object> fetchWithRetry(String url, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                return restTemplate.getForObject(url, Map.class);
            } catch (Exception e) {
                System.out.println("Attempt " + (i + 1) + " failed for " + url + " -> " + e.getMessage());
                try {
                    Thread.sleep(3000); 
                } catch (InterruptedException ignored) {
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

        for (User user : users) {
            try {
                String url = "https://lstats.onrender.com/leetcode/" + user.getUsername();
                Map<String, Object> res = fetchWithRetry(url, 3);

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

        if (newData.size() == users.size()) {
            leadercache.clear();
            leadercache.putAll(newData);

            redisTemplate.delete(CACHE_KEY);

            hashOps.putAll(CACHE_KEY, newData);
            System.out.println(" Leaderboard fully refreshed and saved to Redis (" + users.size() + " users)");
        } else {
            System.out.println(" Partial refresh skipped — got " + newData.size() + "/" + users.size() + " users");
        }
    }

    @GetMapping("/refresh")
    @CacheEvict(value = { "globalLeaderboard", "collegeLeaderboard" }, allEntries = true)
    public ResponseEntity<String> manualRefresh() {
        refreshleaderboard();
        return ResponseEntity.ok("Leaderboard refresh triggered ");
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
            Map<String, Object> res = restTemplate.getForObject(url, Map.class);

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
