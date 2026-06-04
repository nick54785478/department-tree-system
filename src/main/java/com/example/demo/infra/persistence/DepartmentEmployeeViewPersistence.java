package com.example.demo.infra.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.infra.projection.DepartmentEmployeeView;

@Repository
public interface DepartmentEmployeeViewPersistence extends JpaRepository<DepartmentEmployeeView, Long> {

	/**
	 * 供 RBAC / Gateway 系統查詢：找出特定員工隸屬的所有部門
	 */
	List<DepartmentEmployeeView> findAllByTenantIdAndEmployeeId(String tenantId, String employeeId);

	/**
	 * 供事件處理器使用：當員工被解編時，精準刪除該關聯
	 */
	void deleteByTenantIdAndDepartmentIdAndEmployeeId(String tenantId, String departmentId, String employeeId);

	/**
	 * 供事件處理器使用：當部門被停用或裁撤時，批量清空該部門下的所有員工關聯
	 */
	void deleteAllByTenantIdAndDepartmentId(String tenantId, String departmentId);
}