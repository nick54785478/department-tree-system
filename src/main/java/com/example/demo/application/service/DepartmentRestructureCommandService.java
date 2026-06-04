package com.example.demo.application.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.domain.dept.aggregate.Department;
import com.example.demo.application.domain.dept.aggregate.vo.DepartmentId;
import com.example.demo.application.domain.dept.aggregate.vo.TenantId;
import com.example.demo.application.domain.dept.event.EmployeeAssignedToDepartmentEvent;
import com.example.demo.application.domain.dept.event.EmployeeUnassignedFromDepartmentEvent;
import com.example.demo.application.domain.dept.repository.DepartmentRepository;
import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.domain.shared.exception.DomainException;
import com.example.demo.application.port.EventStorerPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 部門組織重組應用服務 (Application Service - Process Manager)
 *
 * <pre>
 * <b>架構定位 (Pure CQRS & Event Sourcing)：</b> 
 * 本服務在微服務架構中扮演「流程編排者 (Orchestrator)」的角色，專責協調跨越單一聚合邊界 (Cross-Aggregate) 的複雜業務操作。 
 * <b>100% 寫入端純潔性：</b> 本服務已徹底斬斷對 Query Side (唯讀視圖表) 的任何依賴。 針對跨聚合的人員轉移大遷徙，
 * 直接透過{@link EventStorerPort} 撈取歷史事件， 利用「命令端記憶體投影 (Command-Side In-Memory Projection)」進行狀態折疊， 
 * 完美演繹了以 Event Store 作為「唯一真實來源 (Single Source of Truth, SSOT)」的架構實踐。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentRestructureCommandService {

	private final DepartmentRepository departmentRepository;

	// 注入事件儲存庫 Port，作為推演人員名單的來源
	private final EventStorerPort eventStorer;

	/**
	 * 執行跨聚合部門合併重組 (Merge Department B into A)
	 * 
	 * <pre>
	 * 將來源部門 (Source) 的所有核心資產 (直屬子部門、人員編制) 完整轉移至目標部門 (Target)，
	 * 並在抽乾資源後，將來源部門標記為「已合併」的終結狀態。 此流程具備絕對的原子性 (Atomicity)，保證重組過程中的狀態一致性。
	 * </pre>
	 *
	 * @param tenantIdStr     多租戶識別碼
	 * @param sourceDeptIdStr 被消滅/被合併的來源部門 ID
	 * @param targetDeptIdStr 吸收資產的目標部門 ID
	 * @param operator        執行此重組操作的管理員 ID
	 */
	@Transactional
	public void mergeDepartment(String tenantIdStr, String sourceDeptIdStr, String targetDeptIdStr, String operator) {

		// 邊界防禦：第一時間將外部傳入的裸字串 (Primitive) 轉化為強型別值物件 (Value Object)
		TenantId tenantId = new TenantId(tenantIdStr);
		DepartmentId sourceDeptId = new DepartmentId(sourceDeptIdStr);
		DepartmentId targetDeptId = new DepartmentId(targetDeptIdStr);

		// ==========================================
		// 1. 載入雙方聚合根 (Aggregate Rehydration)
		// ==========================================
		Department sourceDept = departmentRepository.findByTenantIdAndId(tenantId, sourceDeptId)
				.orElseThrow(() -> new DomainException("來源部門不存在或無權限存取"));
		Department targetDept = departmentRepository.findByTenantIdAndId(tenantId, targetDeptId)
				.orElseThrow(() -> new DomainException("目標部門不存在或無權限存取"));

		// ==========================================
		// 2. 組織拓撲防禦 (DAG Cycle Prevention)
		// ==========================================
		// 防止發生「將祖父部門併入孫子部門」而導致整棵組織樹變成無限迴圈 (Cyclic Graph) 的毀滅性災難。
		if (departmentRepository.isAncestor(tenantId, sourceDeptId, targetDeptId)) {
			throw new DomainException("拓撲違規：無法將上級部門合併至其下屬分支機構中");
		}

		// ==========================================
		// 3. 資產轉移 I：子部門拓撲過繼 (Sub-Department Transfer)
		// ==========================================
		// 找出來源部門的「直屬第一代」子部門，呼叫它們的領域行為進行掛載點轉移
		List<Department> directChildren = departmentRepository.findDirectChildren(tenantId, sourceDeptId);
		for (Department child : directChildren) {
			child.moveTo(targetDept.getId(), operator);
			departmentRepository.save(child);
		}

		// ==========================================
		// 4. 資產轉移 II：利用事件溯源重建並轉移人員名單 (Employee Transfer)
		// ==========================================
		// 捨棄了容易產生延遲與髒讀的 View 表查詢，直接從 Event Store 重播歷史推演現狀，確保絕對強一致性。
		Set<String> employeeIds = rehydrateEmployeeIds(tenantIdStr, sourceDeptIdStr);

		for (String empId : employeeIds) {
			// 從來源部門移出 (將同步扣減 sourceDept 內部的 activeEmployeeCount 防護計數器)
			sourceDept.unassignEmployee(empId, operator);
			// 分派至目標部門 (將同步增加 targetDept 內部的 activeEmployeeCount 防護計數器)
			targetDept.assignEmployee(empId, operator);
		}

		// 優先儲存 Target，確保吸收人員與子部門的領域事件被成功派發與持久化
		departmentRepository.save(targetDept);

		// ==========================================
		// 5. 終結來源部門 (Terminal State Transition)
		// ==========================================
		// 由於在前一步驟中，人員與子部門已被我們利用 Application Service 徹底抽乾，
		// 此時 sourceDept 必定能順利通過內部 activeEmployeeCount == 0 的不變量防護，安全進入停用狀態。
		sourceDept.markAsMergedInto(targetDept.getId(), operator);
		departmentRepository.save(sourceDept);

		log.info("部門重組成功：[{}] 已完全併入 [{}]，共轉移 {} 個子部門與 {} 名員工", sourceDept.getName(), targetDept.getName(),
				directChildren.size(), employeeIds.size());
	}

	// ==========================================
	// 私有輔助方法區 (Event Folding / Reduce)
	// ==========================================

	/**
	 * 命令端記憶體投影 (Command-Side In-Memory Projection / Event Folding)。
	 * 
	 * <pre>
	 * <b>設計模式亮點：</b> 透過重播 (Replay) 該部門從盤古開天至今的所有與人員指派相關的歷史事件， 在記憶體中執行狀態折疊 (Left
	 * Fold / Reduce)，精準推演出「當下這一毫秒」仍留在該部門的人員名單。 此作法帶來了兩大架構優勢： 
	 * 1. <b>免維護額外實體表：</b> 寫入端無需硬建一張雙寫的關聯表，消除 Lock Contention。 
	 * 2. <b>絕對 CQRS 隔離：</b> 拒絕向 Query Side 妥協，堅守 Command Side 業務邏輯的自給自足。
	 * </pre>
	 *
	 * @param tenantId     多租戶識別碼 (字串格式，配合 Event Store 查詢)
	 * @param departmentId 目標推演的部門 ID
	 * @return 精準推演出的現役員工 ID 集合 (Set)
	 */
	private Set<String> rehydrateEmployeeIds(String tenantId, String departmentId) {
		// 1. 從事件庫撈出該部門從創立至今的「所有」事件流
		List<DomainEvent> historyEvents = eventStorer.loadEvents(tenantId, "Department", departmentId);

		// 2. 準備一個 HashSet 容器來裝載動態推演結果 (O(1) 增刪效能)
		Set<String> activeEmployees = new HashSet<>();

		// 3. 執行狀態折疊 (Event Folding) - 完美支援 Java 16+ Pattern Matching for switch
		for (DomainEvent event : historyEvents) {
			switch (event) {
			case EmployeeAssignedToDepartmentEvent e -> {
				// 歷史軌跡：有人被指派進來，加入名單
				activeEmployees.add(e.getEmployeeId());
			}
			case EmployeeUnassignedFromDepartmentEvent e -> {
				// 歷史軌跡：有人被移出，從名單剔除
				activeEmployees.remove(e.getEmployeeId());
			}
			default -> {
				// 忽略其他與人數無關的事件 (如 DepartmentRenamedEvent 等)，維持推演效能
			}
			}
		}
		return activeEmployees;
	}
}