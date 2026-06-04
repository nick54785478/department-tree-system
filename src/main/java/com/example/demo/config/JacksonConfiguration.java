package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.infra.event.mixin.DomainEventMixIn;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

/**
 * Jackson Configuration (基礎設施層 - 全局序列化引擎純手動配置版)
 *
 * <pre>
 * 
 * <b>架構破局與解耦之法：</b> 基於嚴格的六角架構原則，我們的 Infrastructure 模組刻意排除了 spring-boot-autoconfigure 的依賴。 
 * 由於失去了 Spring Boot 的自動組態魔法，Spring 容器中預設並不會產生 {@link ObjectMapper} Bean。 
 * 因此，我們在此手動建構並註冊一個最標準的全局序列化引擎。
 * 
 * <b>這個 Bean 誕生後會觸發的兩大連鎖效應：</b>
 * 1.<b>內部依賴滿足：</b> EventStorerAdapter 等物件渴望的 ObjectMapper 依賴瞬間被滿足，確保事件能順利序列化並落庫至 EventStore。
 * 2.<b>外部表現層接管：</b> 最外層的 Spring Web MVC 在啟動時偵測到我們自定義了 ObjectMapper，便會主動退讓，
 * 全面改用此實例來處理所有的 HTTP JSON Request 與 Response。
 *
 * 藉此，我們不費吹灰之力，順理成章地讓「底層資料庫存取」與「外層 API 介面」 共用了同一個帶有 {@link DomainEventMixIn} 
 * 影子規則的強大序列化器，達成全域 100% 的序列化對齊！
 * </pre>
 */
@Slf4j
@Configuration
public class JacksonConfiguration {

	/**
	 * 建構並註冊全局共用的 ObjectMapper 實例。
	 *
	 * @return 配置完畢的 ObjectMapper
	 */
	@Bean
	public ObjectMapper objectMapper() {
		log.info("[Jackson Config] Manually building global ObjectMapper with DomainEventMixIn and JavaTime support.");

		ObjectMapper mapper = new ObjectMapper();

		// 1. 註冊 Java 8 時間模組 (JSR-310)
		// 由於 DomainEvent 中廣泛使用了 Instant 類別，此模組是精準處理現代 Java 時間型別的絕對必需品。
		mapper.registerModule(new JavaTimeModule());

		// 2. 關閉將時間序列化為時間戳記 (Timestamps) 的預設行為
		// 強制將時間轉換成人類高可讀性、前端易解析的 ISO-8601 標準字串 (例如: "2026-06-02T15:00:00Z")。
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		// 3. 多型影子規則編織 (MixIn Injection)
		// 將領域事件的多型反序列化規則織入引擎中，
		// 確保無論是從 DB 讀取還是向 API 回傳，都能精準帶上 "eventType" 標籤，且完全不污染 Domain 層代碼。
		mapper.addMixIn(DomainEvent.class, DomainEventMixIn.class);

		return mapper;
	}
}