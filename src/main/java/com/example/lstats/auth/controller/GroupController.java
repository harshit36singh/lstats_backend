package com.example.lstats.auth.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.lstats.auth.dto.GroupDTO;
import com.example.lstats.model.Group;
import com.example.lstats.model.GroupInvite;
import com.example.lstats.service.GroupService;


@RestController
@RequestMapping("/groups")
public class GroupController {
    private final GroupService groupservice;

    public GroupController(GroupService groupservice) {
        this.groupservice = groupservice;
    }


    @PostMapping("/create")
    public Group creategroup(@RequestParam String name,@RequestParam String username){
        return groupservice.creategroup(name,username);
    }
    
    @GetMapping("/my")
    public List<GroupDTO> getmygroup(@RequestParam String name){
        return groupservice.getusergroups(name);

    }

    @PostMapping("/{groupid}/invite")
    public GroupInvite sendInvite(@PathVariable Long groupid,@RequestParam String sender,@RequestParam String receiver){
        return groupservice.sendInvite(groupid, sender, receiver);

    }

    @PostMapping("invite/{inviteid}/accept")
    public ResponseEntity<String> acceptinvite(@PathVariable Long inviteid){
        groupservice.acceptinvite(inviteid);
        return ResponseEntity.ok("Accepted");
    }

    @PostMapping("/invites")
    public List<GroupInvite> getpendinginvite(@RequestParam String username){
        return groupservice.getpendinginvites(username);
    }

    
}
