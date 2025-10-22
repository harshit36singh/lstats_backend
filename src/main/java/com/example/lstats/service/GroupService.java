package com.example.lstats.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.lstats.auth.dto.GroupDTO;
import com.example.lstats.model.Group;
import com.example.lstats.model.GroupInvite;
import com.example.lstats.model.User;
import com.example.lstats.repository.GroupRepo;
import com.example.lstats.repository.GroupinviteRepo;
import com.example.lstats.repository.UserRepository;

@Service
public class GroupService {

    private final NotificationService notificationservice;
    private final GroupRepo grouprep;
    private final UserRepository userrepo;
    private final GroupinviteRepo inviterepo;

    public GroupService(GroupRepo grouprep, UserRepository userrepo, GroupinviteRepo inviterepo,NotificationService notificationservice) {
        this.grouprep = grouprep;
        this.userrepo = userrepo;
        this.notificationservice=notificationservice;
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

    public List<GroupDTO> getusergroups(String name) {
    User user = userrepo.findByUsername(name)
            .orElseThrow(() -> new RuntimeException("Cant find this user"));

    List<Group> groups = grouprep.findByMembersContains(user);
    return groups.stream()
            .map(GroupDTO::fromEntity)
            .collect(Collectors.toList());
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
        notificationservice.createNotification(receiverid, "You got a new group invite from"+senderid+"for group"+group.getName());
        return inviterepo.save(invite);
    }


    public void acceptinvite(Long inviteid){
        GroupInvite invite=inviterepo.findById(inviteid).orElseThrow();
        invite.setStatus(GroupInvite.InviteStatus.ACCEPTED);
        inviterepo.save(invite);

        Group group=invite.getGroup();
        group.getMembers().add(invite.getReceiver());
        grouprep.save(group);
    }


    public List<GroupInvite> getpendinginvites(String name){
        User user=userrepo.findByUsername(name).orElseThrow(()->new RuntimeException("Cant find user"));
        return inviterepo.findByReceiverAndStatus(user, GroupInvite.InviteStatus.PENDING);
    }


}
