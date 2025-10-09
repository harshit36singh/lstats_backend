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
import com.example.lstats.model.User;

@RestController
@RequestMapping("/leaderboard")
@CrossOrigin(origins = "*")
public class leaderboard {
    @Autowired
    private UserRepository userRepository;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, Integer> leadercache = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 3600000)
    private void refreshleaderboard() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            try {
                String url = "https://lstatsbackend-production.up.railway.app/leetcode" + user.getUsername();
                Map<String, Object> res = restTemplate.getForObject(url, Map.class);
                if (res != null && res.containsKey("easySolved") && res.containsKey("mediumSolved")
                        && res.containsKey("hardSolved")) {
                            Object e=res.get("easySolved");
                            Object m=res.get("mediumSolved");
                            Object h=res.get("hardSolved");
                            int ea=(e instanceof Number)?((Number)e).intValue():Integer.parseInt(e.toString());
                            int me=(m instanceof Number)?((Number)m).intValue():Integer.parseInt(m.toString());
                            int ha=(h instanceof Number)?((Number)h).intValue():Integer.parseInt(h.toString());
                            leadercache.put(user.getUsername(), ea+me+ha);

                }
            } catch (Exception e) {
                System.out.println("Error fetching for : "+user.getUsername());
            }
        }

    }

    @GetMapping("/refresh")
    public ResponseEntity<String> manualRefresh(){
        refreshleaderboard();
        return ResponseEntity.ok("Refresh triggered");
    }

    @GetMapping("/global")
    public List<Map<String,Object>> globalleaberboard(@RequestParam String collegename){
      List<Map<String,Object>> list=new ArrayList<>();
      leadercache.forEach((username,solved)->{
        Map<String,Object> entry=new HashMap<>();
        entry.put("username",username);
        entry.put("solved", solved);
        list.add(entry);
      });
      list.sort((a,b)->((Integer) b.get("solved"))-((Integer) a.get("solved")));
      for (int i = 0; i < list.size(); i++) {
        list.get(i).put("rank",i+1);
      }
      return list;
        
    } 

}
