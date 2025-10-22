package com.example.lstats.service;

import java.util.List;

import com.example.lstats.model.Notification;
import com.example.lstats.model.User;
import com.example.lstats.repository.NotificationRepository;
import com.example.lstats.repository.UserRepository;

public class NotificationService {
    private final NotificationRepository notificationRepository;
    private UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public Notification createNotification(String username, String message) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("No user found"));
        Notification n = new Notification(user, message);
        return notificationRepository.save(n);

    }

    public List<Notification> getunreadnotification(String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("nouser found"));
        return notificationRepository.findReceiverAndReadStatusFalse(user);

    }

    public void markAsRead(Long id){
        Notification n=notificationRepository.findById(id).orElseThrow(()->new RuntimeException("Notification not found"));
        n.setReadStatus(true);
        notificationRepository.save(n);
    }

}
