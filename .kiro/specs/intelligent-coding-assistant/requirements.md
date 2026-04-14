# 需求文档

## 简介

智能编程学习助手是一个基于 Vue 3 + Spring Boot + 知识图谱与 LLM 的全栈应用，旨在为编程学习者提供智能问答、知识图谱导航、个性化学习路径、代码片段管理和学习进度跟踪等功能。系统通过大语言模型（LLM）生成高质量回答，结合 Neo4j 知识图谱构建系统化知识体系，帮助用户高效学习编程知识。

当前版本暂不引入 Redis（缓存/限流）和 Elasticsearch/向量数据库（向量检索），知识检索阶段采用关键词匹配或直接调用 LLM 方式实现，后续可按需扩展。

## 术语表

- **System（系统）**: 智能编程学习助手应用的整体，包含前端和后端
- **Frontend（前端）**: 基于 Vue 3 + Vite + Element Plus 构建的用户界面
- **Backend（后端）**: 基于 Spring Boot 构建的服务端应用
- **LLM_Service（大模型服务）**: 封装对大语言模型（OpenAI GPT-4 / ChatGLM3 / Qwen）API 调用的服务层
- **Auth_Module（认证模块）**: 基于 Spring Security + JWT 的用户认证与授权模块
- **Chat_Module（问答模块）**: 处理用户编程问题并生成智能回答的模块
- **Graph_Module（图谱模块）**: 管理和查询 Neo4j 知识图谱数据的模块
- **Path_Module（路径模块）**: 生成和管理个性化学习路径的模块
- **Snippet_Module（代码片段模块）**: 管理用户代码片段并提供智能推荐的模块
- **Progress_Module（进度模块）**: 跟踪用户学习行为并生成统计报告的模块
- **Knowledge_Node（知识节点）**: Neo4j 中存储的知识点实体，包含名称、分类、描述、难度等属性
- **Conversation（会话）**: 用户与系统之间的一次问答对话，包含多条消息
- **Learning_Path（学习路径）**: 根据用户水平和目标生成的有序知识节点序列
- **Code_Snippet（代码片段）**: 用户保存的可复用代码段，包含标题、代码、语言、标签等信息

## 需求

### 需求 1：用户注册

**用户故事：** 作为一名编程学习者，我希望能够注册账号，以便使用系统的个性化功能。

#### 验收标准

1. WHEN 用户提交包含用户名、密码和邮箱的注册请求, THE Auth_Module SHALL 验证输入数据的合法性并创建用户账号
2. WHEN 注册成功, THE Auth_Module SHALL 返回成功状态和用户基本信息
3. IF 用户名或邮箱已被注册, THEN THE Auth_Module SHALL 返回明确的重复注册错误信息
4. IF 密码不满足安全要求（长度不少于 8 位）, THEN THE Auth_Module SHALL 返回密码格式错误信息
5. THE Auth_Module SHALL 使用加密算法（如 BCrypt）存储用户密码，禁止明文存储

### 需求 2：用户登录与认证

**用户故事：** 作为一名已注册用户，我希望能够安全登录系统，以便访问我的个人数据和学习记录。

#### 验收标准

1. WHEN 用户提交正确的用户名和密码, THE Auth_Module SHALL 验证凭据并返回 JWT 令牌
2. IF 用户名或密码错误, THEN THE Auth_Module SHALL 返回认证失败错误信息，不泄露具体是用户名还是密码错误
3. WHEN 用户携带有效 JWT 令牌发起请求, THE Auth_Module SHALL 允许访问受保护的 API 资源
4. IF JWT 令牌过期或无效, THEN THE Auth_Module SHALL 返回 401 未授权状态码
5. WHEN 用户请求登出, THE Auth_Module SHALL 使当前令牌失效

### 需求 3：智能问答 - 问题提交与回答生成

**用户故事：** 作为一名编程学习者，我希望能够输入编程问题并获得包含代码示例和原理说明的智能回答，以便快速理解编程概念。

#### 验收标准

1. WHEN 用户在问答界面输入问题并提交, THE Chat_Module SHALL 接收问题并调用 LLM_Service 生成回答
2. THE Chat_Module SHALL 以流式响应（SSE）方式返回回答内容，首个 token 响应时间不超过 2 秒
3. THE Chat_Module SHALL 在回答中包含文字解释、代码示例和原理说明三个部分
4. WHEN 回答包含代码示例, THE Frontend SHALL 对代码进行语法高亮显示并提供一键复制功能
5. WHEN 用户在同一会话中继续提问, THE Chat_Module SHALL 携带历史消息上下文以保持多轮对话的连贯性
6. IF LLM_Service 调用失败或超时, THEN THE Chat_Module SHALL 返回友好的错误提示信息并建议用户重试

### 需求 4：智能问答 - 知识检索与上下文增强

**用户故事：** 作为一名编程学习者，我希望系统的回答能够基于知识库内容，以便获得更准确和有针对性的答案。

#### 验收标准

1. WHEN 用户提交问题, THE Chat_Module SHALL 通过关键词匹配从知识库中检索相关知识片段
2. WHEN 检索到相关知识片段, THE Chat_Module SHALL 将知识片段作为上下文拼接到 LLM 提示词中
3. THE Chat_Module SHALL 在回答中标注知识来源（引用的知识点名称或文档标识）
4. IF 知识库中未检索到相关内容, THEN THE Chat_Module SHALL 直接调用 LLM_Service 生成回答，并标注回答未引用知识库

### 需求 5：智能问答 - 会话管理

**用户故事：** 作为一名编程学习者，我希望能够管理我的问答会话，以便回顾历史问答记录。

#### 验收标准

1. WHEN 用户首次提问, THE Chat_Module SHALL 自动创建新会话并记录会话标题
2. THE Chat_Module SHALL 提供会话列表查询接口，按更新时间倒序返回用户的所有会话
3. WHEN 用户请求查看某个会话, THE Chat_Module SHALL 返回该会话的完整消息记录
4. WHEN 用户请求删除某个会话, THE Chat_Module SHALL 删除该会话及其所有关联消息
5. THE Chat_Module SHALL 确保用户只能访问和操作自己的会话数据

### 需求 6：智能问答 - 答案反馈

**用户故事：** 作为一名编程学习者，我希望能够对回答进行反馈，以便系统持续优化回答质量。

#### 验收标准

1. WHEN 用户对某条回答提交反馈（有用/无用）, THE Chat_Module SHALL 记录反馈评分和可选的文字评论
2. THE Chat_Module SHALL 将反馈数据关联到对应的消息记录
3. THE Chat_Module SHALL 确保每个用户对同一条消息只能提交一次反馈


### 需求 7：知识图谱 - 图谱概览与可视化

**用户故事：** 作为一名编程学习者，我希望能够以可视化图谱的方式浏览编程知识点及其关联关系，以便构建系统化的知识体系。

#### 验收标准

1. THE Graph_Module SHALL 从 Neo4j 查询知识图谱数据（节点和关系）并以 JSON 格式返回
2. THE Frontend SHALL 使用 ECharts 或 AntV G6 将图谱数据渲染为可交互的网络图，500 个节点以内渲染时间不超过 1 秒
3. THE Frontend SHALL 支持图谱的缩放、拖拽和搜索定位操作
4. THE Frontend SHALL 按知识分类（语言、框架、数据库、工具等）对节点进行不同颜色着色
5. THE Frontend SHALL 在节点连线上显示关系类型标签（依赖、包含、前置知识、关联等）

### 需求 8：知识图谱 - 节点交互与懒加载

**用户故事：** 作为一名编程学习者，我希望能够点击知识节点查看详情并探索关联知识，以便深入了解特定知识领域。

#### 验收标准

1. WHEN 用户点击某个知识节点, THE Graph_Module SHALL 返回该节点的详细信息（名称、分类、描述、难度）
2. WHEN 用户点击某个知识节点, THE Frontend SHALL 展开显示该节点的关联节点（懒加载方式）
3. WHEN 用户点击知识节点详情, THE Frontend SHALL 提供跳转到相关问答的入口
4. THE Graph_Module SHALL 支持按知识领域或标签筛选返回子图数据
5. THE Graph_Module SHALL 支持按关键词搜索知识节点并返回匹配结果

### 需求 9：个性化学习路径 - 路径生成

**用户故事：** 作为一名编程学习者，我希望系统能根据我的当前水平和学习目标生成个性化的学习路径，以便有计划地提升编程能力。

#### 验收标准

1. WHEN 用户设置学习目标并提交, THE Path_Module SHALL 结合知识图谱的拓扑结构和用户已掌握的知识生成学习路径
2. THE Path_Module SHALL 以有序节点列表形式返回学习路径，每个节点包含知识点信息和推荐学习资源
3. THE Path_Module SHALL 确保路径中的知识节点按照前置依赖关系正确排序
4. THE Frontend SHALL 以路线图或流程图形式可视化展示学习路径
5. WHEN 用户已有学习路径, THE Path_Module SHALL 提供路径列表查询功能

### 需求 10：个性化学习路径 - 路径管理与进度更新

**用户故事：** 作为一名编程学习者，我希望能够跟踪和管理我的学习路径进度，以便了解自己的学习状态。

#### 验收标准

1. THE Frontend SHALL 在学习路径中标记每个节点的状态（已完成、进行中、未开始）
2. WHEN 用户更新某个路径节点的学习状态, THE Path_Module SHALL 持久化状态变更
3. THE Path_Module SHALL 支持用户跳过已掌握的知识节点
4. THE Path_Module SHALL 为每个路径节点关联推荐学习资源（文档、视频、练习题等）

### 需求 11：代码片段 - 保存与管理

**用户故事：** 作为一名编程学习者，我希望能够保存和管理常用的代码片段，以便快速查找和复用。

#### 验收标准

1. WHEN 用户提交代码片段（包含标题、代码、编程语言、描述和标签）, THE Snippet_Module SHALL 保存代码片段到数据库
2. THE Snippet_Module SHALL 提供代码片段列表查询，支持按标签过滤和关键词搜索
3. WHEN 用户请求更新代码片段, THE Snippet_Module SHALL 更新对应记录
4. WHEN 用户请求删除代码片段, THE Snippet_Module SHALL 删除对应记录
5. THE Frontend SHALL 对代码片段进行多语言语法高亮显示（支持 Java、Python、SQL、JavaScript 等）
6. THE Snippet_Module SHALL 确保用户只能管理自己的代码片段

### 需求 12：代码片段 - 智能推荐

**用户故事：** 作为一名编程学习者，我希望系统能在问答过程中自动推荐相关的代码片段，以便快速获取可复用的代码。

#### 验收标准

1. WHEN 用户在问答过程中提问, THE Snippet_Module SHALL 根据当前问答上下文和用户历史使用频率推荐相关代码片段
2. THE Snippet_Module SHALL 返回推荐代码片段列表，按相关度排序
3. IF 没有匹配的代码片段, THEN THE Snippet_Module SHALL 返回空列表，不影响问答流程

### 需求 13：代码片段 - 导入导出

**用户故事：** 作为一名编程学习者，我希望能够导入和导出代码片段，以便在不同环境间迁移数据。

#### 验收标准

1. WHEN 用户请求导出代码片段, THE Snippet_Module SHALL 将用户的代码片段序列化为 JSON 格式文件并返回
2. WHEN 用户上传 JSON 格式的代码片段文件, THE Snippet_Module SHALL 解析文件内容并导入到用户的代码片段库
3. THE Snippet_Module SHALL 对导出的 JSON 进行格式化输出（Pretty Print）
4. FOR ALL 有效的代码片段数据, 导出后再导入 SHALL 产生与原始数据等价的代码片段记录（往返一致性）
5. IF 导入文件格式不合法, THEN THE Snippet_Module SHALL 返回描述性的解析错误信息


### 需求 14：学习进度 - 行为记录

**用户故事：** 作为一名编程学习者，我希望系统能自动记录我的学习行为，以便后续生成学习统计和报告。

#### 验收标准

1. WHEN 用户进行问答、浏览知识图谱或完成学习路径节点, THE Progress_Module SHALL 自动记录学习行为（行为类型、目标 ID、时间戳）
2. THE Progress_Module SHALL 将学习记录持久化到 MySQL 数据库
3. THE Progress_Module SHALL 确保学习记录与用户关联，用户只能查看自己的记录

### 需求 15：学习进度 - 仪表盘与统计

**用户故事：** 作为一名编程学习者，我希望能够在仪表盘上查看我的学习统计数据，以便了解自己的学习状况。

#### 验收标准

1. THE Progress_Module SHALL 提供仪表盘数据接口，返回学习天数、问答次数、知识掌握比例等统计指标
2. THE Progress_Module SHALL 提供学习热力图数据接口，返回用户每日学习活跃度数据（类似 GitHub 贡献图）
3. THE Progress_Module SHALL 提供知识雷达图数据接口，按知识领域维度返回用户的知识掌握程度
4. THE Frontend SHALL 使用 ECharts 渲染仪表盘、热力图和雷达图

### 需求 16：学习进度 - 学习报告

**用户故事：** 作为一名编程学习者，我希望能够生成和导出学习报告，以便回顾和分享我的学习成果。

#### 验收标准

1. WHEN 用户请求生成学习报告, THE Progress_Module SHALL 汇总用户的学习统计数据并生成结构化报告
2. THE Progress_Module SHALL 支持将学习报告导出为 PDF 格式
3. THE Progress_Module SHALL 在报告中包含学习时间统计、知识覆盖率、问答活跃度等维度数据

### 需求 17：前端代码编辑器集成

**用户故事：** 作为一名编程学习者，我希望在系统中拥有功能丰富的代码编辑体验，以便在学习过程中编写和测试代码。

#### 验收标准

1. THE Frontend SHALL 集成 Monaco Editor 作为代码编辑器组件
2. THE Frontend SHALL 在代码编辑器中支持多语言语法高亮（Java、Python、SQL、JavaScript 等）
3. THE Frontend SHALL 在代码片段管理和问答代码展示中统一使用代码编辑器组件

### 需求 18：安全防护

**用户故事：** 作为系统管理员，我希望系统具备基本的安全防护能力，以便保护用户数据和系统安全。

#### 验收标准

1. THE Auth_Module SHALL 对所有受保护的 API 接口进行 JWT 令牌验证
2. THE Backend SHALL 对用户输入进行 XSS 防护处理
3. THE Backend SHALL 对表单提交启用 CSRF 防护
4. THE Auth_Module SHALL 使用 BCrypt 算法加密存储用户密码
5. THE Backend SHALL 对 API 接口的异常情况返回统一格式的错误响应，不暴露内部实现细节

### 需求 19：响应式布局

**用户故事：** 作为一名编程学习者，我希望能够在不同设备上使用系统，以便随时随地进行学习。

#### 验收标准

1. THE Frontend SHALL 采用响应式布局设计，适配桌面端和移动端浏览器
2. WHILE 在移动端浏览器中访问, THE Frontend SHALL 提供基本的浏览和问答功能

### 需求 20：LLM 提供者可切换

**用户故事：** 作为系统管理员，我希望能够灵活切换 LLM 提供者，以便根据成本和性能需求选择合适的模型。

#### 验收标准

1. THE LLM_Service SHALL 通过统一的抽象接口封装不同 LLM 提供者（OpenAI GPT-4、ChatGLM3、Qwen）的调用
2. WHEN 系统配置中切换 LLM 提供者, THE LLM_Service SHALL 在不修改业务代码的情况下切换到目标模型
3. THE LLM_Service SHALL 通过配置文件指定当前使用的 LLM 提供者和 API 密钥

### 需求 21：容器化部署

**用户故事：** 作为系统管理员，我希望能够通过容器化方式一键部署整个系统，以便简化部署和运维流程。

#### 验收标准

1. THE System SHALL 提供 Docker Compose 配置文件，编排前端、后端、MySQL、Neo4j 和 Nginx 服务
2. WHEN 执行 docker-compose up 命令, THE System SHALL 启动所有服务并完成服务间的网络连接
3. THE System SHALL 通过 Nginx 提供前端静态资源服务和后端 API 反向代理
4. THE System SHALL 在 Docker Compose 配置中支持通过环境变量配置数据库连接、LLM API 密钥等敏感信息

### 需求 22：并发性能

**用户故事：** 作为系统管理员，我希望系统能够支持一定规模的并发用户，以便满足多人同时使用的场景。

#### 验收标准

1. THE System SHALL 支持 100 个以上并发用户同时使用
2. WHILE 系统处于正常负载状态, THE Chat_Module SHALL 保持问答流式响应首 token 延迟不超过 2 秒
3. WHILE 系统处于正常负载状态, THE Graph_Module SHALL 保持 500 节点以内的图谱数据查询响应时间不超过 1 秒
