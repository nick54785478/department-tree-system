package com.example.demo.iface.rest;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.application.service.DepartmentCommandService;
import com.example.demo.application.service.DepartmentRestoreCommandService;
import com.example.demo.application.service.DepartmentRestructureCommandService;
import com.example.demo.application.shared.command.AssignEmployeeCommand;
import com.example.demo.application.shared.command.ChangeDepartmentSortOrderCommand;
import com.example.demo.application.shared.command.CreateDepartmentCommand;
import com.example.demo.application.shared.command.CreateDepartmentTreeCommand;
import com.example.demo.application.shared.command.DeleteDepartmentCommand;
import com.example.demo.application.shared.command.DisableDepartmentCommand;
import com.example.demo.application.shared.command.MoveDepartmentCommand;
import com.example.demo.application.shared.command.RenameDepartmentCommand;
import com.example.demo.application.shared.command.UnassignEmployeeCommand;
import com.example.demo.iface.dto.req.AssignEmployeeResource;
import com.example.demo.iface.dto.req.ChangeSortOrderResource;
import com.example.demo.iface.dto.req.CreateDepartmentResource;
import com.example.demo.iface.dto.req.CreateDepartmentTreeNodeResource;
import com.example.demo.iface.dto.req.MergeDepartmentResource;
import com.example.demo.iface.dto.req.MoveDepartmentResource;
import com.example.demo.iface.dto.req.RenameDepartmentResource;
import com.example.demo.iface.dto.res.DepartmentAssignedResource;
import com.example.demo.iface.dto.res.DepartmentCreatedResource;
import com.example.demo.iface.dto.res.DepartmentDeletedResource;
import com.example.demo.iface.dto.res.DepartmentMovedResource;
import com.example.demo.iface.dto.res.DepartmentRestoredResource;
import com.example.demo.iface.dto.res.DepartmentTreeCreatedResource;
import com.example.demo.iface.dto.res.DepartmentUnassignedResource;
import com.example.demo.iface.dto.res.DepartmentsMergedResource;
import com.example.demo.iface.dto.res.SortOrderChangedResource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Department Command Controller (基礎設施層 - 部門寫入端 API 控制器)
 *
 * <pre>
 * 遵循 CQRS (Command Query Responsibility Segregation) 架構規範， 專責接收並處理所有會改變「部門聚合根
 * (Department Aggregate)」業務狀態的外部 Command 請求。
 * 職責範疇涵蓋：部門建立、從屬關係搬移、樹狀結構邏輯刪除、人員分派、更名、停用、以及時光機復活復原。
 *
 * <b>多維度架構安全性設計：</b> 本控制器所有暴露的 API 皆強制要求透過 HTTP Header 傳入
 * X-Tenant-Id (多租戶邏輯防護隔離牆) 與  X-User-Id (操作者稽核軌跡識別碼)。
 * 在進入Application Layer 之前，即由 Web 門面抽取出值物件元數據， 配合內層 Repository 實現強力的 IDOR (越權存取) 防禦，
 * 嚴格防止跨租戶資料外洩或未授權的組織變更。
 * </pre>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/departments")
public class DepartmentCommandController {

	/**
	 * 注入寫入端核心命令應用服務
	 */
	private final DepartmentCommandService commandService;
	private final DepartmentRestructureCommandService restructureCommandService;

	/**
	 * 注入混合式時光機復活專用應用服務
	 */
	private final DepartmentRestoreCommandService restoreCommandService;

	// =====================================================
	// LIFECYCLE (生命週期操作)
	// =====================================================

	/**
	 * 建立部門 (一級根部門或常規子部門)。
	 *
	 * @param tenantId 租戶識別碼 (強制由 Gateway/前端於 HTTP Header 帶入)
	 * @param operator 當前操作者 ID 帳號 (用於審計軌跡，From HTTP Header)
	 * @param resource 建立部門所需的 Payload 資源 DTO
	 * @return {@code 200 OK} 夾帶建立成功業務狀態回應
	 */
	@PostMapping
	public ResponseEntity<DepartmentCreatedResource> createDepartment(@RequestHeader("X-Tenant-Id") String tenantId,
			@RequestHeader("X-User-Id") String operator, @RequestBody CreateDepartmentResource resource) {
		// 將裸字串與 Payload 封裝為型別安全的應用層 Command
		CreateDepartmentCommand command = new CreateDepartmentCommand(tenantId, resource.id(), resource.parentId(),
				resource.code(), resource.name(), operator);
		commandService.createDepartment(command);
		return ResponseEntity.ok(new DepartmentCreatedResource("200", "Success"));
	}

	/**
	 * 刪除部門 (包含其轄下整棵子樹)。
	 * 
	 * <pre>
	 * <b>破壞性操作約定：</b> 此操作屬於高風險連鎖變更。應用層底層會調用閉包表（Closure Table）
	 * 一口氣拔除該節點及其下所有子孫節點的幾何路徑，並對整棵子樹實施批次邏輯刪除。
	 * </pre>
	 *
	 * @param tenantId     租戶識別碼 (From Header)
	 * @param operator     操作者 ID (From Header)
	 * @param departmentId 欲執行邏輯刪除的子樹根節點部門 ID (URL 路徑變數)
	 * @return {@code 200 OK} 刪除完成回應
	 */
	@DeleteMapping("/{departmentId}")
	public ResponseEntity<DepartmentDeletedResource> delete(@RequestHeader("X-Tenant-Id") String tenantId,
			@RequestHeader("X-User-Id") String operator, @PathVariable String departmentId) {
		commandService.delete(new DeleteDepartmentCommand(tenantId, departmentId, operator));
		return ResponseEntity.ok(new DepartmentDeletedResource("200", "Success"));
	}

	// =====================================================
	// STRUCTURE (組織架構異動)
	// =====================================================

	/**
	 * 移動部門 (調整組織從屬樹狀拓撲關係)。
	 * 
	 * <pre>
	 * 將指定部門及其旗下的所有附屬子樹結構，完整搬移、掛載到新的父部門節點底下。 底層投影會連帶驅動閉包表進行笛卡爾乘積級別的路徑重組。
	 * </pre>
	 *
	 * @param tenantId 租戶識別碼 (From Header)
	 * @param operator 操作者 ID (From Header)
	 * @param resource 包含目標部門 ID 與新父節點 ID 的 Payload DTO
	 * @return {@code 200 OK} 搬移成功回應
	 */
	@PostMapping("/move")
	public ResponseEntity<DepartmentMovedResource> moveDepartment(@RequestHeader("X-Tenant-Id") String tenantId,
			@RequestHeader("X-User-Id") String operator, @RequestBody MoveDepartmentResource resource) {
		MoveDepartmentCommand command = new MoveDepartmentCommand(tenantId, resource.departmentId(),
				resource.newParentId(), operator);
		commandService.moveDepartment(command);
		return ResponseEntity.ok(new DepartmentMovedResource("200", "Success"));
	}

	// =====================================================
	// MEMBERSHIP (人員分派)
	// =====================================================

	/**
	 * 將員工指派、分派至指定的目標部門。
	 * 
	 * <pre>
	 * <b>事件連鎖驅動：</b> 成功分派後會發布事件，非同步驅動唯讀端投影表（DepartmentView）
	 * 進行直屬人數與整條祖先路徑總人數的向上滾動（Roll-up）遞增更新。
	 * </pre>
	 *
	 * @param tenantId     租戶識別碼 (From Header)
	 * @param operator     操作者 ID (From Header)
	 * @param departmentId 目標加入的部門 ID (URL 路由參數)
	 * @param resource     包含員工 ID 的 Payload 資源包
	 * @return {@code 201 Created} 分派成功建立回應
	 */
	@PostMapping("/{id}/employees")
	public ResponseEntity<DepartmentAssignedResource> assignEmployee(@RequestHeader("X-Tenant-Id") String tenantId,
			@RequestHeader("X-User-Id") String operator, @PathVariable("id") String departmentId,
			@RequestBody AssignEmployeeResource resource) {
		AssignEmployeeCommand command = new AssignEmployeeCommand(tenantId, departmentId, resource.employeeId(),
				operator);
		commandService.assignEmployee(command);
		return new ResponseEntity<>(new DepartmentAssignedResource("200", "Success"), HttpStatus.CREATED);
	}

	/**
	 * 將特定員工從指定部門中移出（解除分派關聯）。
	 * <p>
	 * 遵循 RESTful 風格設計，使用 {@code DELETE} 方法針對資源組合（Department 與 Employee 的連結）進行移除操作。
	 * 唯讀端統計人數會自動連帶執行反向扣減（Delta = -1）。
	 * </p>
	 *
	 * @param tenantId     租戶識別碼 (From Header)
	 * @param operator     操作者 ID (From Header)
	 * @param departmentId 目標部門 ID
	 * @param employeeId   欲移出的員工唯一識別碼 (URL 路由參數)
	 * @return {@code 201 Created} 移除成功回應 (架構約定亦可考量翻轉為 204 No Content)
	 */
	@DeleteMapping("/{id}/employees/{employeeId}")
	public ResponseEntity<DepartmentUnassignedResource> unassignEmployee(@RequestHeader("X-Tenant-Id") String tenantId,
			@RequestHeader("X-User-Id") String operator, @PathVariable("id") String departmentId,
			@PathVariable("employeeId") String employeeId) {
		UnassignEmployeeCommand command = new UnassignEmployeeCommand(tenantId, departmentId, employeeId, operator);
		commandService.unassignEmployee(command);
		return new ResponseEntity<>(new DepartmentUnassignedResource("200", "Success"), HttpStatus.CREATED);
	}

	// =========================================================
	// ATTRIBUTE UPDATES (單一屬性更新 - 使用 PATCH)
	// =========================================================

	/**
	 * 部門更名。
	 * 
	 * <pre>
	 * 依據 RESTful 局部欄位更新語義，採用 PATCH 請求。名稱將會非同步同步至讀取端視圖。
	 * </pre>
	 *
	 * @return {@code 204 No Content} 代表指令同步執行成功，但無需回傳冗餘的 Body 載體
	 */
	@PatchMapping("/{id}/name")
	public ResponseEntity<Void> renameDepartment(@RequestHeader("X-Tenant-Id") String tenantId,
			@RequestHeader("X-User-Id") String operator, @PathVariable("id") String departmentId,
			@RequestBody RenameDepartmentResource resource) {
		RenameDepartmentCommand command = new RenameDepartmentCommand(tenantId, departmentId, resource.name(),
				operator);
		commandService.renameDepartment(command);
		return ResponseEntity.noContent().build();
	}

	/**
	 * 業務停用部門 (包含其下所有活著的子孫)。
	 * 
	 * <pre>
	 * 採用 PATCH 請求。被停用的組織將無法再被指派新員工，且前端組織樹渲染通常呈現禁用灰色。
	 * </pre>
	 *
	 * @return {@code 204 No Content}
	 */
	@PatchMapping("/{id}/disable")
	public ResponseEntity<Void> disableDepartment(@RequestHeader("X-Tenant-Id") String tenantId,
			@RequestHeader("X-User-Id") String operator, @PathVariable("id") String departmentId) {
		DisableDepartmentCommand command = new DisableDepartmentCommand(tenantId, departmentId, operator);
		commandService.disableDepartment(command);
		return ResponseEntity.noContent().build();
	}

	/**
	 * 調整部門在 UI 上同層級之中的顯示排序權重。
	 *
	 * @return {@code 204 No Content} 數值越小，在同級中排列越靠前
	 */
	@PatchMapping("/{id}/sort-order")
	public ResponseEntity<SortOrderChangedResource> changeSortOrder(@RequestHeader("X-Tenant-Id") String tenantId,
			@RequestHeader("X-User-Id") String operator, @PathVariable("id") String departmentId,
			@RequestBody ChangeSortOrderResource resource) {
		ChangeDepartmentSortOrderCommand command = new ChangeDepartmentSortOrderCommand(tenantId, departmentId,
				resource.sortOrder(), operator);
		commandService.changeSortOrder(command);
		return new ResponseEntity<>(new SortOrderChangedResource("200", "Success"), HttpStatus.OK);
	}

	/**
	 * 復原、復活已邏輯刪除的部門 (Undelete / Restore / 時光機復活)。
	 * 
	 * <pre>
	 * <b>RESTful 語義路由設計說明：</b> 在 REST 慣例中，針對資源執行的特定、非典型 CRUD 狀態大翻轉動作，通常採用
	 * {@code POST + /動詞} 的非對稱路由設計。 本入口會觸發強大的「孤兒節點防禦機制」：若生前老爸已死，復活後會自動重掛載到 Root 頂層。
	 * </pre>
	 *
	 * @param tenantId     租戶 ID (由 API Gateway 統一過濾並透過安全 Header 帶入)
	 * @param operator     操作管理員 ID
	 * @param departmentId 欲執行從地獄復活的部門 ID
	 * @return {@code 200 OK} 無 Payload 回傳
	 */
	@PostMapping("/{departmentId}/restore")
	public ResponseEntity<DepartmentRestoredResource> restoreDepartment(@RequestHeader("X-Tenant-Id") String tenantId,
			@RequestHeader("X-User-Id") String operator, @PathVariable String departmentId) {
		log.info("[API] Received request to restore department: {} for tenant: {}", departmentId, tenantId);

		// 1. 單純的門面轉發，解包基礎型別參數餵給內層的專屬 Use Case 服務
		restoreCommandService.execute(tenantId, departmentId, operator);

		// 2. 成功執行完畢，回傳 200 OK
		return new ResponseEntity<>(new DepartmentRestoredResource("200", "Success"), HttpStatus.OK);
	}

	/**
	 * 批次遞迴建立整棵複雜的部門組織樹 (Bulk Create Nested Tree)。
	 * <p>
	 * 適用於企業系統大初始化或外部 HR 系統大變更導入，利用接收一整顆巢狀 Resource，由上至下重構指令。
	 * </p>
	 *
	 * @param tenantId     租戶識別碼
	 * @param operator     操作者 ID
	 * @param requestNodes 包含多層級巢狀結構的根節點 Payload Resource
	 * @return {@code 201 Created} 批量建樹事務接受成功
	 */
	@PostMapping("/tree")
	public ResponseEntity<DepartmentTreeCreatedResource> createDepartmentTree(
			@RequestHeader("X-Tenant-Id") String tenantId, @RequestHeader("X-User-Id") String operator,
			@RequestBody CreateDepartmentTreeNodeResource requestNodes) {
		log.info("[API] Received request to bulk create department tree for root ID: {}", requestNodes.id());

		// 1. 將前端多層級的 Resource 遞迴解包、轉換為內部的巢狀 Command 樹
		CreateDepartmentTreeCommand rootCommand = mapToCommand(tenantId, operator, requestNodes);

		// 2. 轉發給 Application Service 處理，交由 Service 利用 DFS 深度優先演算法順序寫入 DB 并廣播事件
		commandService.createDepartmentTree(rootCommand);

		return new ResponseEntity<>(new DepartmentTreeCreatedResource("200", "Success"), HttpStatus.CREATED);
	}

	/**
	 * 執行跨聚合部門合併重組 (Merge Department Process)
	 *
	 * <pre>
	 * 💡 <b>CQRS Command Endpoint (寫入端操作入口)：</b>
	 * 觸發組織架構的重量級重組流程。將來源部門 (Source) 的所有直屬人員編制與子部門拓撲，
	 * 完整過繼並轉移至目標部門 (Target)，並將來源部門的業務生命週期標記為停用 (已合併)。
	 * 此 API 保證操作的絕對原子性 (Atomicity)。
	 * </pre>
	 *
	 * @param tenantId     多租戶識別碼 (透過 HTTP Header `X-Tenant-ID` 強制隔離存取邊界)
	 * @param operator     操作者/管理員 ID (透過 HTTP Header `X-User-ID` 傳遞，作為 Audit Trail
	 *                     稽核軌跡)
	 * @param sourceDeptId 被消滅/被合併的來源部門 ID (由 URL Path 傳入，明確指定操作資源主體)
	 * @param request      包含目標接收部門 ID 的 Request Body (Command Payload)
	 * @return 封裝了狀態碼與確認訊息的標準化回應 (HTTP 200 OK)
	 */
	@PostMapping("/{sourceDeptId}/merge")
	public ResponseEntity<DepartmentsMergedResource> mergeDepartment(@RequestHeader("X-Tenant-ID") String tenantId,
			@RequestHeader("X-User-ID") String operator, @PathVariable String sourceDeptId,
			@RequestBody MergeDepartmentResource request) {

		// 🛡️ 邊界防禦與請求轉發：
		// Controller 僅作 HTTP 協議解析，直接將裸字串參數轉交給 Application Service 進行領域層編排
		restructureCommandService.mergeDepartment(tenantId, sourceDeptId, request.targetDeptId(), operator);

		// 💡 CQRS 回應哲學：
		// 由於寫入端不負責讀取查詢，且讀取端閉包表 (Closure Table) 正在異步重組中，
		// 因此 Command API 僅回傳業務執行成功的確認訊息 (Ack)，不夾帶任何最新的部門狀態實體。
		return ResponseEntity.ok(new DepartmentsMergedResource("200", "部門重組成功：資產已全數轉移"));
	}

	/**
	 * 內部遞迴映射方法：將巢狀的前端 Resource 轉譯為型別安全的巢狀系統指令 (Resource -> Command Tree)
	 * <p>
	 * 此方法採用嚴格遞迴，完美保留多層級結構的深度完整性，不遺漏任何子節點。
	 * </p>
	 */
	private CreateDepartmentTreeCommand mapToCommand(String tenantId, String operatorId,
			CreateDepartmentTreeNodeResource resource) {
		if (resource == null) {
			return null;
		}

		// 遞迴降解轉換子節點列表 (Children)
		List<CreateDepartmentTreeCommand> childCommands = new ArrayList<>();
		if (resource.children() != null && !resource.children().isEmpty()) {
			for (CreateDepartmentTreeNodeResource childResource : resource.children()) {
				childCommands.add(mapToCommand(tenantId, operatorId, childResource));
			}
		}

		// 構建與之對齊的 Command 元件
		return new CreateDepartmentTreeCommand(tenantId, operatorId, resource.id(), resource.parentId(),
				resource.code(), resource.name(), childCommands);
	}

}