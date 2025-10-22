package com.example.lstats.auth.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.lstats.model.Notification;
import com.example.lstats.service.NotificationService;

@RestController
@RequestMapping("/notification")
public class NotificationController {
    private final NotificationService notificationservice;

    public NotificationController(NotificationService notificationservice) {
        this.notificationservice = notificationservice;
    }

    @PostMapping("/create")
    public Notification createNotification(@RequestParam String name,@RequestParam String message ){
        return notificationservice.createNotification(name, message);
    }

    @GetMapping("/unread")
    public List<Notification> unreadmessage(@RequestParam String username){
        return notificationservice.getunreadnotification(username);
    }

    @PostMapping("{id}/read")
    public void markasread(@PathVariable Long id){
        notificationservice.markAsRead(id);
    }
}
