package com.example.lstats.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.lstats.model.GroupInvite;
import com.example.lstats.model.User;

public interface GroupinviteRepo extends JpaRepository<GroupInvite,Long> {
    List<GroupInvite> findByReceiverAndSender(User user,GroupInvite.InviteStatus status);
    
}
