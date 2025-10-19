package com.example.lstats.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name="friendreq")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class friendmodel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long Id;


    @ManyToOne
    @JoinColumn(name="sender_id",nullable = false)
    private User sender;


    @ManyToOne
    @JoinColumn(name="receiver_id",nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private Status status=Status.PENDING;


    public enum Status{
        PENDING,REJECTED,ACCEPTED
    } 




}
