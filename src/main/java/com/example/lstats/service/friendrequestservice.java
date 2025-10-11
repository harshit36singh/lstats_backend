package com.example.lstats.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.lstats.model.User;
import com.example.lstats.model.friendmodel;
import com.example.lstats.repository.UserRepository;
import com.example.lstats.repository.friendrequestrepository;

@Service
public class friendrequestservice {

    private final friendrequestrepository Friendrequestrepository;
    private final UserRepository userrepository;

    public friendrequestservice(friendrequestrepository f, UserRepository u) {
        this.Friendrequestrepository = f;
        this.userrepository = u;

    }

    public friendmodel sendreq(Long senderid, Long recieverid) {
        User sender = userrepository.findById(recieverid)
                .orElseThrow(() -> new RuntimeException("Sender not found."));
        User reciever = userrepository.findById(recieverid)
                .orElseThrow(() -> new RuntimeException("Receiver not found."));

        if (Friendrequestrepository.findBySenderAndReceiver(sender, reciever).isPresent()) {
            throw new RuntimeException("Request alread sent");
        }
        friendmodel req = new friendmodel();
        req.setSender(sender);
        req.setReceiver(reciever);
        return Friendrequestrepository.save(req);

    }

    public friendmodel acceptreq(Long reqid) {
        friendmodel req = Friendrequestrepository.findById(reqid)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        req.setStatus(friendmodel.Status.ACCEPTED);
        return Friendrequestrepository.save(req);
    }

    public friendmodel rejectreq(Long acceptid) {
        friendmodel req = Friendrequestrepository.findById(acceptid)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        req.setStatus(friendmodel.Status.REJECTED);
        return Friendrequestrepository.save(req);
    }

    public List<friendmodel> getpendingreq(Long userid) {
        User user = userrepository.findById(userid).orElseThrow(() -> 
        new RuntimeException("No such user found."));
        return Friendrequestrepository.findByReceiver(user).stream()
                .filter(r -> r.getStatus() == friendmodel.Status.PENDING).toList();
    }
}
