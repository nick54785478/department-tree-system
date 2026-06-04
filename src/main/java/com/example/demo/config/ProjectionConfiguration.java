package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.demo.application.domain.dept.event.DepartmentCreatedEvent;
import com.example.demo.application.domain.dept.event.DepartmentDeletedEvent;
import com.example.demo.application.domain.dept.event.DepartmentDisabledEvent;
import com.example.demo.application.domain.dept.event.DepartmentMovedEvent;
import com.example.demo.application.domain.dept.event.DepartmentRenamedEvent;
import com.example.demo.application.domain.dept.event.DepartmentRestoredEvent;
import com.example.demo.application.domain.dept.event.DepartmentSortOrderChangedEvent;
import com.example.demo.application.projection.projector.DepartmentTreeProjector;
import com.example.demo.application.projection.projector.DepartmentViewProjector;
import com.example.demo.infra.event.dispatcher.ProjectionDispatcher;

import lombok.extern.slf4j.Slf4j;

/**
 * Projection Configuration (投影機路由組態)
 *
 * <pre>
 * 負責系統啟動時，將各個 Projector 註冊到 {@link ProjectionDispatcher} 中。 
 * 
 * <b>架構決策</b>：此組態明確定義了哪些 Domain Event 需要同時更新「樹狀閉包表 (Tree)」與「扁平視圖 (View)」。 
 * 
 * 將依賴關係與路由規則集中於此，徹底隔離了 Application Service 對投影機的直接依賴， 確保業務邏輯與讀取端同步邏輯的完全解耦。
 * </pre>
 */
@Slf4j
@Configuration
public class ProjectionConfiguration {

	/**
	 * 建立並配置 ProjectionDispatcher，定義各領域事件的分派路徑。
	 */
	@Bean
	public ProjectionDispatcher projectionDispatcher(DepartmentViewProjector viewProjector,
			DepartmentTreeProjector treeProjector) {
		ProjectionDispatcher dispatcher = new ProjectionDispatcher();

		// #### 1. 結構與生命週期事件 (同時觸發 View 與 Tree) ####
		// 當結構異動時，必須保證 Read Model 的樹狀關係與扁平資料同步更新
		dispatcher.register(DepartmentCreatedEvent.class, e -> {
			viewProjector.project(e);
			treeProjector.project(e);
		});

		dispatcher.register(DepartmentMovedEvent.class, e -> {
			viewProjector.project(e);
			treeProjector.project(e);
		});

		dispatcher.register(DepartmentDeletedEvent.class, e -> {
			viewProjector.project(e);
			treeProjector.project(e);
		});

		dispatcher.register(DepartmentRestoredEvent.class, e -> {
			viewProjector.project(e);
			treeProjector.project(e);
		});

		// #### 2. 屬性變更事件 (只觸發 View，不影響 Tree 結構) ####
		// 這些事件僅涉及屬性欄位更新，不需要進行 Closure Table 的路徑重建
		dispatcher.register(DepartmentRenamedEvent.class, viewProjector::project);
		dispatcher.register(DepartmentDisabledEvent.class, viewProjector::project);
		dispatcher.register(DepartmentSortOrderChangedEvent.class, viewProjector::project);

		log.info("ProjectionDispatcher successfully configured with view and tree projectors.");

		return dispatcher;
	}
}