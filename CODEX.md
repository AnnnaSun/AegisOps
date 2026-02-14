# CODEX Daily Work Log

用于记录每天在本项目中的关键工作、变更点、验证结果和后续计划。

## 2026-02-13

### 今日目标
- 搭建 Day1 最小可运行框架：`日志 -> LLM 分析 -> 结构化返回`
- 修复 WebFlux 阻塞调用问题
- 完成非阻塞链路改造与编译验证

### 完成事项
- 创建单模块 Spring Boot + Maven 工程骨架（Java 23）
- 按分层实现核心包结构：`controller/service/analyzer/llm/model/plugin/config/util`
- 打通 Ollama `/api/generate`（`stream=false`）调用
- 提供 `POST /analyze/log` 接口，返回 `AnalysisResult`
- 新增 `@ConfigurationProperties` 配置：`ollama.url/model/timeoutSeconds`
- 新增 `logback-spring.xml` 方便调试
- 修复 WebFlux 报错：`block() ... not supported in thread reactor-http-nio-*`
- 将调用链改为全非阻塞：
  - `LLMClient.generate` -> `Mono<String>`
  - `Analyzer.analyze` -> `Mono<R>`
  - `LogAnalyzer/AnalyzeService/AnalyzeController` 全链路 `Mono`
  - `OllamaClient` 移除 `block()`，改为 reactive pipeline

### 关键文件
- `src/main/java/com/example/aiengineeragent/controller/AnalyzeController.java`
- `src/main/java/com/example/aiengineeragent/service/AnalyzeService.java`
- `src/main/java/com/example/aiengineeragent/analyzer/Analyzer.java`
- `src/main/java/com/example/aiengineeragent/analyzer/LogAnalyzer.java`
- `src/main/java/com/example/aiengineeragent/llm/LLMClient.java`
- `src/main/java/com/example/aiengineeragent/llm/OllamaClient.java`
- `src/main/java/com/example/aiengineeragent/config/OllamaProperties.java`
- `src/main/resources/application.yml`
- `src/main/resources/logback-spring.xml`
- `README.md`

### 验证记录
- `mvn -q -DskipTests compile`：通过
- `mvn test`：受本机 JDK23 + Mockito agent 附加机制影响失败（与业务改动无关）

### 遇到问题与处理
- 问题：在 WebFlux 事件线程执行 `block()` 导致运行时异常
- 处理：移除阻塞调用，改为全链路响应式 `Mono` 返回

### 明日计划
- 增加 `WebTestClient` 接口测试覆盖 `POST /analyze/log`
- 优化 `LogAnalyzer` 文本解析健壮性（标题缺失/格式偏差场景）
- 评估将 `severity` 规则配置化（从 `application.yml` 读取）

## 2026-02-14

### 今日目标
- 完成 Day2：结构化 JSON 输出 + 规则优先 + 解析兜底 + evidence 预留
- 保持分层边界：`Controller -> Service -> Analyzer -> LLMClient`
- 修复 macOS Netty DNS native 告警且兼容 Windows/Linux

### 完成事项
- `LogAnalyzer` 改造为 Rule-First：
  - 新增规则接口 `LogRule` 与实现 `OOMRule`
  - 命中 OOM 规则时直接返回，不调用 LLM
- 强制 LLM 输出 JSON：
  - Prompt 明确要求仅输出合法 JSON
  - 使用 Jackson `ObjectMapper` 解析到 `AnalysisResult`
  - 解析失败返回 fallback（不抛到 Controller）
- 模型结构扩展：
  - `AnalysisResult` 增加 `evidence: List<Evidence>`（默认空）
  - 新增 `Evidence` 模型（`type/detail`）
- Ollama 响应处理增强：
  - 继续使用 `/api/generate` + `stream=false`
  - 明确读取 `response` 字段，缺失时返回空串并记录告警
- 日志增强：
  - 记录规则命中
  - 记录是否调用 LLM
  - 记录 JSON 解析成功/失败
- 构建兼容性优化（跨平台）：
  - `pom.xml` 使用 macOS 条件 profile 按架构引入 `netty-resolver-dns-native-macos`
  - Linux/Windows 不加载 macOS native 依赖

### 关键文件
- `src/main/java/com/example/aiengineeragent/analyzer/LogAnalyzer.java`
- `src/main/java/com/example/aiengineeragent/rule/LogRule.java`
- `src/main/java/com/example/aiengineeragent/rule/OOMRule.java`
- `src/main/java/com/example/aiengineeragent/model/AnalysisResult.java`
- `src/main/java/com/example/aiengineeragent/model/Evidence.java`
- `src/main/java/com/example/aiengineeragent/llm/OllamaClient.java`
- `pom.xml`
- `README.md`

### 验证记录
- `mvn -q -DskipTests compile`：通过
- 接口验收场景：
  - OOM 日志：命中规则，`callLlm=false`
  - 普通异常：走 LLM，JSON 解析成功
  - LLM 非 JSON：触发 fallback，接口仍返回稳定 JSON

### 遇到问题与处理
- 问题：`MacOSDnsServerAddressStreamProvider` native 库加载失败告警
- 处理：在 Maven 中按 macOS 架构启用 `netty-resolver-dns-native-macos`，并添加注释说明

### 明日计划
- 补 Day2 相关自动化测试（规则命中/LLM JSON/fallback）
- 细化 `evidence` 生成策略（从日志提取关键证据片段）
- 评估新增更多规则（如 `ConnectionRefused`、`Timeout`）

---

## 每日记录模板

> 复制以下结构新增日期即可。

```md
## YYYY-MM-DD

### 今日目标
- 

### 完成事项
- 

### 关键文件
- 

### 验证记录
- 

### 遇到问题与处理
- 问题：
- 处理：

### 明日计划
- 
```
