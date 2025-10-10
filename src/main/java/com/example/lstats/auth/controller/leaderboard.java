package com.example.lstats.auth.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.example.lstats.repository.UserRepository;
import com.example.lstats.service.collegename;
import com.example.lstats.model.User;

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

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, Leader> leadercache = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 3600000)
    private void refreshleaderboard() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            try {
                String url = "https://lstats.onrender.com/leetcode/" + user.getUsername();
                Map<String, Object> res = restTemplate.getForObject(url, Map.class);
                if (res != null && res.containsKey("easySolved") && res.containsKey("mediumSolved")
                        && res.containsKey("hardSolved") && res.containsKey("profilePic")) {
                    Object e = res.get("easySolved");
                    Object m = res.get("mediumSolved");
                    Object h = res.get("hardSolved");
                    Object im = res.get("profilePic");
                    int ea = (e instanceof Number) ? ((Number) e).intValue() : Integer.parseInt(e.toString());
                    int me = (m instanceof Number) ? ((Number) m).intValue() : Integer.parseInt(m.toString());
                    int ha = (h instanceof Number) ? ((Number) h).intValue() : Integer.parseInt(h.toString());
                    String image = (im instanceof String) ? ((String) im) : "";
                    leadercache.put(user.getUsername(), new Leader(ea, me, ha, image, user.getCollegename()));

                }
            } catch (Exception e) {
                System.out.println("Error fetching for : " + user.getUsername());
            }
        }

    }

    @GetMapping("/refresh")
    public ResponseEntity<String> manualRefresh() {
        refreshleaderboard();
        return ResponseEntity.ok("Refresh triggered");
    }

    @GetMapping("/global")
    public List<Map<String, Object>> globalleaberboard(@RequestParam(required = false) String collegename) {
        List<Map<String, Object>> list = new ArrayList<>();
        leadercache.forEach((username, entry) -> {
            Map<String, Object> e = new HashMap<>();
            e.put("username", username);
            e.put("solved", entry.gettotalsolved());
            e.put("avatar", entry.img != null ? entry.img : "");
            e.put("points", entry.getpoints());
            e.put("collgename", entry.clgname);
            list.add(e);
        });
        list.sort((a, b) -> ((Integer) b.get("points")) - ((Integer) a.get("points")));
        for (int i = 0; i < list.size(); i++) {
            list.get(i).put("rank", i + 1);
        }
        return list;

    }

    @GetMapping("/colleges")
    public List<Map<String, Object>> clgleaderboard() {
        Map<String, Integer> collegpoints = new HashMap<>();
        leadercache.forEach((username, entry) -> {
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
        list.sort((a,b)->((Integer) b.get("points")-(Integer) a.get("points")));
        for (int i = 0; i < list.size(); i++) {
            list.get(i).put("rank", i+1);
        }
        return list;
    }

}
