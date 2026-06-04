package com.example.demo.infra.adapter;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.port.DomainEventPublisherPort;

import lombok.RequiredArgsConstructor;

/**
 * Domain Event Publisher Adapter (Infrastructure Layer)
 *
 * <p>
 * 領域事件發布器的具體實作。
 * 
 * <strong>架構意圖</strong>：將 Spring 框架特有的 {@link ApplicationEventPublisher}
 * 髒活封裝於此， 確保 Application Layer 與 Domain Layer 絕對不會 import 任何 Spring 專屬的
 * Event類別， 以實踐六角架構 (Hexagonal Architecture) 的依賴反轉。
 * </p>
 */
@Component
@RequiredArgsConstructor
class DomainEventPublisherAdapter implements DomainEventPublisherPort {

	private final ApplicationEventPublisher springPublisher;

	/**
	 * 將領域事件轉交給 Spring 的內部事件匯流排進行廣播。
	 * <p>
	 * 後續可由 @EventListener 或 @TransactionalEventListener 進行同步/非同步攔截。
	 * </p>
	 */
	@Override
	public void publish(DomainEvent event) {
		springPublisher.publishEvent(event);
	}
}