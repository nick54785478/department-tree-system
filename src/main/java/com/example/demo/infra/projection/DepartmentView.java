package com.example.demo.infra.projection;

import com.example.demo.iface.event.DepartmentRollUpProjectionHandler;
import com.example.demo.iface.event.DepartmentViewProjectionHandler;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DepartmentView (讀取端投影模型 - 部門基本資料與多維扁平視圖)
 *
 * <pre>
 * 專供前端、報表以及查詢 API（Query Side）使用的<b>讀取端優化投影模型 (Read Model View)</b>。 
 * 
 * <b>讀寫分離與高吞吐設計</b>： 
 * 本表完全不參與寫入端 (Command Side) 的業務運算。本表的資料變更，純粹是由 {@link DepartmentViewProjectionHandler} 
 * 與 {@link DepartmentRollUpProjectionHandler} 在攔截到寫入端成功 Commit 廣播出的領域事件後，
 * 透過高效的 Spring JDBC 原生 SQL 進行異步/同步更新。 內部包含了預先滾動計算好的各層級人數統計欄位，達成了極致的前端 $O(1)$ 單表讀取效能。
 * </pre>
 */
@Getter
@Entity
@Table(name = "department_views")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepartmentView {

	/**
	 * 部門唯一識別碼 (與寫入端 Aggregate ID 物理對齊)
	 */
	@Id
	@Column(name = "id", length = 50)
	private String id;

	/**
	 * 多租戶防護核心識別碼
	 */
	@Column(name = "tenant_id", nullable = false, updatable = false, length = 50)
	private String tenantId;

	/**
	 * 直接上級直屬父部門 ID (若為一級部門則儲存 null)
	 */
	@Column(name = "parent_id", length = 50)
	private String parentId;

	/**
	 * 業務層面人類可讀的部門編碼 (如: "RD-CORE-02")
	 */
	@Column(name = "code", nullable = false, length = 50)
	private String code;

	/**
	 * 部門顯示名稱
	 */
	@Column(name = "name", nullable = false, length = 200)
	private String name;

	/**
	 * 部門生命週期業務狀態字串。
	 * <p>
	 * 包含 "ACTIVE" (啟用)、"DISABLED" (停用)、以及為了時光機復活機制作為備份的 "DELETED" (已邏輯刪除) 等狀態。
	 * </p>
	 */
	@Column(name = "status", nullable = false, length = 20)
	private String status;

	/**
	 * 同一層級組織在 UI 呈現上的顯示排序權重 (數值越小通常在前端排得越前面)
	 */
	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	// =========================================================
	// 統計欄位區 (由 Roll-Up Projection Handler 異步攔截事件更新)
	// =========================================================

	/**
	 * 部門直屬人數 (Direct Headcount)。
	 * <p>
	 * 代表組織編制中，直接與此部門綁定、不包含轄下附屬子單位的實際直屬員工總人數。
	 * </p>
	 */
	@Column(name = "direct_headcount", nullable = false)
	private int directHeadcount = 0;

	/**
	 * 組織總人數 (Total Headcount)。
	 * <p>
	 * 利用閉包表幾何關係，向上滾動 (Roll-up) 遞增算出的總兵力。包含該部門自身編制以及旗下所有子孫分支機構的人數總和。
	 * </p>
	 */
	@Column(name = "total_headcount", nullable = false)
	private int totalHeadcount = 0;

	/**
	 * 全參數唯讀模型視圖建構子。
	 */
	public DepartmentView(String id, String tenantId, String parentId, String code, String name, String status,
			int sortOrder) {
		this.id = id;
		this.tenantId = tenantId;
		this.parentId = parentId;
		this.code = code;
		this.name = name;
		this.status = status;
		this.sortOrder = sortOrder;
	}

	/**
	 * 備註/安全網：保留給 JPA 使用者手動操作或單元測試模擬實體狀態時的輔助 Mutation 方法。
	 * <p>
	 * 實務生產環境中，為了對抗高併發並消滅 Lost Update，此欄位多由
	 * {@code DepartmentRollUpProjectionHandlerAdapter} 透過資料庫原生的
	 * {@code SET direct_headcount = direct_headcount + :delta} 原子性 SQL 語句直接更新。
	 * </p>
	 *
	 * @param delta 人數變化步長 (調入傳入正數如 1，調出傳入負數如 -1)
	 */
	public void incrementDirectHeadcount(int delta) {
		this.directHeadcount += delta;
	}

	/**
	 * 備註/安全網：保留給 JPA 使用者手動操作實體時的總人數輔助變更方法。
	 *
	 * @param delta 子樹組織人數變化步長 (增加傳入正數如 1，扣除傳入負數如 -1)
	 */
	public void incrementTotalHeadcount(int delta) {
		this.totalHeadcount += delta;
	}
}