package com.example.lstats.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.lstats.model.Group;
import com.example.lstats.model.GroupInvite;
import com.example.lstats.model.User;
import com.example.lstats.repository.GroupRepo;
import com.example.lstats.repository.GroupinviteRepo;
import com.example.lstats.repository.UserRepository;

@Service
public class GroupService {

    private final GroupRepo grouprep;
    private final UserRepository userrepo;
    private final GroupinviteRepo inviterepo;

    public GroupService(GroupRepo grouprep, UserRepository userrepo, GroupinviteRepo inviterepo) {
        this.grouprep = grouprep;
        this.userrepo = userrepo;
        this.inviterepo = inviterepo;
    }

    public Group creategroup(String name, String creatorname) {
        User creator = userrepo.findByUsername(creatorname)
        .orElseThrow(() -> 
        new RuntimeException("Cant find user"));

        Group g=new Group();
        g.setName(name);
        g.setCreatedby(creator);
        g.getMembers().add(creator);
        return grouprep.save(g);

    }

    public List<Group> getusergroups(String name){
        User user=userrepo.findByUsername(name).orElseThrow(()->new RuntimeException("Cant find this user"));
        return grouprep.findByMembersContains(user);

    }

    public GroupInvite sendInvite(Long groupid,String senderid,String receiverid){
        Group group=grouprep.findById(groupid).orElseThrow(()->new RuntimeException("Cant find the group"));
        User sender=userrepo.findByUsername(senderid).orElseThrow(()->new RuntimeException("Cant find the sender"));
        User receiver=userrepo.findByUsername(receiverid).orElseThrow(()->new RuntimeException("Cant find the sender"));
        GroupInvite invite=new GroupInvite();
        invite.setGroup(group);
        invite.setSender(sender);
        invite.setReceiver(receiver);
        invite.setStatus(GroupInvite.InviteStatus.PENDING);
        return inviterepo.save(invite);
    }


    public void acceptinvite(Long inviteid){
        GroupInvite invite=inviterepo.findById(inviteid).orElseThrow();
        invite.setStatus(GroupInvite.InviteStatus.ACCEPTED);
        inviterepo.save(invite);

        Group group=new Group();
        group.getMembers().add(invite.getReceiver());
        grouprep.save(group);
    }


    public List<GroupInvite> getpendinginvites(String name){
        User user=userrepo.findByUsername(name).orElseThrow(()->new RuntimeException("Cant find user"));
        return inviterepo.findByReceiverandSender(user, GroupInvite.InviteStatus.PENDING);
    }
}
