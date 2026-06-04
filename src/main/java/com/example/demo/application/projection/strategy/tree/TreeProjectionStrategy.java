package com.example.demo.application.projection.strategy.tree;

import com.example.demo.application.domain.shared.event.DomainEvent;

/**
 * Tree Projection Strategy (樹狀投影策略合約)
 *
 * <pre>
 * 定義處理單一部門樹狀事件的策略標準，利用 Java 泛型 (Generics) 綁定具體的事件型別，提供強型別的執行環境。
 * </pre>
 *
 * @param <T> 該策略支援的具體領域事件型別
 */
public interface TreeProjectionStrategy<T extends DomainEvent> {

	/**
	 * 宣告此策略支援的領域事件型別，供 Projector 啟動時建立路由表。
	 */
	Class<T> supportedEvent();

	/**
	 * 乾淨的執行介面，不需要再傳入基礎設施的 Port 或進行 Transaction 宣告，專注於閉包表的幾何演算法呼叫。
	 */
	void execute(T event);
}