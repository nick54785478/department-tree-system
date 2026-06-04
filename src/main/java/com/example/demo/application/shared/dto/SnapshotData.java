package com.example.demo.application.shared.dto;

/**
 * Snapshot Data (時光機 - 歷史快照數據包裹 DTO)
 *
 * <p>
 * 專供 {@code EventStorerPort} 及其 Adapter 內部向外傳遞快照文本的通用載體。 隔離了資料庫的實體 Entity
 * 結構，提供簡潔安全的唯讀 Record 設計。
 * </p>
 *
 * @param version 該快照存檔點所定格的「歷史絕對事件序號(版本號)」
 * @param payload 經由序列化 Port 打包後的純文字狀態快照 Payload (通常為密閉的 JSON 字串)
 */
public record SnapshotData(Long version, String payload) {
}