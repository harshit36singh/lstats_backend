package com.example.lstats.service;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.io.IOException;
import jakarta.annotation.PostConstruct;

@Service
public class collegename {

    private List<String> colleges;

    @PostConstruct
    public void init() {
        try {

            ClassPathResource resource = new ClassPathResource("college.txt");
            colleges = Files.readAllLines(resource.getFile().toPath())
                    .stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            colleges = new ArrayList<>();
        }
    }

    public List<String> getcolleges(String query) {
        if (query == null || query.isEmpty())
            return colleges;
        String lquery = query.toLowerCase();
        return colleges.stream().filter(c -> c.toLowerCase().contains(lquery)).collect(Collectors.toList());
    }
}
