package com.example.demo.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AssignableTypeFilter;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.infra.event.registry.DomainEventRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * Outbox Component Configuration (基礎設施自動掃描組態)
 */
@Slf4j
@Configuration
public class OutboxConfiguration {

	@Bean
	@SuppressWarnings("unchecked")
	public DomainEventRegistry domainEventRegistry() {

		Map<String, Class<? extends DomainEvent>> map = new HashMap<>();

		// 關閉預設的 @Component 過濾器
		ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);

		// 設定過濾規則：找出所有實作/繼承自 DomainEvent 的類別
		provider.addIncludeFilter(new AssignableTypeFilter(DomainEvent.class));

		// 確保這個路徑是所有領域事件（Domain Events）的「最頂層父目錄」
		// 依據你的 Stack Trace 提示，建議改用更寬廣的根路徑，讓它向下遞迴掃描所有子 Package！
		String domainPackage = "com.example.demo";

		log.info("[Event Registry] Starting Classpath scanning for DomainEvents under package: {}", domainPackage);

		for (BeanDefinition def : provider.findCandidateComponents(domainPackage)) {
			try {
				Class<?> clazz = Class.forName(def.getBeanClassName());

				// 排除掉抽象類別本身，我們只需要具體的實體事件類別
				if (!clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
					String eventName = clazz.getSimpleName();
					map.put(eventName, (Class<? extends DomainEvent>) clazz);

					log.info("[Event Registered] Found and mapped event: {} -> {}", eventName, clazz.getName());
				}
			} catch (ClassNotFoundException e) {
				throw new IllegalStateException("Failed to scan and load DomainEvent class: " + def.getBeanClassName(),
						e);
			}
		}

		log.info("[Event Registry] Total registered domain events: 共 {} 種", map.size());

		// 如果不幸發現一個都沒掃到，在啟動時就提早斷言崩潰，防止進入執行期才噴時光機異常
		if (map.isEmpty()) {
			log.error(
					"[CRITICAL WARNING] DomainEventRegistry is EMPTY! Please check if 'domainPackage' is configured correctly!");
		}

		return new DomainEventRegistry(map);
	}
}