package com.example.demo.application.port;

import com.example.demo.application.shared.dto.DepartmentTemporalState;

/**
 * Snapshot Serializer Port (基礎設施 - 快照序列化合約)
 *
 * <pre>
 * 負責處理記憶體狀態物件 (State Object) 與純文字持久化格式 (通常為 JSON) 之間的雙向轉換。 
 * 
 * <strong>架構意圖：</strong>
 *   利用此 Port 徹底隔離底層序列化框架 (如 Jackson, Gson) 的實作細節， 
 * 防止框架專屬的 Annotation 污染 Application Layer 甚至是 Domain Layer。 
 * 同時，此合約的實作類別也是處理未來狀態結構「版本演進 (Schema Evolution)」與相容性的關鍵守門員。
 * </pre>
 */
public interface SnapshotSerializerPort {

	/**
	 * 將時光機記憶體狀態序列化為字串 Payload。
	 *
	 * @param state 當下計算出的部門時光狀態物件
	 * @return 序列化後的字串 (如 JSON string)
	 */
	String serialize(DepartmentTemporalState state);

	/**
	 * 將文字 Payload 反序列化還原為記憶體狀態物件。
	 *
	 * @param payload 從 Event Store 撈取出的快照二進位或字串資料
	 * @return 還原後的部門時光狀態物件
	 * @throws IllegalArgumentException 若 payload 格式損毀或版本不相容時拋出
	 */
	DepartmentTemporalState deserialize(String payload);
}