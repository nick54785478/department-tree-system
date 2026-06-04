package com.example.demo.infra.event.registry;

import java.util.Map;
import java.util.Optional;

import com.example.demo.application.domain.shared.event.DomainEvent;

/**
 * Domain Event Registry (基礎設施/型別中心 - 領域事件動態註冊表)
 *
 * <pre>
 * 專責維護、管理「事件名稱字串 (String)」與「具體 Java 類別 (Class)」之間的執行期雙向對應關係。
 *
 * <b>系統底層底座核心定位</b>： 當異步排程器（如 Outbox Processor 或 Event Store Rehydration）從資料庫歷史表中撈出一筆 平坦的文字紀錄時，
 * 它只會拿到 {@code event_type = 'DepartmentRestoredEvent'} 與 {@code payload = '{...}'}。 
 * 此時，底層基礎設施必須拿著 'DepartmentRestoredEvent' 字串，前來向本註冊表換取真實的 {@link DepartmentRestoredEvent.class}，
 * 才能交給 Jackson 反序列化出正確的充血實體。
 *
 * 執行緒安全設計： 本元件具備完全的<b>不可變性 (Immutability)</b>。在系統啟動完成 Classpath 掃描注入後， 內部 Map
 * 即被完全鎖定封裝，保證在高併發的 Projector / Replay 執行期擁有 100% 的 Thread-Safe 安全防護。
 * </pre>
 */
public class DomainEventRegistry {

	/**
	 * 唯讀的不可變事件型別映射快取表
	 */
	private final Map<String, Class<? extends DomainEvent>> registry;

	/**
	 * 透過系統組態設定（{@code OutboxConfiguration}）在啟動時注入。
	 * <p>
	 * 使用 Java 9+ 的 {@link Map#copyOf} 進行深度複製與防禦性封裝，徹底杜絕運行期被不當外部代碼篡改路由規則的風險。
	 * </p>
	 *
	 * @param registryMap 啟動階段由 Classpath 掃描引擎（如 AssignableTypeFilter）自動搜集完畢的事件對應矩陣
	 */
	public DomainEventRegistry(Map<String, Class<? extends DomainEvent>> registryMap) {
		this.registry = Map.copyOf(registryMap);
	}

	/**
	 * 根據事件類別名稱（Simple Name），動態換取執行期對應的 Java Class 強型別。
	 *
	 * @param eventType 歷史資料庫或訊息中介軟體傳來的事件類型識別字串 (例如: "DepartmentCreatedEvent")
	 * @return 封裝了具體 Event Class 類型的 Optional 容器；若傳入未註冊的幽靈事件字串，則回傳
	 *         {@code Optional.empty()}
	 */
	public Optional<Class<? extends DomainEvent>> getType(String eventType) {
		if (eventType == null || eventType.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(registry.get(eventType));
	}
}