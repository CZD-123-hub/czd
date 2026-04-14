# 智能编程学习助手 - 需求规格说明书

## 项目名称

基于 Vue3 + Spring Boot + Redis + 知识图谱与 LLM 的智能编程学习助手

---

## 一、功能需求

### 1. 智能问答

- **描述**: 用户输入编程问题（如"Spring Boot 如何整合 Redis？"），系统从知识库中检索并生成精准答案，附带相关代码示例和原理说明。
- **核心流程**:
  1. 用户输入自然语言问题
  2. 系统通过向量检索（RAG）从知识库中召回相关文档/知识片段
  3. 将召回内容作为上下文，调用 LLM 生成结构化回答
  4. 返回答案，包含：文字解释 + 代码示例 + 原理说明
- **关键要求**:
  - 支持多轮对话，保持上下文连贯
  - 代码示例需语法高亮、可复制
  - 答案需标注知识来源（出自哪个知识点/文档）
  - 支持对答案的反馈（有用/无用），用于优化检索质量

### 2. 知识图谱导航

- **描述**: 可视化展示编程知识点（如 Java、Spring、数据库）之间的关联、依赖关系，帮助用户构建系统化知识体系。
- **核心流程**:
  1. 后端从 Neo4j 查询知识图谱数据（节点 + 关系）
  2. 前端使用 ECharts/G6 渲染可交互的图谱
  3. 用户点击节点可查看知识详情、跳转相关问答
- **关键要求**:
  - 支持缩放、拖拽、搜索定位
  - 节点分类着色（如：语言、框架、数据库、工具等）
  - 支持按知识领域/标签筛选子图
  - 点击节点可展开关联节点（懒加载）
  - 显示节点间关系类型（如：依赖、包含、前置知识等）

### 3. 个性化学习路径

- **描述**: 根据用户当前水平和发展目标，智能推荐学习路线和资源。
- **核心流程**:
  1. 用户填写自评问卷或通过测试题评估当前水平
  2. 用户设置学习目标（如：掌握 Spring Cloud 微服务开发）
  3. 系统结合知识图谱的拓扑结构 + 用户已掌握知识，生成推荐路径
  4. 路径以有序节点列表形式展示，每个节点关联学习资源
- **关键要求**:
  - 路径可视化展示（路线图/流程图形式）
  - 标记已完成/进行中/未开始状态
  - 支持路径调整（用户可跳过已掌握的知识点）
  - 每个节点关联推荐资源（文档、视频、练习题等）

### 4. 代码片段管理与推荐

- **描述**: 用户可保存常用代码片段，系统可根据上下文智能推荐相关代码。
- **核心流程**:
  1. 用户手动保存代码片段（标题 + 代码 + 标签 + 描述）
  2. 问答过程中，系统根据当前话题自动推荐用户已收藏或公共代码片段
  3. 支持代码片段的搜索、分类管理
- **关键要求**:
  - 支持多语言代码高亮（Java, Python, SQL, JavaScript 等）
  - 支持标签分类和全文搜索
  - 支持代码片段的导入/导出
  - 智能推荐基于当前问答上下文 + 用户历史使用频率

### 5. 学习进度跟踪

- **描述**: 记录用户问答历史、学习轨迹，生成学习报告。
- **核心流程**:
  1. 自动记录用户每次问答、浏览知识图谱、完成学习路径节点等行为
  2. 生成学习统计数据（每日/每周活跃度、知识覆盖率等）
  3. 定期生成学习报告
- **关键要求**:
  - 仪表盘展示：学习天数、问答次数、知识掌握比例等
  - 学习热力图（类似 GitHub Contribution Graph）
  - 知识掌握雷达图（按知识领域维度）
  - 学习报告支持导出（PDF）

---

## 二、技术架构

### 前端

| 技术 | 用途 |
|------|------|
| Vue 3 | 前端框架（Composition API） |
| Vite | 构建工具 |
| Element Plus | UI 组件库 |
| ECharts / AntV G6 | 知识图谱可视化 |
| Monaco Editor / CodeMirror | 代码编辑器/代码高亮 |
| Pinia | 状态管理 |
| Vue Router | 路由管理 |
| Axios | HTTP 请求 |

### 后端

| 技术 | 用途 |
|------|------|
| Spring Boot | 后端框架 |
| Spring Security | 认证与授权（JWT） |
| MyBatis-Plus | ORM / 数据访问层 |
| Spring AI / RestTemplate | 调用 LLM API |

### AI 与数据

| 技术 | 用途 |
|------|------|
| OpenAI GPT-4 API / ChatGLM3 / Qwen | 大模型（智能问答生成） |
| Neo4j | 知识图谱存储（图数据库） |
| （后续扩展）Elasticsearch / Milvus | 向量检索，实现 RAG 架构（当前版本暂不引入） |

### 数据存储

| 技术 | 用途 |
|------|------|
| MySQL | 用户数据、会话记录、代码片段、学习进度 |

### 部署

| 技术 | 用途 |
|------|------|
| Docker | 容器化部署 |
| Docker Compose | 多服务编排 |
| Nginx | 前端静态资源 + 反向代理 |

---

## 三、系统模块划分

```
intelligent-coding-assistant/
├── frontend/                  # 前端 (Vue3 + Vite)
│   ├── src/
│   │   ├── views/
│   │   │   ├── Chat/          # 智能问答页面
│   │   │   ├── KnowledgeGraph/# 知识图谱页面
│   │   │   ├── LearningPath/  # 学习路径页面
│   │   │   ├── CodeSnippets/  # 代码片段管理页面
│   │   │   ├── Dashboard/     # 学习进度仪表盘
│   │   │   ├── Auth/          # 登录/注册
│   │   │   └── Profile/       # 用户中心
│   │   ├── components/        # 公共组件
│   │   ├── api/               # API 接口封装
│   │   ├── store/             # Pinia 状态管理
│   │   ├── router/            # 路由配置
│   │   └── utils/             # 工具函数
│   └── ...
│
├── backend/                   # 后端 (Spring Boot)
│   ├── src/main/java/
│   │   ├── controller/        # 接口层
│   │   ├── service/           # 业务逻辑层
│   │   ├── mapper/            # MyBatis-Plus Mapper
│   │   ├── entity/            # 数据实体
│   │   ├── dto/               # 数据传输对象
│   │   ├── config/            # 配置类 (Security, Redis, Neo4j 等)
│   │   ├── ai/                # AI 相关 (LLM 调用, RAG 流程)
│   │   └── graph/             # 知识图谱相关服务
│   └── ...
│
├── docker/                    # Docker 配置
│   ├── docker-compose.yml
│   ├── mysql/
│   ├── neo4j/
│   └── nginx/
│
└── docs/                      # 项目文档
```

---

## 四、核心数据模型

### MySQL 表设计（概要）

| 表名 | 说明 | 核心字段 |
|------|------|----------|
| `user` | 用户表 | id, username, password, email, avatar, level, created_at |
| `conversation` | 会话表 | id, user_id, title, created_at, updated_at |
| `message` | 消息表 | id, conversation_id, role(user/assistant), content, created_at |
| `code_snippet` | 代码片段表 | id, user_id, title, code, language, description, tags, created_at |
| `learning_path` | 学习路径表 | id, user_id, target, status, created_at |
| `learning_node` | 路径节点表 | id, path_id, knowledge_id, order, status(done/doing/todo) |
| `learning_record` | 学习记录表 | id, user_id, action_type, target_id, created_at |
| `feedback` | 反馈表 | id, message_id, user_id, rating(useful/useless), comment |

### Neo4j 节点与关系

**节点类型**:
- `Knowledge`: 知识点（name, category, description, difficulty）
- `Resource`: 学习资源（title, url, type）

**关系类型**:
- `DEPENDS_ON`: 前置知识依赖
- `CONTAINS`: 包含子知识点
- `RELATED_TO`: 关联知识
- `HAS_RESOURCE`: 知识点关联资源

---

## 五、核心 API 设计（概要）

### 认证模块
- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/logout` - 用户登出

### 智能问答模块
- `POST /api/chat/send` - 发送问题（支持流式响应 SSE）
- `GET /api/chat/conversations` - 获取会话列表
- `GET /api/chat/conversations/{id}/messages` - 获取会话消息
- `DELETE /api/chat/conversations/{id}` - 删除会话
- `POST /api/chat/feedback` - 提交答案反馈

### 知识图谱模块
- `GET /api/graph/overview` - 获取图谱概览数据
- `GET /api/graph/node/{id}` - 获取节点详情
- `GET /api/graph/node/{id}/neighbors` - 获取关联节点
- `GET /api/graph/search?keyword=xxx` - 搜索知识点

### 学习路径模块
- `POST /api/path/generate` - 生成学习路径
- `GET /api/path/list` - 获取用户路径列表
- `PUT /api/path/node/{id}/status` - 更新节点学习状态

### 代码片段模块
- `POST /api/snippets` - 保存代码片段
- `GET /api/snippets` - 获取片段列表（支持搜索/标签过滤）
- `GET /api/snippets/recommend` - 根据上下文推荐代码片段
- `PUT /api/snippets/{id}` - 更新片段
- `DELETE /api/snippets/{id}` - 删除片段

### 学习进度模块
- `GET /api/progress/dashboard` - 获取仪表盘数据
- `GET /api/progress/heatmap` - 获取学习热力图数据
- `GET /api/progress/radar` - 获取知识雷达图数据
- `GET /api/progress/report` - 生成学习报告

---

## 六、非功能需求

| 项目 | 要求 |
|------|------|
| 性能 | 问答响应首 token < 2s（流式），图谱渲染 < 1s（500 节点内） |
| 并发 | 支持 100+ 并发用户 |
| 安全 | JWT 认证，XSS/CSRF 防护，密码加密存储 |
| 可用性 | 响应式布局，支持移动端基本浏览 |
| 可扩展 | LLM 提供者可切换（OpenAI / 本地模型），向量库可替换 |
