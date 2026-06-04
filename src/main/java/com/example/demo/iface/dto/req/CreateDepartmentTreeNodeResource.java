package com.example.demo.iface.dto.req;

import java.util.List;

public record CreateDepartmentTreeNodeResource(String id, String parentId, String code, String name,
		List<CreateDepartmentTreeNodeResource> children) {

}
