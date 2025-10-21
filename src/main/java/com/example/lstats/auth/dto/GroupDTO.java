package com.example.lstats.auth.dto;

import java.util.List;
import java.util.stream.Collectors;

import com.example.lstats.model.Group;
import com.example.lstats.model.User;
import lombok.Data;

@Data
public class GroupDTO {
    private Long id;
    private String name;
    private String createdBy;
    private List<String> members;

    public static GroupDTO fromEntity(Group group) {
        GroupDTO dto = new GroupDTO();
        dto.setId(group.getId());
        dto.setName(group.getName());
        dto.setCreatedBy(group.getCreatedby().getUsername());
        dto.setMembers(group.getMembers()
                .stream()
                .map(User::getUsername)
                .collect(Collectors.toList()));
        return dto;
    }
}
