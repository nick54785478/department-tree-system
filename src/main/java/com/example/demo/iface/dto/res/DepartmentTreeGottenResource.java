package com.example.demo.iface.dto.res;

import com.example.demo.infra.shared.dto.DepartmentTreeNodeGottenView;

public record DepartmentTreeGottenResource(String code, String message, DepartmentTreeNodeGottenView data) {

}
