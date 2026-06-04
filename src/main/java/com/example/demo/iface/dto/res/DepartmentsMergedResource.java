package com.example.demo.iface.dto.res;

/**
 * 部門重組成功回應載體 (Command Acknowledgment)
 *
 * <p>標準化的成功回應格式，供前端攔截器 (Interceptor) 統一處理 Toast 提示或後續畫面重整邏輯。</p>
 *
 * @param code    業務狀態碼 (如 "200")
 * @param message 具體的人類可讀操作成功提示
 */
public record DepartmentsMergedResource(
  String code, 
  String message
) {}