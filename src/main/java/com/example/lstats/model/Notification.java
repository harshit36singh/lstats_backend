package com.example.lstats.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name="notification")
@NoArgsConstructor
public class Notification {
    
    @Id
    @GeneratedValue(strategy =GenerationType.IDENTITY )
    private Long id;


    @ManyToOne
    private User receiver;

    private String message;

    private boolean readStatus=false;

    private LocalDateTime createdat=LocalDateTime.now();

    public Notification(User user,String message){
        this.receiver=user;
        this.message=message;


    }

}


