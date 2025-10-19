package com.example.lstats.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.lstats.model.Group;
import com.example.lstats.model.User;

public interface GroupRepo extends JpaRepository<Group,Long> {
    List<Group> findByMembersContains(User user);
}
