package com.example.demo.infra.adapter;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.demo.application.domain.dept.aggregate.Department;
import com.example.demo.application.domain.dept.aggregate.vo.DepartmentId;
import com.example.demo.application.domain.dept.aggregate.vo.TenantId;
import com.example.demo.application.domain.dept.repository.DepartmentRepository;
import com.example.demo.infra.persistence.DepartmentPersistence;

import lombok.RequiredArgsConstructor;

/**
 * Department Repository Adapter (Infrastructure Layer)
 *
 * <pre>
 * 實作 Domain Layer 的 {@link DepartmentRepositoryPort} 介面。 負責將領域層的 Aggregate
 * 請求轉發給底層的 JPA Persistence。 本層不應包含業務邏輯，僅負責資料轉換與持久化。
 * </pre>
 */
@Component
@RequiredArgsConstructor
class DepartmentRepositoryAdapter implements DepartmentRepository {

	private final DepartmentPersistence persistence;

	@Override
	public boolean existsByTenantIdAndId(TenantId tenantId, DepartmentId id) {
		return persistence.existsByTenantIdAndId(tenantId, id);
	}

	@Override
	public Optional<Department> findById(DepartmentId id) {
		return persistence.findById(id);
	}

	@Override
	public Optional<Department> findByTenantIdAndId(TenantId tenantId, DepartmentId id) {
		return persistence.findByTenantIdAndId(tenantId, id);
	}

	@Override
	public List<Department> findAllByTenantIdAndIds(TenantId tenantId, List<DepartmentId> ids) {
		return persistence.findAllByTenantIdAndIdIn(tenantId, ids);
	}

	@Override
	public Department save(Department department) {
		// 注意：在此呼叫 save 時，Spring Data JPA 會自動攔截 @DomainEvents 並進行廣播
		return persistence.save(department);
	}

	@Override
	public List<DepartmentId> findSubtreeIds(TenantId tenantId, DepartmentId rootId) {
		// 1. 拆解 VO：將 Domain 傳遞的強型別拆為裸 String，交給 JPA 原生查詢執行 O(1) 效能檢索
		List<String> rawIds = persistence.findSubtreeIds(tenantId.getValue(), rootId.getValue());

		// 2. 封裝 VO：將資料庫返回的 String 重新包裝回 Domain Layer 認識的 Value Object
		return rawIds.stream().map(DepartmentId::new).collect(Collectors.toList());
	}

	@Override
	public boolean isAncestor(TenantId tenantId, DepartmentId ancestorId, DepartmentId descendantId) {
		// 透過底層 Native SQL 直攻唯讀閉包表 (department_tree) 進行拓撲防禦檢核
		return persistence.isAncestor(tenantId.getValue(), ancestorId.getValue(), descendantId.getValue());
	}

	@Override
	public List<Department> findDirectChildren(TenantId tenantId, DepartmentId parentId) {
		// 直接將強型別值物件傳遞給 JPA (Spring Data 能自動解析 EmbeddedId 內部屬性)
		return persistence.findByTenantIdAndParentId(tenantId, parentId);
	}
}