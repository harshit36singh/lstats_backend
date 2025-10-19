package com.example.lstats.model;


import jakarta.persistence.*;
import lombok.Data;


@Data
@Entity
@Table(name = "group_invites")
public class GroupInvite {
    

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;


    @ManyToOne
    @JoinColumn(name="group_id")
    private Group group;


    @ManyToOne
    @JoinColumn(name="sender_id")
    private User sender;


    @ManyToOne
    @JoinColumn(name="receiver_id")
    private User receiver;


    @Enumerated(EnumType.STRING)
    private InviteStatus status=InviteStatus.PENDING;


    public enum InviteStatus{
        PENDING,ACCEPTED,REJECTED
    }
}
