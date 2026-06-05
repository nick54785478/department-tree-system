package com.example.demo.infra.adapter;

import org.springframework.stereotype.Component;

import com.example.demo.application.port.SnapshotSerializerPort;
import com.example.demo.application.shared.dto.DepartmentTemporalState;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Jackson Snapshot Serializer Adapter (Infrastructure Layer)
 *
 * <pre>
 * 快照序列化機制的具體實作。
 * 
 * <strong>架構意圖</strong>：集中管理時光機記憶體狀態的 JSON 轉換邏輯，如果未來時光狀態 (Temporal State)
 * 的結構發生破壞性變更 (Breaking Change)， 我們可以在這裡加入自訂的 Jackson JsonNode 遷移邏輯 (Schema Migration)， 
 * 將舊版 JSON 格式平滑轉換為新版實體，而不必去動到核心的業務層。
 * </pre>
 */
@Component
@RequiredArgsConstructor
class JacksonSnapshotSerializerAdapter implements SnapshotSerializerPort {

	private final ObjectMapper objectMapper;

	@Override
	public String serialize(DepartmentTemporalState state) {
		try {
			return objectMapper.writeValueAsString(state);
		} catch (Exception e) {
			throw new RuntimeException("Failed to serialize snapshot", e);
		}
	}

	@Override
	public DepartmentTemporalState deserialize(String payload) {
		try {
			return objectMapper.readValue(payload, DepartmentTemporalState.class);
		} catch (Exception e) {
			throw new RuntimeException("Failed to deserialize snapshot payload", e);
		}
	}
}