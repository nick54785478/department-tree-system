package com.example.demo.infra.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.port.EventStorerPort;
import com.example.demo.application.shared.dto.SnapshotData;
import com.example.demo.infra.event.registry.DomainEventRegistry;
import com.example.demo.infra.event.sourcing.StoredEvent;
import com.example.demo.infra.persistence.EventStorePersistence;
import com.example.demo.infra.persistence.SnapshotPersistence;
import com.example.demo.infra.snapshot.AggregateSnapshot;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Event Store Adapter (Infrastructure Layer)
 *
 * <p>
 * 負責事件流 (Event Stream) 與快照 (Snapshot) 的物理持久化實作。
 * 
 * <strong>技術選型</strong>：使用 Jackson 處理事件的 JSON 序列化，並透過 JPA 寫入關聯式資料庫。 利用
 * {@link DomainEventRegistry} 動態對應字串的 EventType 與實體的 Class， 解決反序列化時的型別抹除問題。
 * </p>
 */
@Component
@RequiredArgsConstructor
class EventStorerAdapter implements EventStorerPort {

	private final EventStorePersistence eventStorePersistence;
	private final ObjectMapper objectMapper;
	private final DomainEventRegistry registry;
	private final SnapshotPersistence snapshotPersistence; // 🌟 注入快照 Repo

	@Override
	public void append(DomainEvent event) {
		try {
			// 將物件序列化為 JSON Payload
			String payload = objectMapper.writeValueAsString(event);
			StoredEvent storedEvent = new StoredEvent(event, payload);
			eventStorePersistence.save(storedEvent);
		} catch (Exception e) {
			throw new RuntimeException("Failed to serialize DomainEvent for EventStore", e);
		}
	}

	@Override
	public List<DomainEvent> loadEvents(String tenantId, String aggregateType, String aggregateId) {
		List<StoredEvent> storedEvents = eventStorePersistence
				.findByTenantIdAndAggregateTypeIgnoreCaseAndAggregateIdOrderByGlobalPositionAsc(tenantId, aggregateType,
						aggregateId);

		return deserializeEvents(storedEvents);
	}

	@Override
	public List<DomainEvent> loadEventsUpTo(String tenantId, String aggregateType, String aggregateId,
			Instant upToTimestamp) {
		List<StoredEvent> storedEvents = eventStorePersistence
				.findByTenantIdAndAggregateTypeAndAggregateIdAndOccurredAtLessThanEqualOrderByGlobalPositionAsc(
						tenantId, aggregateType, aggregateId, upToTimestamp);

		return deserializeEvents(storedEvents);
	}

	@Override
	public List<DomainEvent> loadAllEventsOrderedByGlobalPosition() {
		// 透過 JPA 撈出所有事件，並保證絕對順序 (Global Position)
		List<StoredEvent> storedEvents = eventStorePersistence.findAllByOrderByGlobalPositionAsc();

		return deserializeEvents(storedEvents);
	}

	@Override
	public void saveSnapshot(String tenantId, String aggregateType, String aggregateId, Long version,
			Instant occurredAt, String payloadJson) {
		// 建立並儲存快照實體
		AggregateSnapshot snapshot = new AggregateSnapshot(tenantId, aggregateType, aggregateId, version, occurredAt,
				payloadJson);
		snapshotPersistence.save(snapshot);
	}

	@Override
	public Optional<SnapshotData> loadLatestSnapshotBefore(String tenantId, String aggregateType, String aggregateId,
			Instant upToTimestamp) {
		// 透過 Spring Data JPA 撈出最新一筆快照，並轉譯為 Record DTO 交給上層
		return snapshotPersistence
				.findFirstByTenantIdAndAggregateTypeAndAggregateIdAndOccurredAtLessThanEqualOrderByVersionDesc(tenantId,
						aggregateType, aggregateId, upToTimestamp)
				.map(snap -> new SnapshotData(snap.getVersion(), snap.getPayload()));
	}

	@Override
	public List<DomainEvent> loadEventsBetween(String tenantId, String aggregateType, String aggregateId,
			Long fromVersion, Instant upToTimestamp) {
		// 撈出快照之後的差異事件 (Delta Events)
		List<StoredEvent> storedEvents = eventStorePersistence
				.findByTenantIdAndAggregateTypeAndAggregateIdAndGlobalPositionGreaterThanAndOccurredAtLessThanEqualOrderByGlobalPositionAsc(
						tenantId, aggregateType, aggregateId, fromVersion, upToTimestamp);

		return deserializeEvents(storedEvents);
	}

	@Override
	public long countEvents(String tenantId, String aggregateType, String aggregateId) {
		return eventStorePersistence.countByTenantIdAndAggregateTypeAndAggregateId(tenantId, aggregateType,
				aggregateId);
	}

	/**
	 * 內部共用邏輯：將資料庫的 JSON Payload 轉回 DomainEvent 實體
	 */
	private List<DomainEvent> deserializeEvents(List<StoredEvent> storedEvents) {
		return storedEvents.stream().map(stored -> {
			try {
				// 利用 Registry 找出 JSON 應該反序列化成哪個具體的 Class
				Class<? extends DomainEvent> targetClass = registry.getType(stored.getEventType()).orElseThrow(
						() -> new IllegalStateException("Unknown Event Type in EventStore: " + stored.getEventType()));

				return objectMapper.readValue(stored.getPayload(), targetClass);
			} catch (Exception e) {
				throw new RuntimeException("Failed to deserialize StoredEvent: " + stored.getEventId(), e);
			}
		}).collect(Collectors.toList());
	}
}