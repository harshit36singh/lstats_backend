package com.example.lstats.service;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class collegename {

    private List<String> colleges;

    @PostConstruct
    public void init() {
        try {

            ClassPathResource resource = new ClassPathResource("college.txt");
            String content = Files.readString(resource.getFile().toPath());
            colleges = Arrays.stream(content.split(","))
                     .map(s -> s.replace("\"", "").trim()) // remove quotes and trim
                     .filter(s -> !s.isEmpty())
                     .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            colleges = List.of("other");
        }
    }

    public List<String> getcolleges(String query) {
        if (query == null || query.isEmpty())
            return colleges;
        String lquery = query.toLowerCase();
        return colleges.stream().filter(c -> c.toLowerCase().contains(lquery)).collect(Collectors.toList());
    }
}
