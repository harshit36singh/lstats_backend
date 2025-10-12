package com.example.lstats.auth.dto;

import com.example.lstats.model.User;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    
    private String message;
    private String token;
    private User user;
}
