package com.example.demo.application.domain.dept.aggregate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

import com.example.demo.application.domain.dept.aggregate.vo.DepartmentCode;
import com.example.demo.application.domain.dept.aggregate.vo.DepartmentId;
import com.example.demo.application.domain.dept.aggregate.vo.DepartmentStatus;
import com.example.demo.application.domain.dept.aggregate.vo.TenantId;
import com.example.demo.application.domain.dept.event.DepartmentCreatedEvent;
import com.example.demo.application.domain.dept.event.DepartmentDeletedEvent;
import com.example.demo.application.domain.dept.event.DepartmentDisabledEvent;
import com.example.demo.application.domain.dept.event.DepartmentMergedEvent;
import com.example.demo.application.domain.dept.event.DepartmentMovedEvent;
import com.example.demo.application.domain.dept.event.DepartmentRenamedEvent;
import com.example.demo.application.domain.dept.event.DepartmentRestoredEvent;
import com.example.demo.application.domain.dept.event.DepartmentSortOrderChangedEvent;
import com.example.demo.application.domain.dept.event.EmployeeAssignedToDepartmentEvent;
import com.example.demo.application.domain.dept.event.EmployeeUnassignedFromDepartmentEvent;
import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.domain.shared.exception.DomainException;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Department Aggregate Root (領域層/寫入端 - 部門聚合根)
 *
 * <p>
 * 這是 CQRS 架構與戰術 DDD (Tactical Domain-Driven Design) 中的寫入模型核心。 * 🎯
 * <b>三大核心聚合職責：</b> 1. <b>保護業務規則不變量 (Invariants)：</b>
 * 負責維護組織變更的原子性，自我檢核狀態一致性，嚴格拒絕非法的業務請求。 2. <b>封裝內部狀態：</b> 外部 Command Service
 * 無法直接修改此類別的任何屬性，必須透過具備業務語義的方法 (如: moveTo, mergeInto) 驅動狀態流轉。 3. <b>發布領域事件：</b>
 * 狀態變更成功後，負責在記憶體中註冊對應的領域事件 (Domain Events)，作為驅動 CQRS 唯讀端投影同步與歷史事件庫 (Event
 * Store) 的元數據燃料。 * ⚖️ <b>輕量化與併發設計備註 (Lightweight Aggregate Principle)：</b>
 * 為保持聚合根的極致輕量化並徹底消滅資料庫併發死鎖 (Concurrency Contention Lock)， 本實體**刻意不包含**任何員工實體清單
 * (不出現 {@code List<Employee>})。 取而代之的是引入極其輕量的 {@code activeEmployeeCount}
 * 作為領域防護計數器。 具體的人員指派與名單追蹤，全數交由 Application Service 透過 Event Sourcing Replay 推演，
 * 實現了寫入端絕對強一致性與極致高吞吐的完美平衡。
 * </p>
 */
@Getter
@Entity
@Table(name = "departments", indexes = { @Index(name = "idx_dept_tenant_parent", columnList = "tenant_id, parent_id"),
		@Index(name = "idx_dept_tenant_status", columnList = "tenant_id, status, deleted") }, uniqueConstraints = {
				@UniqueConstraint(name = "uk_dept_tenant_code", columnNames = { "tenant_id", "code" }) })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department {

	@EmbeddedId
	private DepartmentId id;

	@Embedded
	@AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false, updatable = false))
	private TenantId tenantId;

	@AttributeOverride(name = "value", column = @Column(name = "parent_id"))
	private DepartmentId parentId;

	@Embedded
	@AttributeOverride(name = "value", column = @Column(name = "code", nullable = false, length = 50))
	private DepartmentCode code;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private DepartmentStatus status;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Version
	private long version;

	// ==================================================
	// 審計稽核軌跡 (Audit Trail Attributes)
	// ==================================================
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "created_by", nullable = false, updatable = false, length = 100)
	private String createdBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "updated_by", nullable = false, length = 100)
	private String updatedBy;

	// ==================================================
	// 核心領域防護狀態 (Core Invariant State)
	// ==================================================

	/**
	 * 內部防禦性計數器 (僅限 Command 端內部使用)。
	 * 
	 * <pre>
	 * <b>領域防護核心 (Invariant Defender)：</b> 純粹用於守護「部門若尚有人員編制則絕對不可停用/合併」的領域不變量。
	 * 透過維護這個輕量級的整數，聚合根無需將數千名員工的關聯表載入記憶體， 即可在 O(1) 的時間複雜度下完成強一致性 (Strong
	 * Consistency) 的生死檢核，此屬性絕對不對外暴露給 Query 端。
	 * </pre>
	 */
	@Column(name = "active_employee_count", nullable = false)
	private int activeEmployeeCount = 0;

	// ==================================================
	// 時光機與邏輯刪除屬性 (Temporal Lifecycle Attributes)
	// ==================================================

	@Column(name = "deleted", nullable = false)
	private boolean deleted;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "deleted_by", length = 100)
	private String deletedBy;

	// ==================================================
	// 領域事件基礎設施中樞 (Domain Event Infrastructure)
	// ==================================================

	@Transient
	private final List<DomainEvent> domainEvents = new ArrayList<>();

	@DomainEvents
	public List<DomainEvent> getDomainEvents() {
		return Collections.unmodifiableList(domainEvents);
	}

	@AfterDomainEventPublication
	public void clearDomainEvents() {
		this.domainEvents.clear();
	}

	private void raise(DomainEvent event) {
		domainEvents.add(event);
	}

	// ==================================================
	// Constructor (嚴密閉合的建構子)
	// ==================================================

	private Department(TenantId tenantId, DepartmentId id, DepartmentId parentId, DepartmentCode code, String name,
			DepartmentStatus status, int sortOrder, boolean deleted, Instant deletedAt, String deletedBy,
			Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {
		this.tenantId = Objects.requireNonNull(tenantId, "TenantId required");
		this.id = Objects.requireNonNull(id, "DepartmentId required");
		this.parentId = parentId;
		this.code = Objects.requireNonNull(code, "DepartmentCode required");
		changeNameInternal(name);
		this.status = Objects.requireNonNull(status, "DepartmentStatus required");
		this.sortOrder = sortOrder;
		this.deleted = deleted;
		this.deletedAt = deletedAt;
		this.deletedBy = deletedBy;
		this.createdAt = Objects.requireNonNull(createdAt);
		this.createdBy = Objects.requireNonNull(createdBy);
		this.updatedAt = Objects.requireNonNull(updatedAt);
		this.updatedBy = Objects.requireNonNull(updatedBy);
	}

	// ==================================================
	// Factory Methods (充血領域工廠方法)
	// ==================================================

	public static Department createRoot(TenantId tenantId, DepartmentId id, DepartmentCode code, String name,
			String operator) {
		Instant now = Instant.now();
		Department dept = new Department(tenantId, id, null, code, name, DepartmentStatus.ACTIVE, 0, false, null, null,
				now, operator, now, operator);
		dept.raise(
				new DepartmentCreatedEvent(tenantId.getValue(), id.getValue(), null, code.getValue(), name, operator));
		return dept;
	}

	public static Department createChild(TenantId tenantId, DepartmentId id, DepartmentId parentId, DepartmentCode code,
			String name, String operator) {
		if (parentId == null) {
			throw new DomainException("Parent department specification is required for child creation");
		}
		Instant now = Instant.now();
		Department dept = new Department(tenantId, id, parentId, code, name, DepartmentStatus.ACTIVE, 0, false, null,
				null, now, operator, now, operator);
		dept.raise(new DepartmentCreatedEvent(tenantId.getValue(), id.getValue(), parentId.getValue(), code.getValue(),
				name, operator));
		return dept;
	}

	// ==================================================
	// Business Behaviors (充血業務行為矩陣)
	// ==================================================

	public void rename(String newName, String operator) {
		validateNotDeleted();
		validateActive();
		changeNameInternal(newName);
		touch(operator);
		raise(new DepartmentRenamedEvent(this.tenantId.getValue(), this.id.getValue(), newName, operator));
	}

	public void moveToRoot(String operator) {
		if (this.parentId == null)
			return;
		String oldParentId = this.parentId.getValue();
		String subtreeRootId = this.id.getValue();

		this.parentId = null;
		touch(operator);

		raise(new DepartmentMovedEvent(this.tenantId.getValue(), this.id.getValue(), oldParentId, null, operator,
				subtreeRootId));
	}

	public void moveTo(DepartmentId newParentId, String operator) {
		Objects.requireNonNull(newParentId, "Target parent ID cannot be null for movement");
		Objects.requireNonNull(operator, "Operator identity cannot be null");
		validateNotDeleted();
		validateActive();

		String oldParentIdValue = this.parentId != null ? this.parentId.getValue() : null;
		String newParentIdValue = newParentId.getValue();
		String subtreeRootId = this.id.getValue();

		if (newParentIdValue.equals(oldParentIdValue))
			return;

		this.parentId = newParentId;
		touch(operator);

		raise(new DepartmentMovedEvent(this.tenantId.getValue(), this.id.getValue(), oldParentIdValue, newParentIdValue,
				operator, subtreeRootId));
	}

	public void restore(String operator) {
		Objects.requireNonNull(operator, "Operator required for restoration trail");
		if (!this.deleted) {
			throw new DomainException("Department is currently active and alive, cannot trigger restore behavior.");
		}

		this.deleted = false;
		this.deletedAt = null;
		this.deletedBy = null;
		touch(operator);

		String parentIdValue = this.parentId != null ? this.parentId.getValue() : null;
		raise(new DepartmentRestoredEvent(this.tenantId.getValue(), operator, this.id.getValue(), parentIdValue,
				this.status.name(), this.code.getValue(), this.name));
	}

	public void delete(String operator) {
		Objects.requireNonNull(operator, "Delete operator identity required");
		if (this.parentId == null) {
			throw new DomainException(
					"Root framework constraint: Supreme root department cannot be deleted from organization table");
		}
		if (this.deleted) {
			throw new DomainException("Idempotency guard: Target department is already in logically deleted state");
		}

		Instant now = Instant.now();
		this.deleted = true;
		this.deletedAt = now;
		this.deletedBy = operator;
		this.updatedAt = now;
		this.updatedBy = operator;

		raise(new DepartmentDeletedEvent(this.tenantId.getValue(), this.id.getValue(), operator));
	}

	/**
	 * 業務上行政停用部門。
	 * <p>
	 * 觸發前將進行絕對的領域防禦檢核，確保部門轄下已無任何人員編制。
	 * </p>
	 */
	public void disable(String operator) {
		validateNotDeleted();
		validateActive();

		// 絕對零依賴的領域防護
		if (this.activeEmployeeCount > 0) {
			throw new DomainException(String.format("Aggregate Frontier Defense: 無法停用，部門 [%s] 尚有 %d 名直屬人員編制。",
					this.name, this.activeEmployeeCount));
		}

		this.status = DepartmentStatus.DISABLED;
		touch(operator);

		raise(new DepartmentDisabledEvent(this.tenantId.getValue(), this.id.getValue(), operator));
	}

	public void changeSortOrder(int sortOrder, String operator) {
		validateNotDeleted();
		if (sortOrder < 0) {
			throw new DomainException(
					"Business invariant constraint: Sort order weight value cannot be negative (must >= 0)");
		}
		this.sortOrder = sortOrder;
		touch(operator);

		raise(new DepartmentSortOrderChangedEvent(this.tenantId.getValue(), this.id.getValue(), sortOrder, operator));
	}

	/**
	 * 人員指派：將員工編制分配進此部門聚合範圍內。
	 * <p>
	 * 💡 <b>狀態演進：</b> 寫入端僅增加 {@code activeEmployeeCount} 計數器，並發射「人員指派事件」。
	 * 藉由維護此輕量級計數，即刻具備防禦非法停用的能力。
	 * </p>
	 */
	public void assignEmployee(String employeeId, String operator) {
		validateNotDeleted();
		validateActive();
		if (employeeId == null || employeeId.isBlank()) {
			throw new IllegalArgumentException("Target Employee ID cannot be blank for assignment");
		}

		// 維護內部防禦狀態
		this.activeEmployeeCount++;
		touch(operator);

		raise(new EmployeeAssignedToDepartmentEvent(this.tenantId.getValue(), this.id.getValue(), employeeId,
				operator));
	}

	/**
	 * 人員解除指派：將特定員工從本部門的編制中徹底移出。
	 * <p>
	 * 💡 <b>特殊業務寬限規則：</b> 為了配合企業進行組織整併等過渡期行政維運， 即使部門目前已處於 DISABLED
	 * 狀態，本聚合根依然破例允許執行「移出人員」，確保凍結組織能順利排空。 同時同步遞減 {@code activeEmployeeCount}。
	 * </p>
	 */
	public void unassignEmployee(String employeeId, String operator) {
		validateNotDeleted();

		if (employeeId == null || employeeId.isBlank()) {
			throw new IllegalArgumentException("Target Employee ID cannot be blank for unassignment");
		}

		// 維護內部防禦狀態 (防禦低於零的異常邊界)
		if (this.activeEmployeeCount > 0) {
			this.activeEmployeeCount--;
		}
		touch(operator);

		raise(new EmployeeUnassignedFromDepartmentEvent(this.tenantId.getValue(), this.id.getValue(), employeeId,
				operator));
	}

	/**
	 * 將本部門宣告為「已被合併」，並指向吸收我們的目標部門。
	 * 
	 * <pre>
	 * 這是組織重組 (Restructure) 流程的最終步驟。 必須在 Application Service 抽乾本部門所有人員與資產後方可呼叫。
	 * </pre>
	 * 
	 * @param targetDeptId 吸收我們的目標部門 ID
	 * 
	 * @param operator     操作者 ID
	 * @throws DomainException 若部門內尚有員工未清空，將觸發不變量防護拋錯
	 */
	public void markAsMergedInto(DepartmentId targetDeptId, String operator) {
		validateNotDeleted();
		validateActive();

		// 🛡領域防護：確認底下的人員與子部門都已經被抽乾了，才能功成身退
		if (this.activeEmployeeCount > 0) {
			throw new DomainException("合併程序異常：部門尚有直屬員工未完成轉移");
		}

		this.status = DepartmentStatus.DISABLED;
		touch(operator);

		// 發布專屬的重組事件，賦予後續資料倉儲 (Data Warehouse) 強大的分析價值
		raise(new DepartmentMergedEvent(this.tenantId.getValue(), operator, // 對齊父類別的 operator
				this.id.getValue(), // 被合併的來源部門
				targetDeptId.getValue() // 吸收資產的目標部門
		));
	}

	// ==================================================
	// Internal Validations (聚合不變量內部檢核防禦牆)
	// ==================================================

	private void validateNotDeleted() {
		if (this.deleted) {
			throw new DomainException(
					"Aggregate Frontier Defense: Operation rejected because target department is logically deleted");
		}
	}

	private void validateActive() {
		if (this.status == DepartmentStatus.DISABLED) {
			throw new DomainException(
					"Aggregate Frontier Defense: Operation rejected because target department status is currently DISABLED");
		}
	}

	private void changeNameInternal(String name) {
		if (name == null || name.isBlank()) {
			throw new DomainException(
					"Domain rule validation error: Department name description cannot be empty or blank");
		}
		if (name.length() > 200) {
			throw new DomainException(
					"Domain rule validation error: Department name length exceeds physical database storage limitation (max 200 characters)");
		}
		this.name = name;
	}

	private void touch(String operator) {
		this.updatedAt = Instant.now();
		this.updatedBy = operator;
	}
}