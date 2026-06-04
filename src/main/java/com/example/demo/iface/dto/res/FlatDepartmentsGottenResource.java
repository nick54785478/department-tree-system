package com.example.demo.iface.dto.res;

import java.util.List;

import com.example.demo.infra.shared.dto.DepartmentFlatNodeGottenView;

public record FlatDepartmentsGottenResource(String code, String message, List<DepartmentFlatNodeGottenView> data) {

}
