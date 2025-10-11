package com.example.lstats.auth.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.lstats.model.friendmodel;
import com.example.lstats.service.friendrequestservice;

@RestController
@RequestMapping("/friends")
public class FriendRequestController {
    private  final friendrequestservice friendrequestService;

    FriendRequestController(friendrequestservice f){
        this.friendrequestService=f;

    }
    
    @PostMapping("/send")
    public friendmodel sendreq(@RequestParam Long senderid,@RequestParam Long receiverid){
        return friendrequestService.sendreq(senderid, receiverid);
        
    }
    

    @PostMapping("/accept/{requestid}")
    public friendmodel acceptreq(@RequestParam Long requestid){
        return friendrequestService.acceptreq(requestid);
    }

    @PostMapping("/reject/{requestid}")
    public friendmodel rejectreq(@RequestParam Long requestid){
        return friendrequestService.rejectreq(requestid);

    }


    @GetMapping("/pending/{userid}")
    public List<friendmodel> getpendingreq(@PathVariable Long userid){
        return friendrequestService.getpendingreq(userid);

    }



}
