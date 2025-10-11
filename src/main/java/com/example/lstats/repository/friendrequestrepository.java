package com.example.lstats.repository;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.lstats.model.User;
import com.example.lstats.model.friendmodel;
import java.util.List;
import java.util.Optional;


public interface friendrequestrepository extends JpaRepository<friendmodel,Long>{


    List<friendmodel> findByReceiver(User receiver);

    List<friendmodel> findBySender(User sender);

 Optional<friendmodel> findBySenderAndReceiver(User sender, User receiver);
    
}
