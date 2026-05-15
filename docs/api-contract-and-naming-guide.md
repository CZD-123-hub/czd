# API 契约与字段命名规范（阶段1基线）

## 1. 统一响应结构

所有非文件流接口统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "error": {
    "bizCode": "VALIDATION_FAILED",
    "details": []
  },
  "traceId": "c8f3...",
  "timestamp": "2026-04-20T13:00:00Z",
  "path": "/api/xxx"
}
```

- 成功响应：`code=200`，`message=success`，`error` 为空。
- 失败响应：`code` 为 HTTP 语义码，`error.bizCode` 为业务错误码，`message` 为可读错误信息。
- `traceId/timestamp/path` 必须在成功与失败场景都返回，便于前后端定位问题。

## 2. 错误码规范

- HTTP 语义码：`200/400/401/403/404/429/500`
- 业务错误码：`SUCCESS/BAD_REQUEST/VALIDATION_FAILED/UNAUTHORIZED/FORBIDDEN/NOT_FOUND/TOO_MANY_REQUESTS/INTERNAL_SERVER_ERROR`
- 领域错误码示例：`CONVERSATION_NOT_FOUND`、`DOCUMENT_NOT_FOUND`、`AI_GENERATION_FAILED`

约束：

- 控制器不要直接拼接错误结构，统一抛异常，由全局异常处理器输出标准结构。
- 参数校验错误统一落到 `VALIDATION_FAILED`，并在 `error.details` 中返回字段级信息。

## 3. DTO 命名与职责边界

- 请求体：`XxxRequest` / `XxxCreateRequest` / `XxxQueryRequest`
- 响应体：`XxxVO`
- 分页：`PageResult<T>`
- 统一响应壳：`ApiResponse<T>`

约束：

- Controller 输入必须优先使用 DTO，不直接接 `Map`。
- Controller 输出必须优先使用 VO，不直接返回 Entity。
- Entity 仅用于持久层与领域内部，不暴露给前端。

## 4. 字段命名规范

- 后端 Java：`camelCase`
- JSON 字段：`camelCase`
- 前端 TS 类型：`camelCase`，与 JSON 保持一一对应
- 布尔字段使用语义前缀：`is/has/can`（如 `isCompleted`）

常见字段约定：

- 主键：`id`（Long）
- 时间：`createdAt`、`updatedAt`（ISO-8601）
- 分页：`page`、`size`、`total`、`records`

## 5. 参数校验规范

- 在 Controller 层使用 `@Valid`
- DTO 字段使用 `@NotBlank/@NotNull/@Min/@Max/@Pattern`
- 校验消息使用清晰的英文短句（例如 `title is required`），由前端统一国际化/展示

## 6. 已落地接口清单（阶段1）

- `AuthController`：注册/登录/当前用户信息
- `PathController`：路径生成、列表、节点状态更新
- `SnippetController`：列表筛选、导入结果、分页参数校验
- `ProgressController`：仪表盘、周计划切换、报告导出
- `DocumentController`：DTO 入参 + VO 出参
- `GraphController`：图谱查询统一响应
- `ChatController`：会话列表/消息/反馈统一响应

## 7. 回归保障

- 控制器契约测试覆盖：
  - `AuthControllerContractTest`
  - `PathControllerContractTest`
  - `SnippetControllerContractTest`
  - `ProgressControllerContractTest`
  - `DocumentControllerContractTest`
  - `GraphControllerContractTest`
  - `ChatControllerContractTest`

后续新增接口时，必须同步补充契约测试，避免字段漂移导致前端回归。
