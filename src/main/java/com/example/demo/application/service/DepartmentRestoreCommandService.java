package com.example.demo.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.domain.dept.aggregate.Department;
import com.example.demo.application.domain.dept.aggregate.vo.DepartmentId;
import com.example.demo.application.domain.dept.aggregate.vo.TenantId;
import com.example.demo.application.domain.dept.repository.DepartmentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Department Restore Command Service (應用層 - 部門復原命令服務)
 *
 * <pre>
 * 專責執行將「已邏輯刪除的部門」從前世死亡狀態中復活、重返組織樹的使用案例 (Undelete Use Case)。 
 * 混合式架構 + DDD Value Object 專用定版。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentRestoreCommandService {

	/**
	 * 注入寫入端的領域倉儲 Port 通道 (使用依賴反轉，完美實踐 DIP)
	 */
	private final DepartmentRepository departmentRepository;

	/**
	 * 執行部門復活業務調度。
	 * <p>
	 * 包含核心的「孤兒節點防禦檢查」：如果發現該部門的前世老爸已經死了， 為了防止其復活後成為游離在組織樹之外的孤兒，會主動觸發變更將其掛載到 Root
	 * 節點下。
	 * </p>
	 *
	 * @param tenantIdStr     外部傳入的租戶字串
	 * @param departmentIdStr 外部傳入的欲復活部門 ID 字串
	 * @param operatorId      執行此復活操作的管理員識別碼
	 * @throws IllegalArgumentException 若欲復活的部門在系統寫入端根本不存在
	 */
	@Transactional
	public void execute(String tenantIdStr, String departmentIdStr, String operatorId) {
		log.info("User {} attempting to restore department: {}", operatorId, departmentIdStr);

		// 類型邊界轉換：將基礎設施層的裸型別 (String) 轉換為領域層認識、具備自我業務驗證的值物件 (Value Object)
		TenantId tenantId = new TenantId(tenantIdStr);
		DepartmentId departmentId = new DepartmentId(departmentIdStr);

		// 1. 從寫入端資料庫撈出真實的聚合根實體 (使用嚴格的租戶邊界隔離查詢，杜絕 IDOR 越權漏洞)
		Department department = departmentRepository.findByTenantIdAndId(tenantId, departmentId)
				.orElseThrow(() -> new IllegalArgumentException("Department does not exist. ID: " + departmentIdStr));

		// 2. 幾何空間防禦：孤兒節點防禦檢查 (Orphan Node Defense)
		if (department.getParentId() != null) {

			// 遵循純粹的 DDD 充血模型精神：不再將「老爸是否邏輯刪除」的邏輯推給特製的 SQL 語法，
			// 而是將老爸實體完整取出後，在 Java 記憶體層次去 map 檢查其實體身上的 `!parent.isDeleted()` 業務狀態。
			boolean isParentAlive = departmentRepository.findByTenantIdAndId(tenantId, department.getParentId())
					.map(parent -> !parent.isDeleted()).orElse(false); // 如果在 DB 連老爸的紀錄都徹底找不到了，也視為已死

			// 防禦觸發：如果老爸已經死了
			if (!isParentAlive) {
				log.warn(
						"Parent department is dead. Reattaching restored department {} to ROOT to prevent orphan tree gap.",
						departmentIdStr);
				// 呼叫聚合根內部的移動行為，將 parentId 設為 null，並在內部發布其專屬的 DepartmentMovedEvent
				department.moveToRoot(operatorId);
			}
		}

		// 3. 呼叫聚合根內部的核心復原邏輯。
		// 實體內部的 deleted 標記會被清空、抹除死亡稽核軌跡，並正式產生攜帶生前狀態丰富化資訊的 `DepartmentRestoredEvent`
		department.restore(operatorId);

		// 4. 儲存實體。
		// 這會發出強大的 Hibernate UPDATE SQL，並且在事務 commit 前，
		// 自動將實體內累積的 MovedEvent (若有觸發防禦) 與 RestoredEvent 一併派發出去，驅動唯讀端大同步！
		departmentRepository.save(department);

		log.info("Successfully restored department: {}", departmentIdStr);
	}
}