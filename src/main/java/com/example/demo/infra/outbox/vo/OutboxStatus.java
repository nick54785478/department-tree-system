package com.example.demo.infra.outbox.vo;

/**
 * OutboxStatus (發件匣事件處理狀態列舉)
 */
public enum OutboxStatus {
	/**
	 * 待處理：事件已與業務資料一同 Commit，等待非同步 Poller 撈取派發
	 */
	PENDING,

	/**
	 * 已處理：事件已成功遞送至外部 Message Broker
	 */
	PROCESSED,

	/**
	 * 失敗：非同步派發遭遇致命錯誤或超過最大重試次數，定格存檔供後續稽核
	 */
	FAILED
}