package com.example.demo.application.projection.strategy.view;

import com.example.demo.application.domain.shared.event.DomainEvent;

/**
 * View Projection Strategy (視圖投影策略合約)
 *
 * <pre>
 * 定義處理單一部門視圖屬性事件的策略標準。 利用 Java 泛型 (Generics) 綁定具體的事件型別，提供強型別的執行環境。 
 * 實作類別不需考慮 Transaction 與 冪等防護，只需專注於呼叫 Port 執行資料庫更新。
 * </pre>
 *
 * @param <T> 該策略支援的具體領域事件型別
 */
public interface ViewProjectionStrategy<T extends DomainEvent> {

	/**
	 * 宣告此策略支援的事件型別，供 Projector 啟動時建立路由表。
	 */
	Class<T> supportedEvent();

	/**
	 * 執行具體的投影邏輯 (更新 department_views 扁平視圖表)
	 */
	void execute(T event);
}