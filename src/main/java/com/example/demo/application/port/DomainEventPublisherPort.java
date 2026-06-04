package com.example.demo.application.port;

import com.example.demo.application.domain.shared.event.DomainEvent;

/**
 * Domain Event Publisher Port (應用層/領域層 - 領域事件發布通道合約)
 *
 * <pre>
 * 定義領域事件 (Domain Events) 的基礎派發合約。 
 * 作為六角架構寫入端與事件驅動基礎設施之間的傳輸橋樑。
 * 在純 Event Sourcing 架構或混合式架構中，此 Port 負責將聚合根產生的核心業務事件，
 * 發布給 Spring 的 ApplicationEventPublisher、資產本地 Outbox 資料表，或是遠端的訊息佇列中介軟體 (如 Kafka, RabbitMQ)。
 * </pre>
 */
public interface DomainEventPublisherPort {

	/**
	 * 將封裝好的領域事件派發至系統廣播通道。
	 * <p>
	 * 此操作通常由 Command Service 載入並操作完 Aggregate Root 後觸發， 或是由混合式 Repository 的
	 * {@code save()} 階段自動連帶觸發。
	 * </p>
	 *
	 * @param event 繼承自 {@code DomainEvent} 基底類別、攜帶完整基礎審計與租戶元數據的具體業務事件
	 */
	void publish(DomainEvent event);
}