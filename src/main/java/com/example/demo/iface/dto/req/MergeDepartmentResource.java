package com.example.demo.iface.dto.req;

/**
 * 部門合併請求載體 (Command Payload)
 *
 * <p>封裝從前端傳入的重組參數。實務上可在此搭配 JSR-380 (@NotBlank) 進行基礎的輸入格式防禦。</p>
 *
 * @param targetDeptId 吸收所有資產與人員的存續目標部門 ID
 */
public record MergeDepartmentResource(
  String targetDeptId
) {}