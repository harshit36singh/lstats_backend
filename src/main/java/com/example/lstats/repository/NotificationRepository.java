package com.example.lstats.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.lstats.model.Notification;
import com.example.lstats.model.User;

public interface NotificationRepository extends JpaRepository<Notification,Long>{
    List<Notification> findReceiverAndReadStatusFalse(User receiver);
}
