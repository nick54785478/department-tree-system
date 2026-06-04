package com.example.demo.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.domain.dept.aggregate.Department;
import com.example.demo.application.domain.dept.aggregate.vo.DepartmentCode;
import com.example.demo.application.domain.dept.aggregate.vo.DepartmentId;
import com.example.demo.application.domain.dept.aggregate.vo.DepartmentStatus;
import com.example.demo.application.domain.dept.aggregate.vo.TenantId;
import com.example.demo.application.domain.dept.repository.DepartmentRepository;
import com.example.demo.application.shared.command.AssignEmployeeCommand;
import com.example.demo.application.shared.command.ChangeDepartmentSortOrderCommand;
import com.example.demo.application.shared.command.CreateDepartmentCommand;
import com.example.demo.application.shared.command.CreateDepartmentTreeCommand;
import com.example.demo.application.shared.command.DeleteDepartmentCommand;
import com.example.demo.application.shared.command.DisableDepartmentCommand;
import com.example.demo.application.shared.command.MoveDepartmentCommand;
import com.example.demo.application.shared.command.RenameDepartmentCommand;
import com.example.demo.application.shared.command.UnassignEmployeeCommand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Department Command Application Service (部門指令應用服務)
 *
 * <pre>
 * 本類別擔任部門聚合根 (Department Aggregate) 的 Use-Case Orchestrator (使用案例協調者)。
 *
 * <b>核心職責</b>： 
 *  1. 接收外部介面層 (Controller/Listener) 傳入的 Command DTO。 
 *  2. 負責跨實體的基礎業務驗證(如：防禦循環依賴、越權存取檢核)。 
 *  3. 載入並呼叫聚合根內部的業務方法 (Behavior) 來變更狀態，保護領域不變量 (Invariants)。
 *  4. 透過倉儲合約 (Port) 將變更持久化，並觸發後續的領域事件 (Domain Events)。
 *
 * <b>架構原則</b>： 
 *  - 遵守依賴反轉原則 (DIP)：僅依賴 {@link DepartmentRepository} Port 介面，與底層 Spring Data JPA 或資料庫實作完全解耦。 
 *  - 嚴格的交易邊界：每個方法皆宣告{@code @Transactional}，確保狀態變更與事件發布具備絕對的原子性 (Atomicity)。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentCommandService {

	// 統一依賴 Port 介面，確保應用層的絕對純潔性
	private final DepartmentRepository departmentRepository;

	// =========================================================
	// CREATE DEPARTMENT
	// =========================================================

	/**
	 * 建立單一部門 (Root 或 Child)。
	 * <p>
	 * 處理租戶邊界內的唯一性校驗，並依據是否提供 parentId 決定調用哪一種聚合根工廠方法。
	 * </p>
	 *
	 * @param command 包含建立部門所需完整資訊的指令 DTO
	 * @throws IllegalArgumentException 若必填欄位缺失
	 * @throws IllegalStateException    若部門 ID 已存在，或父部門狀態不合法 (停用/刪除)
	 */
	@Transactional
	public void createDepartment(CreateDepartmentCommand command) {
		validateCreate(command);

		TenantId tenantId = new TenantId(command.tenantId());
		DepartmentId id = new DepartmentId(command.id());
		DepartmentCode code = new DepartmentCode(command.code());

		// 唯一性防禦：確保同一租戶下，不會發生 Aggregate ID 重複碰撞
		if (departmentRepository.existsByTenantIdAndId(tenantId, id)) {
			throw new IllegalStateException("Department ID already exists in this tenant");
		}

		Department department;

		// 情境 A：建立一級部門 (Root Department)，無父節點
		if (command.parentId() == null) {
			department = Department.createRoot(tenantId, id, code, command.name(), command.operator());
		}
		// 情境 B：建立子部門 (Child Department)
		else {
			DepartmentId parentId = new DepartmentId(command.parentId());

			// 安全性設計：直接使用帶有 Tenant 防護的查詢，避免越權存取他人的部門作為父節點
			Department parent = loadAndValidateTenant(tenantId, parentId);
			validateParent(parent);

			department = Department.createChild(tenantId, id, parentId, code, command.name(), command.operator());
		}

		// 儲存 Aggregate：Spring Data JPA 會在此刻自動攔截 @DomainEvents 並將建立事件廣播出去
		departmentRepository.save(department);
	}

	// =========================================================
	// MOVE DEPARTMENT
	// =========================================================

	/**
	 * 移動部門 (組織樹結構重組)。
	 * <p>
	 * 包含循環依賴檢核、自體移動防禦，並支援將部門晉升為 Root 節點。
	 * </p>
	 *
	 * @param command 包含移動目標與操作者資訊的指令 DTO
	 * @throws IllegalArgumentException 若嘗試移至自身
	 * @throws IllegalStateException    若偵測到循環依賴 (Cycle) 或父節點不合法
	 */
	@Transactional
	public void moveDepartment(MoveDepartmentCommand command) {
		validate(command);

		TenantId tenantId = new TenantId(command.tenantId());
		DepartmentId deptId = new DepartmentId(command.departmentId());

		// 1. 載入欲移動的目標部門聚合根
		Department department = loadAndValidateTenant(tenantId, deptId);

		// 2. 判斷移動情境：移至根節點 vs 移至其他特定父節點
		if (command.newParentId() == null) {
			// 情境 A：移至最頂層 (晉升成為 Root 節點)
			// 無需檢查 Cycle，也無需載入 Parent
			department.moveToRoot(command.operator());
		} else {
			// 情境 B：移至特定子節點底下
			DepartmentId newParentId = new DepartmentId(command.newParentId());

			// 防禦：不能把部門移到自己底下 (邏輯謬誤)
			if (deptId.equals(newParentId)) {
				throw new IllegalArgumentException("Cannot move department to itself");
			}

			// 冪等性/無效操作防禦：不能移到自己原本的老爸底下
			// 若發現是無效移動，直接 return 結束，避免浪費 DB 資源與觸發無效的 Domain Event
			if (department.getParentId() != null && department.getParentId().equals(newParentId)) {
				log.info("Department {} is already under parent {}. Ignoring move command.", deptId.getValue(),
						newParentId.getValue());
				return;
			}

			// 載入並驗證新父節點的合法性
			Department newParent = loadAndValidateTenant(tenantId, newParentId);
			validateParent(newParent);

			// 核心幾何防禦：循環依賴檢查 (避免 A -> B -> C -> A 導致樹狀結構崩壞)
			validateNoCycle(deptId, newParentId, tenantId);

			// 執行聚合根內部的移動邏輯
			department.moveTo(newParentId, command.operator());
		}

		// 3. 儲存 Aggregate 並自動發布 DepartmentMovedEvent
		departmentRepository.save(department);
	}

	// =========================================================
	// DELETE DEPARTMENT
	// =========================================================

	/**
	 * 邏輯刪除部門 (包含其下整棵子樹的所有子孫節點)。
	 * <p>
	 * 這是一個具備連鎖效應的高風險操作，需確保該節點下的所有組織皆一併遭到邏輯移除。
	 * </p>
	 *
	 * @param command 刪除指令 DTO
	 */
	@Transactional
	public void delete(DeleteDepartmentCommand command) {
		validate(command);

		TenantId tenantId = new TenantId(command.tenantId());
		DepartmentId rootId = new DepartmentId(command.departmentId());

		// 架構亮點：不依賴應用層遞迴，而是直接請 Repository 利用底層 Closure Table 找出所有子孫 ID
		List<DepartmentId> ids = departmentRepository.findSubtreeIds(tenantId, rootId);

		if (ids.isEmpty()) {
			throw new IllegalStateException("Department subtree not found");
		}

		// 批次載入整棵子樹的所有聚合根進入記憶體
		List<Department> departments = departmentRepository.findAllByTenantIdAndIds(tenantId, ids);

		// 逐一觸發聚合根內部的 delete 領域邏輯
		for (Department department : departments) {
			department.delete(command.operator());
			// 儲存 Aggregate 並自動為每一個被刪除的節點發布 DepartmentDeletedEvent
			departmentRepository.save(department);
		}
	}

	// =========================================================
	// EMPLOYEE ASSIGNMENT
	// =========================================================

	/**
	 * 將特定員工指派至某部門。
	 */
	@Transactional
	public void assignEmployee(AssignEmployeeCommand cmd) {
		TenantId tenantId = new TenantId(cmd.tenantId());
		DepartmentId deptId = new DepartmentId(cmd.departmentId());

		Department department = loadAndValidateTenant(tenantId, deptId);

		// 委派給 Aggregate 執行領域邏輯
		department.assignEmployee(cmd.employeeId(), cmd.operator());

		// 儲存 Aggregate 並自動發布 EmployeeAssignedToDepartmentEvent
		departmentRepository.save(department);

		log.info("Employee {} assigned to department {} successfully", cmd.employeeId(), cmd.departmentId());
	}

	/**
	 * 將特定員工從某部門移出。
	 */
	@Transactional
	public void unassignEmployee(UnassignEmployeeCommand cmd) {
		TenantId tenantId = new TenantId(cmd.tenantId());
		DepartmentId deptId = new DepartmentId(cmd.departmentId());

		Department department = loadAndValidateTenant(tenantId, deptId);

		// 委派給 Aggregate 執行領域邏輯
		department.unassignEmployee(cmd.employeeId(), cmd.operator());

		// 儲存 Aggregate 並自動發布 EmployeeUnassignedFromDepartmentEvent
		departmentRepository.save(department);

		log.info("Employee {} unassigned from department {} successfully", cmd.employeeId(), cmd.departmentId());
	}

	// =========================================================
	// RENAME DEPARTMENT
	// =========================================================

	/**
	 * 變更部門名稱。
	 */
	@Transactional
	public void renameDepartment(RenameDepartmentCommand command) {
		if (command.newName() == null || command.newName().isBlank()) {
			throw new IllegalArgumentException("New name is required");
		}

		TenantId tenantId = new TenantId(command.tenantId());
		DepartmentId deptId = new DepartmentId(command.departmentId());

		Department department = loadAndValidateTenant(tenantId, deptId);

		// 執行聚合根內的名稱變更規則與狀態保護
		department.rename(command.newName(), command.operator());

		departmentRepository.save(department);
	}

	// =========================================================
	// DISABLE DEPARTMENT (停用部門及其所有子孫)
	// =========================================================

	/**
	 * 業務停用部門 (包含其下所有活著的子孫節點)。
	 * <p>
	 * 停用的部門在 UI 上通常會反灰且無法再進行員工分派，但不同於邏輯刪除，它依然存在於組織樹中。
	 * </p>
	 */
	@Transactional
	public void disableDepartment(DisableDepartmentCommand command) {
		TenantId tenantId = new TenantId(command.tenantId());
		DepartmentId targetDeptId = new DepartmentId(command.departmentId());

		// 1. 確保目標部門存在 (同時也當作權限與基本驗證)
		loadAndValidateTenant(tenantId, targetDeptId);

		// 2. 利用 Closure Table 的強大能力，一口氣找出自己與所有子孫的 ID
		List<DepartmentId> subtreeIds = departmentRepository.findSubtreeIds(tenantId, targetDeptId);

		// 3. 批次載入整棵子樹的所有聚合根
		List<Department> subtree = departmentRepository.findAllByTenantIdAndIds(tenantId, subtreeIds);

		// 4. 逐一執行業務停用邏輯
		for (Department dept : subtree) {
			// 防護：因為 Aggregate 內的 validateActive() 若遇到已停用的實體會拋錯，
			// 所以我們先檢查該節點是否還在啟用狀態，避免已經被停用的子節點導致整個 Transaction 意外 Rollback。
			if (dept.getStatus() == DepartmentStatus.ACTIVE) {
				dept.disable(command.operator());
				// 每 save 一次，就會觸發該部門專屬的 DepartmentDisabledEvent
				departmentRepository.save(dept);
			}
		}

		log.info("Successfully disabled department {} and its active descendants. Total affected: {}",
				targetDeptId.getValue(), subtree.size());
	}

	// =========================================================
	// CHANGE SORT ORDER
	// =========================================================

	/**
	 * 變更部門在 UI 上的同級排序權重。
	 */
	@Transactional
	public void changeSortOrder(ChangeDepartmentSortOrderCommand command) {
		TenantId tenantId = new TenantId(command.tenantId());
		DepartmentId deptId = new DepartmentId(command.departmentId());

		Department department = loadAndValidateTenant(tenantId, deptId);

		department.changeSortOrder(command.sortOrder(), command.operator());

		departmentRepository.save(department);
	}

	// =========================================================
	// BULK CREATE DEPARTMENT TREE (整棵樹批次遞迴建立)
	// =========================================================

	/**
	 * 批次建立整棵巢狀部門樹。
	 * <p>
	 * 適用於系統初始化或大規模組織匯入。透過 DFS 遞迴保證了由上至下的正確寫入順序。
	 * </p>
	 *
	 * @param rootCommand 包含巢狀子節點列表的樹狀指令 DTO
	 */
	@Transactional
	public void createDepartmentTree(CreateDepartmentTreeCommand rootCommand) {
		if (rootCommand == null) {
			throw new IllegalArgumentException("Root command cannot be null");
		}

		TenantId tenantId = new TenantId(rootCommand.tenantId());

		// 解析初始的掛載點 (若為 null 代表從最頂層 Root 開始建樹)
		DepartmentId startingParentId = rootCommand.parentId() != null ? new DepartmentId(rootCommand.parentId())
				: null;

		// 啟動深度優先 (DFS) 遞迴建立部門
		this.createNodeRecursively(rootCommand, startingParentId, tenantId);

		log.info("Successfully created nested department tree starting from root {}", rootCommand.id());
	}

	/**
	 * 深度優先 (DFS) 遞迴建樹演算法
	 * <p>
	 * 保證了父節點的 INSERT 會永遠早於子節點，完美避開關聯式資料庫的外鍵與相依性問題， 且發布事件的順序也會完美呈現 Top-Down。
	 * </p>
	 */
	private void createNodeRecursively(CreateDepartmentTreeCommand commandNode, DepartmentId parentId,
			TenantId tenantId) {
		DepartmentId id = new DepartmentId(commandNode.id());
		DepartmentCode code = new DepartmentCode(commandNode.code());
		String operator = commandNode.operatorId();

		// 1. 重複檢查 (防禦同租戶下 ID 重複碰撞)
		if (departmentRepository.existsByTenantIdAndId(tenantId, id)) {
			throw new IllegalStateException("Department ID " + id.getValue() + " already exists in this tenant");
		}

		Department department;

		// 2. 建立根節點或子節點
		if (parentId == null) {
			department = Department.createRoot(tenantId, id, code, commandNode.name(), operator);
		} else {
			// JPA 快取魔法：上一層遞迴剛剛才執行完 save()，
			// 所以這裡的 load 絕對能直接命中 L1 Cache，不會產生額外的 N+1 SQL 查詢負擔！
			Department parent = loadAndValidateTenant(tenantId, parentId);
			validateParent(parent);
			department = Department.createChild(tenantId, id, parentId, code, commandNode.name(), operator);
		}

		// 3. 儲存 Aggregate (寫入 Command DB 並發出 CreatedEvent 給 Projector)
		departmentRepository.save(department);

		// 4. 遞迴往下鑽：處理所有巢狀子節點
		if (commandNode.children() != null && !commandNode.children().isEmpty()) {
			for (CreateDepartmentTreeCommand childCommand : commandNode.children()) {
				// 強制將剛剛建立完成的自己 (id)，當作老爸 (parentId) 傳遞給下一個子節點
				createNodeRecursively(childCommand, id, tenantId);
			}
		}
	}

	// =========================================================
	// VALIDATION & HELPERS (輔助驗證方法)
	// =========================================================

	/**
	 * 載入並驗證租戶邊界。
	 * <p>
	 * 將 IDOR (越權存取) 防禦推遲到資料庫查詢層級，確保撈出來的資料絕對隸屬於當前請求租戶。
	 * </p>
	 */
	private Department loadAndValidateTenant(TenantId tenantId, DepartmentId departmentId) {
		return departmentRepository.findByTenantIdAndId(tenantId, departmentId)
				.orElseThrow(() -> new SecurityException("Department not found or access denied for tenant"));
	}

	/**
	 * 驗證作為父節點的合法性。
	 * <p>
	 * 被停用或被刪除的部門，不得作為新部門的父節點，也不接受其他部門移入。
	 * </p>
	 */
	private void validateParent(Department parent) {
		if (parent.getStatus() == DepartmentStatus.DISABLED) {
			throw new IllegalStateException("Disabled parent not allowed");
		}
		if (parent.isDeleted()) {
			throw new IllegalStateException("Deleted parent not allowed");
		}
	}

	/**
	 * 遞迴防禦：檢查移動操作是否會導致組織樹出現循環依賴 (Cycle)。
	 * <p>
	 * 從目標的新老爸開始，一路往上找祖先，如果中途撞見了自己，代表發生了循環。
	 * </p>
	 */
	private void validateNoCycle(DepartmentId movingId, DepartmentId newParentId, TenantId tenantId) {
		DepartmentId current = newParentId;

		while (current != null) {
			if (current.equals(movingId)) {
				throw new IllegalStateException("Cycle detected");
			}

			Department parent = departmentRepository.findByTenantIdAndId(tenantId, current).orElse(null);
			if (parent == null) {
				break;
			}
			current = parent.getParentId();
		}
	}

	/**
	 * 檢核建立部門指令的必要參數。
	 */
	private void validateCreate(CreateDepartmentCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("Command cannot be null");
		}
		if (command.tenantId() == null || command.tenantId().isBlank()) {
			throw new IllegalArgumentException("Tenant id required");
		}
		if (command.id() == null || command.id().isBlank()) {
			throw new IllegalArgumentException("Department id required");
		}
		if (command.code() == null || command.code().isBlank()) {
			throw new IllegalArgumentException("Department code required");
		}
		if (command.name() == null || command.name().isBlank()) {
			throw new IllegalArgumentException("Department name required");
		}
		if (command.operator() == null || command.operator().isBlank()) {
			throw new IllegalArgumentException("Operator required");
		}
	}

	/**
	 * 檢核移動部門指令的必要參數。
	 */
	private void validate(MoveDepartmentCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("Command cannot be null");
		}
		if (command.tenantId() == null || command.tenantId().isBlank()) {
			throw new IllegalArgumentException("Tenant id required");
		}
		if (command.departmentId() == null || command.departmentId().isBlank()) {
			throw new IllegalArgumentException("Department id required");
		}
		if (command.newParentId() == null || command.newParentId().isBlank()) {
			throw new IllegalArgumentException("New parent id required");
		}
		if (command.departmentId().equals(command.newParentId())) {
			throw new IllegalArgumentException("Cannot move to itself");
		}
	}

	/**
	 * 檢核刪除部門指令的必要參數。
	 */
	private void validate(DeleteDepartmentCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("Command cannot be null");
		}
		if (command.tenantId() == null || command.tenantId().isBlank()) {
			throw new IllegalArgumentException("Tenant id required");
		}
		if (command.departmentId() == null || command.departmentId().isBlank()) {
			throw new IllegalArgumentException("Department id required");
		}
	}
}