# Enterprise Department Management System

一個基於戰術領域驅動設計 (Tactical DDD)、CQRS 與事件溯源 (Event Sourcing) 建構的企業級高併發組織樹管理系統。


## 專案概述 (Project Overview)

本專案旨在解決大型企業面臨的複雜「組織架構重組 (Org Restructuring)」、「無限層級樹狀結構管理」以及「精確稽核軌跡 (Audit Trail)」等痛點。

系統徹底摒棄了傳統 CRUD 與貧血模型 (Anemic Domain Model) 的做法，全面擁抱 六角架構 (Hexagonal Architecture)，並將 CQRS (命令查詢職責分離) 推進到極致。透過引入 閉包表 (Closure Table) 解決了關聯式資料庫處理樹狀結構的 N+1 效能瓶頸，同時利用 事件溯源 (Event Sourcing) 達成了跨聚合根操作的零鎖併發 (Lock-Free Concurrency) 與完美時光機回溯。

## 核心架構亮點 (Core Architectural Highlights)

**1. 戰術領域驅動設計 (Tactical DDD) & 輕量化聚合根**
>* Lightweight Aggregate： Department 聚合根刻意不包含任何 List<Employee> 實體關聯。人員編制的指派僅透過輕量級計數器 (activeEmployeeCount) 進行 $O(1)$ 的領域不變量防護，徹底消滅了大型部門異動時的資料庫併發死鎖 (Lock Contention)。
>* 充血模型 (Rich Domain Model)： 封裝了所有狀態流轉邏輯 (moveTo, mergeInto, restore, disable)，外部系統無法繞過聚合根直接修改狀態。
>* 嚴格多租戶隔離： 透過強型別值物件 (Value Objects, 如 TenantId, DepartmentId)，從領域層根絕越權存取 (IDOR) 風險。

**2. 極致 CQRS 與事件折疊 (Event Folding)**
>* 寫入端純潔性 (100% Write-Side Purity)： 寫入端完全不依賴任何 Query Side 的視圖表。在處理如「部門合併」這種跨聚合的重量級流程時，系統透過 命令端記憶體投影 (Command-Side In-Memory Projection)，重播並折疊歷史事件，在記憶體中推演出當下的人員名單，達到極致效能與強一致性。
>* Application Service 作為流程編排器： 嚴格遵守六角架構邊界，跨聚合協調邏輯 (Process Manager) 被完美收攏於 Command Service 中，絕不讓基礎設施邏輯污染領域模型。

**3. 事件溯源 (Event Sourcing) 與時光機**
>* 單一真實來源 (Single Source of Truth)： 所有業務狀態的改變皆化為不可變的 DomainEvent 附加於 Event Store。
>* 業務語意豐富化： 提供如 DepartmentMergedEvent 等高含金量事件，明確標示組織資產的流向，為後續的資料倉儲 (Data Warehouse) 與稽核提供完美斷點銜接。

**4. 閉包表拓撲引擎 (Closure Table Topology)**
>* 讀取端效能怪獸： 讀取端投影器 (Projector) 透過監聽領域事件，異步維護 department_tree 閉包表。
>* $O(1)$ 幾何操作： 不論是查詢整棵子樹、祖先血脈，還是進行防禦性的循環圖 (Cyclic Graph) 檢測，皆能在單次 SQL 查詢內完成，徹底擺脫遞迴查詢的夢魘。

## 核心業務情境 (Key Use Cases)
>* 組織無縫重組 (Org Restructuring)： 支援 Merge (合併) 與 Move (掛載點轉移)。一鍵自動平移子部門與員工編制，並安全清理來源部門狀態。
>* 人員編制大遷徙 (Mass Employee Transfer)： 利用 Event Replay 瞬間鎖定轉移名單，無須依賴低效的關聯表雙寫。
>* 時光機還原 (Undelete & Restore)： 支援從邏輯刪除狀態中滿血復活，並包含自動回歸頂層 Root 的拓撲防禦機制，防止產生幽靈孤兒節點。

## 技術堆疊 (Tech Stack)
>* Language: Java 17 / 21
>* Framework: Spring Boot 3.x
>* Data Access (Write Side): Spring Data JPA / Hibernate (Driven by Domain Events)
>* Data Access (Query Side / Topology): Spring JDBC / Native SQL (Closure Table)
>* Event Serialization: Jackson Object Mapper (Polymorphic Deserialization)
>* Architecture Patterns: Hexagonal Architecture (Ports and Adapters), CQRS, Event Sourcing, Domain-Driven Design

## 開發指南與規範
>* Event Immutable Rule: 所有的 DomainEvent 必須是不可變的 (Immutable)，且必須包含 tenantId 與發生時間。
>* Cross-Aggregate Operations: 若業務邏輯跨越兩個以上的 Aggregate，禁止在 Aggregate 內部互相調用，必須透過 ApplicationService 進行協調。
>* Read-Model Segregation: 任何 _view 或讀取專用的查詢，絕對禁止注入到寫入端 (domain 或 application 的 Command Service 中)。
