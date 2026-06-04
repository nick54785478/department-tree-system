package com.example.demo.infra.event.dispatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.example.demo.application.domain.dept.event.DepartmentCreatedEvent;
import com.example.demo.application.domain.shared.event.DomainEvent;

/**
 * Projection Dispatcher (基礎設施/讀取端 - 投影事件分派器)
 *
 * <pre>
 * 專責在 Rebuild (全域系統重建/事件重播) 階段，將領域事件精準且順序正確地派發給對應的 Projector 策略模組。
 *
 * <b>架構設計亮點</b>： 
 * 1. <b>一對多廣播模式：</b> 支援「單一事件觸發多個相依投影行為」的註冊模型（例如：{@link DepartmentCreatedEvent} 必須同時派發給
 * ViewProjector 與 TreeProjector）。 
 * 2. <b>開閉原則 (OCP) 典範：</b> 本類別作為純粹的核心路由匯流排，未來隨著業務擴充不論增加多少個全新的 Projector，此核心分派類別的代碼皆「完全不動如山」，具備極佳的健壯性。 
 * 3. <b>型別安全防護：</b> 在註冊階段透過泛型手法壓制並封裝了強轉型風險，對外部調用者展現出極致乾淨的無變形合約。
 * </pre>
 */
public class ProjectionDispatcher {

	/**
	 * * 內部核心路由表：儲存 Mapping 規則。 Key 為事件的 Class 類別型別，Value 則為該事件被觸發時，必須依序執行的所有處理邏輯
	 * (Consumers) 清單。
	 */
	private final Map<Class<? extends DomainEvent>, List<Consumer<DomainEvent>>> dispatchTable = new HashMap<>();

	/**
	 * 註冊事件與對應的投影處理器。
	 * <p>
	 * 運用 Java 泛型捕捉技術，確保傳入的 Lambda 處理邏輯所預期的強型別，與目標事件型別絕對匹配。
	 * </p>
	 *
	 * @param eventClass 目標領域事件的具體 Class 型別 (如 DepartmentCreatedEvent.class)
	 * @param handler    具體的唯讀端視圖更新或幾何路徑計算邏輯 (Lambda 或方法參照)
	 * @param <T>        強型別約束：確保傳入的 Handler Consumer 型別與 Event 類別完全一致
	 */
	@SuppressWarnings("unchecked")
	public <T extends DomainEvent> void register(Class<T> eventClass, Consumer<T> handler) {
		// 執行期線程防護：若該事件尚未被任何 Projector 註冊過，則初始化一個專屬的 ArrayList
		dispatchTable.computeIfAbsent(eventClass, k -> new ArrayList<>())
				// 關鍵架構黑魔法：在此封裝一層外殼 Consumer，將外部丟入的具體 T (例如 DepartmentCreatedEvent)
				// 安全地抹除並向下轉型為全域通用的基底 DomainEvent，從而塞入統一的 dispatchTable 中，隱藏複雜的泛型宣告。
				.add(event -> handler.accept((T) event));
	}

	/**
	 * 執行事件的全域路由分派。
	 * <p>
	 * 在全域事件重播（Global Replay）時，遍歷歷史流並依序將事件餵入此方法，觸發連鎖視圖重建。
	 * </p>
	 *
	 * @param event 繼承自基底類別、目前正在被重播的單一歷史領域事件
	 */
	public void dispatch(DomainEvent event) {
		if (event == null)
			return;

		// 透過事件的具體執行期類別 (Runtime Class) 找出所有註冊的處理器。
		// 防禦性設計：若無任何 Projector 關心此事件，則回傳 Collections.emptyList()，完美杜絕
		// NullPointerException。
		List<Consumer<DomainEvent>> handlers = dispatchTable.getOrDefault(event.getClass(), Collections.emptyList());

		// 順序執行所有與此事件綁定的投影邏輯
		for (Consumer<DomainEvent> handler : handlers) {
			handler.accept(event);
		}
	}
}