# AI Engineer Agent - Day2

Spring Boot + Maven 单模块工程，提供 Day2 闭环：`日志输入 -> 规则优先 -> LLM(JSON) -> 稳定结构化返回`。

## Day2 关键能力

- 规则优先：命中 `OOMRule` 时直接返回，不调用 LLM
- LLM 强制 JSON：Prompt 约束仅输出 JSON
- 解析兜底：Jackson 解析失败时返回 fallback，不把异常抛到 Controller
- 结果扩展：`AnalysisResult` 新增 `evidence` 字段（当前允许为空）
- Ollama 兼容：`/api/generate` 使用 `stream=false`，解析 `response` 字段
- 运行日志：记录规则命中/是否调用 LLM/解析成功或失败

## Dev Notes（规则优先级约定）

- `10-99`：HIGH 确定性规则（优先执行）
- `100-199`：中等置信度规则
- `900+`：兜底类规则
- 当前 `OOMRule` 使用 `@Order(10)`，属于最高优先级一档

## Day2.1 Notes

- Severity 误报收敛：
  - `fatal` 不再单独触发 HIGH，需同时命中 `error|exception|crash`
  - `panic` 仅在 `kernel panic` 或 `panic:` 场景触发 HIGH
  - `corrupt` 仅在 `corrupt(ed) index|file|database` 场景触发 HIGH，其他场景降为 MEDIUM
- 测试覆盖点（核心分支）：
  - OOM 规则短路且不调用 LLM
  - LLM 返回合法 JSON 的解析成功路径
  - LLM 返回非 JSON 的 fallback 路径
  - LLM 调用异常路径（可选加分项，已覆盖）

## 1. IDEA 运行方式（Run/Debug）

1. 用 IntelliJ IDEA 打开项目根目录：`/Users/annasun/Developer/IdeaProjects/AegisOps`
2. 等待 Maven 依赖导入完成
3. 打开主类：`com.example.aiengineeragent.AIEngineerAgentApplication`
4. 点击 `Run` 或 `Debug` 启动
5. 默认端口：`8080`

建议断点位置：
- `src/main/java/com/example/aiengineeragent/controller/AnalyzeController.java`
- `src/main/java/com/example/aiengineeragent/service/AnalyzeService.java`
- `src/main/java/com/example/aiengineeragent/analyzer/LogAnalyzer.java`
- `src/main/java/com/example/aiengineeragent/llm/OllamaClient.java`

## 2. 启动 Ollama

1. 启动 Ollama 服务（本地默认 `11434`）
2. 拉起模型示例：

```bash
ollama run qwen3:8b
```

配置位于 `src/main/resources/application.yml`：

```yaml
ollama:
  url: "http://localhost:11434"
  model: "qwen3:8b"
  timeoutSeconds: 30
```

## 3. curl 调用示例

```bash
curl -X POST "http://localhost:8080/analyze/log" \
  -H "Content-Type: application/json" \
  -d '{
    "log": "java.net.ConnectException: Connection refused at ..."
  }'
```

示例返回（结构化）：

```json
{
  "summary": "...",
  "risks": ["..."],
  "suggestions": ["..."],
  "severity": "HIGH",
  "evidence": [],
  "rawLLMResponse": "SUMMARY: ..."
}
```

## 4. 常见错误排查

1. `Connection refused` / `11434` 不通
- 检查 Ollama 是否运行
- 检查 `ollama.url` 是否正确（默认 `http://localhost:11434`）

2. 模型未拉取
- 执行 `ollama run qwen3:8b` 拉取并启动模型
- 或修改 `application.yml` 为本机已有模型

3. 请求超时
- 增大 `ollama.timeoutSeconds`
- 首次模型加载通常更慢，建议先在终端预热模型

4. Java 版本不匹配
- 项目要求 Java 23
- IDEA Project SDK 和 Maven JDK 都要设置为 23
