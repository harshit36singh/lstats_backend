package com.example.lstats.auth.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.lstats.auth.AuthService;
import com.example.lstats.auth.dto.AuthResponse;
import com.example.lstats.auth.dto.LoginRequest;
import com.example.lstats.auth.dto.RegisterRequest;
import com.example.lstats.service.collegename;
import jakarta.validation.Valid;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.example.lstats.auth.controller.leaderboard;


@RestController
@RequestMapping("/auth")

public class AuthController {

private leaderboard leaderboardService;

    
    private final AuthService authService;
    private final collegename collegename;
     public AuthController(AuthService authService,collegename collegename,leaderboard leaderboardService){
        this.authService=authService;
        this.collegename=collegename;
        this.leaderboardService=leaderboardService;
    }
    
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req){
        AuthResponse res=authService.register(req);
        
        return ResponseEntity.status(201).body(res); 
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req){
        AuthResponse res=authService.login(req);
        leaderboardService.updateUserLeaderboard(req.getUsername());
        return ResponseEntity.ok(res);
    }
   @GetMapping("/collges")
   public ResponseEntity<List<String>> getcolleges(@RequestParam(required=false) String query){
    List<String> result=collegename.getcolleges(query);
return ResponseEntity.ok(result);
   }
    

}
