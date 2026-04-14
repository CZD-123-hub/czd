CREATE DATABASE IF NOT EXISTS coding_assistant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE coding_assistant;

-- User table
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    `email` VARCHAR(100) NOT NULL UNIQUE,
    `avatar` VARCHAR(255) DEFAULT NULL,
    `level` VARCHAR(20) DEFAULT 'beginner',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_username` (`username`),
    INDEX `idx_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Conversation table
CREATE TABLE IF NOT EXISTS `conversation` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `title` VARCHAR(200) DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_conversation_user_id` (`user_id`),
    CONSTRAINT `fk_conversation_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Message table
CREATE TABLE IF NOT EXISTS `message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `conversation_id` BIGINT NOT NULL,
    `role` VARCHAR(20) NOT NULL,
    `content` TEXT NOT NULL,
    `sources` JSON DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_message_conversation_id` (`conversation_id`),
    CONSTRAINT `fk_message_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Feedback table
CREATE TABLE IF NOT EXISTS `feedback` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `message_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `rating` VARCHAR(10) NOT NULL,
    `comment` TEXT DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_feedback_message_user` (`message_id`, `user_id`),
    CONSTRAINT `fk_feedback_message` FOREIGN KEY (`message_id`) REFERENCES `message` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_feedback_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Code snippet table
CREATE TABLE IF NOT EXISTS `code_snippet` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `code` TEXT NOT NULL,
    `language` VARCHAR(30) NOT NULL,
    `description` TEXT DEFAULT NULL,
    `tags` JSON DEFAULT NULL,
    `use_count` INT DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_snippet_user_id` (`user_id`),
    CONSTRAINT `fk_snippet_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Learning path table
CREATE TABLE IF NOT EXISTS `learning_path` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `target` VARCHAR(200) NOT NULL,
    `status` VARCHAR(20) DEFAULT 'active',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_path_user_id` (`user_id`),
    CONSTRAINT `fk_path_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Learning node table
CREATE TABLE IF NOT EXISTS `learning_node` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `path_id` BIGINT NOT NULL,
    `knowledge_id` VARCHAR(100) NOT NULL,
    `node_order` INT NOT NULL,
    `status` VARCHAR(20) DEFAULT 'todo',
    `resource_urls` JSON DEFAULT NULL,
    INDEX `idx_node_path_id` (`path_id`),
    CONSTRAINT `fk_node_path` FOREIGN KEY (`path_id`) REFERENCES `learning_path` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Learning record table
CREATE TABLE IF NOT EXISTS `learning_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `action_type` VARCHAR(30) NOT NULL,
    `target_id` VARCHAR(100) DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_record_user_created` (`user_id`, `created_at`),
    CONSTRAINT `fk_record_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Knowledge document table (for RAG)
CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(200) NOT NULL,
    `content` MEDIUMTEXT NOT NULL,
    `category` VARCHAR(50) DEFAULT NULL,
    `embedding` JSON DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_doc_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 测试数据
-- ============================================================

-- -------------------- 用户 --------------------
-- 密码均为 12345678（BCrypt 加密）
-- 注意：此 BCrypt 哈希通过 Spring Security 的 BCryptPasswordEncoder 生成
INSERT INTO `user` (`id`, `username`, `password`, `email`, `avatar`, `level`, `created_at`, `updated_at`) VALUES
(1, 'zhangsan', '$2a$10$EIY1/UbJkBHUGQMmibMrFOfl1UeJIGLIWH4EvAVRBGnSMXxZq9F5e', 'zhangsan@example.com', NULL, 'intermediate', '2025-12-01 10:00:00', '2026-03-18 09:00:00'),
(2, 'lisi',     '$2a$10$EIY1/UbJkBHUGQMmibMrFOfl1UeJIGLIWH4EvAVRBGnSMXxZq9F5e', 'lisi@example.com',     NULL, 'beginner',     '2026-01-15 14:30:00', '2026-03-19 16:00:00'),
(3, 'wangwu',   '$2a$10$EIY1/UbJkBHUGQMmibMrFOfl1UeJIGLIWH4EvAVRBGnSMXxZq9F5e', 'wangwu@example.com',   NULL, 'advanced',     '2026-01-20 08:00:00', '2026-03-20 11:00:00');

-- -------------------- 会话 --------------------
INSERT INTO `conversation` (`id`, `user_id`, `title`, `created_at`, `updated_at`) VALUES
(1, 1, 'Spring Boot 整合 Redis',              '2026-03-10 09:00:00', '2026-03-10 09:15:00'),
(2, 1, 'MyBatis-Plus 分页查询',               '2026-03-12 14:00:00', '2026-03-12 14:30:00'),
(3, 1, 'Vue3 Composition API 入门',           '2026-03-15 10:00:00', '2026-03-15 10:45:00'),
(4, 2, 'Java 基础 - 集合框架',                '2026-03-11 16:00:00', '2026-03-11 16:20:00'),
(5, 2, 'MySQL 索引优化',                      '2026-03-14 11:00:00', '2026-03-14 11:30:00'),
(6, 3, 'Docker Compose 多服务编排',           '2026-03-13 08:00:00', '2026-03-13 08:40:00'),
(7, 3, '设计模式 - 工厂模式与策略模式',       '2026-03-16 15:00:00', '2026-03-16 15:25:00');

-- -------------------- 消息 --------------------
-- 会话1: Spring Boot 整合 Redis
INSERT INTO `message` (`id`, `conversation_id`, `role`, `content`, `sources`, `created_at`) VALUES
(1,  1, 'user',      'Spring Boot 如何整合 Redis？', NULL, '2026-03-10 09:00:00'),
(2,  1, 'assistant', '## Spring Boot 整合 Redis\n\nSpring Boot 整合 Redis 非常简单，主要分以下几步：\n\n### 1. 添加依赖\n\n```xml\n<dependency>\n    <groupId>org.springframework.boot</groupId>\n    <artifactId>spring-boot-starter-data-redis</artifactId>\n</dependency>\n```\n\n### 2. 配置连接信息\n\n```yaml\nspring:\n  redis:\n    host: localhost\n    port: 6379\n    password: your-password\n    database: 0\n```\n\n### 3. 使用 RedisTemplate\n\n```java\n@Autowired\nprivate RedisTemplate<String, Object> redisTemplate;\n\npublic void setValue(String key, Object value) {\n    redisTemplate.opsForValue().set(key, value);\n}\n\npublic Object getValue(String key) {\n    return redisTemplate.opsForValue().get(key);\n}\n```\n\n### 原理说明\n\nSpring Boot 通过 `spring-boot-starter-data-redis` 自动配置了 `RedisConnectionFactory` 和 `RedisTemplate`，底层使用 Lettuce 作为 Redis 客户端。', '["redis", "spring-boot"]', '2026-03-10 09:00:30'),
(3,  1, 'user',      '如何配置 Redis 序列化方式？', NULL, '2026-03-10 09:05:00'),
(4,  1, 'assistant', '## Redis 序列化配置\n\n默认的 `RedisTemplate` 使用 JDK 序列化，建议替换为 JSON 序列化：\n\n```java\n@Configuration\npublic class RedisConfig {\n\n    @Bean\n    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {\n        RedisTemplate<String, Object> template = new RedisTemplate<>();\n        template.setConnectionFactory(factory);\n        \n        // Key 使用 String 序列化\n        template.setKeySerializer(new StringRedisSerializer());\n        template.setHashKeySerializer(new StringRedisSerializer());\n        \n        // Value 使用 JSON 序列化\n        Jackson2JsonRedisSerializer<Object> jsonSerializer = \n            new Jackson2JsonRedisSerializer<>(Object.class);\n        template.setValueSerializer(jsonSerializer);\n        template.setHashValueSerializer(jsonSerializer);\n        \n        template.afterPropertiesSet();\n        return template;\n    }\n}\n```\n\n这样存储的数据在 Redis 中可读性更好，也便于其他语言读取。', '["redis", "spring-boot"]', '2026-03-10 09:05:30');

-- 会话2: MyBatis-Plus 分页查询
INSERT INTO `message` (`id`, `conversation_id`, `role`, `content`, `sources`, `created_at`) VALUES
(5,  2, 'user',      'MyBatis-Plus 怎么实现分页查询？', NULL, '2026-03-12 14:00:00'),
(6,  2, 'assistant', '## MyBatis-Plus 分页查询\n\n### 1. 配置分页插件\n\n```java\n@Configuration\npublic class MyBatisPlusConfig {\n    @Bean\n    public MybatisPlusInterceptor mybatisPlusInterceptor() {\n        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();\n        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));\n        return interceptor;\n    }\n}\n```\n\n### 2. 使用分页\n\n```java\n// Service 层\npublic IPage<User> getUserPage(int current, int size) {\n    Page<User> page = new Page<>(current, size);\n    return userMapper.selectPage(page, null);\n}\n\n// 带条件的分页查询\npublic IPage<User> searchUsers(int current, int size, String keyword) {\n    Page<User> page = new Page<>(current, size);\n    LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();\n    wrapper.like(User::getUsername, keyword);\n    return userMapper.selectPage(page, wrapper);\n}\n```\n\n### 原理\n\nMyBatis-Plus 的分页插件会拦截 SQL，自动添加 `LIMIT` 语句，并执行 `COUNT` 查询获取总记录数。', '["mybatis-plus", "mysql"]', '2026-03-12 14:00:45'),
(7,  2, 'user',      '如何自定义分页 SQL？', NULL, '2026-03-12 14:10:00'),
(8,  2, 'assistant', '## 自定义分页 SQL\n\n在 Mapper 接口中定义方法，第一个参数为 `IPage`：\n\n```java\n// Mapper 接口\n@Mapper\npublic interface UserMapper extends BaseMapper<User> {\n    IPage<UserVO> selectUserPage(IPage<UserVO> page, @Param(\"keyword\") String keyword);\n}\n```\n\n```xml\n<!-- UserMapper.xml -->\n<select id=\"selectUserPage\" resultType=\"com.example.vo.UserVO\">\n    SELECT u.id, u.username, u.email, COUNT(c.id) as chat_count\n    FROM user u\n    LEFT JOIN conversation c ON u.id = c.user_id\n    WHERE u.username LIKE CONCAT(\'%\', #{keyword}, \'%\')\n    GROUP BY u.id\n</select>\n```\n\nMyBatis-Plus 会自动为自定义 SQL 添加分页支持。', '["mybatis-plus"]', '2026-03-12 14:10:30');

-- 会话3: Vue3 Composition API
INSERT INTO `message` (`id`, `conversation_id`, `role`, `content`, `sources`, `created_at`) VALUES
(9,  3, 'user',      'Vue3 的 Composition API 怎么用？和 Options API 有什么区别？', NULL, '2026-03-15 10:00:00'),
(10, 3, 'assistant', '## Vue 3 Composition API\n\n### 基本用法\n\n```vue\n<script setup lang=\"ts\">\nimport { ref, computed, onMounted } from \"vue\"\n\n// 响应式状态\nconst count = ref(0)\nconst doubleCount = computed(() => count.value * 2)\n\n// 方法\nfunction increment() {\n  count.value++\n}\n\n// 生命周期\nonMounted(() => {\n  console.log(\"组件已挂载\")\n})\n</script>\n\n<template>\n  <button @click=\"increment\">{{ count }} x 2 = {{ doubleCount }}</button>\n</template>\n```\n\n### 与 Options API 的区别\n\n| 特性 | Options API | Composition API |\n|------|------------|----------------|\n| 代码组织 | 按选项类型分组 | 按逻辑功能分组 |\n| 逻辑复用 | Mixins（有命名冲突） | Composables（清晰明确） |\n| TypeScript | 支持较弱 | 完美支持 |\n| 代码量 | 较多模板代码 | 更简洁 |\n\nComposition API 最大的优势在于**逻辑关注点的聚合**，相关代码放在一起，项目越大优势越明显。', '["vue3", "javascript"]', '2026-03-15 10:01:00');

-- 会话4: Java集合框架
INSERT INTO `message` (`id`, `conversation_id`, `role`, `content`, `sources`, `created_at`) VALUES
(11, 4, 'user',      'Java 中 ArrayList 和 LinkedList 有什么区别？', NULL, '2026-03-11 16:00:00'),
(12, 4, 'assistant', '## ArrayList vs LinkedList\n\n### 底层实现\n- **ArrayList**: 基于动态数组，内存连续\n- **LinkedList**: 基于双向链表，内存不连续\n\n### 性能对比\n\n| 操作 | ArrayList | LinkedList |\n|------|-----------|------------|\n| 随机访问 get(i) | O(1) ✅ | O(n) ❌ |\n| 尾部添加 add() | O(1) 均摊 | O(1) |\n| 头部插入 add(0,e) | O(n) ❌ | O(1) ✅ |\n| 中间插入 | O(n) | O(n) |\n| 内存占用 | 较小 | 较大（需存储前后指针） |\n\n### 使用建议\n\n```java\n// 大部分场景用 ArrayList（随机访问多）\nList<String> list = new ArrayList<>();\n\n// 频繁在头部/中间插入删除用 LinkedList\nDeque<String> deque = new LinkedList<>();\n```\n\n**实际开发中 90% 以上场景用 ArrayList 即可**，因为 CPU 缓存对连续内存更友好。', '["java", "data-structures"]', '2026-03-11 16:00:45');

-- 会话5: MySQL索引优化
INSERT INTO `message` (`id`, `conversation_id`, `role`, `content`, `sources`, `created_at`) VALUES
(13, 5, 'user',      'MySQL 怎么优化慢查询？索引该怎么加？', NULL, '2026-03-14 11:00:00'),
(14, 5, 'assistant', '## MySQL 慢查询优化\n\n### 1. 开启慢查询日志\n\n```sql\nSET GLOBAL slow_query_log = ON;\nSET GLOBAL long_query_time = 1;  -- 超过1秒记录\n```\n\n### 2. 使用 EXPLAIN 分析\n\n```sql\nEXPLAIN SELECT * FROM user WHERE username = ''zhangsan'';\n```\n\n关注 `type` 列：ALL(全表) > index > range > ref > const\n\n### 3. 索引优化原则\n\n```sql\n-- 单列索引\nCREATE INDEX idx_username ON user(username);\n\n-- 联合索引（遵循最左前缀原则）\nCREATE INDEX idx_user_status_time ON order(user_id, status, created_at);\n\n-- 覆盖索引（避免回表）\nSELECT user_id, status FROM order WHERE user_id = 1;\n```\n\n### 4. 常见优化技巧\n- 避免 `SELECT *`，只查需要的字段\n- 避免在索引列上使用函数：`WHERE YEAR(created_at) = 2024` ❌\n- 用 `LIMIT` 限制返回行数\n- 大表分页用游标分页替代 `OFFSET`', '["mysql", "database"]', '2026-03-14 11:01:00');

-- 会话6: Docker Compose
INSERT INTO `message` (`id`, `conversation_id`, `role`, `content`, `sources`, `created_at`) VALUES
(15, 6, 'user',      'Docker Compose 如何编排多个服务？', NULL, '2026-03-13 08:00:00'),
(16, 6, 'assistant', '## Docker Compose 多服务编排\n\n### 示例：Spring Boot + MySQL + Redis\n\n```yaml\nversion: \"3.8\"\nservices:\n  mysql:\n    image: mysql:8.0\n    environment:\n      MYSQL_ROOT_PASSWORD: root123\n      MYSQL_DATABASE: myapp\n    ports:\n      - \"3306:3306\"\n    volumes:\n      - mysql-data:/var/lib/mysql\n    healthcheck:\n      test: [\"CMD\", \"mysqladmin\", \"ping\", \"-h\", \"localhost\"]\n      interval: 10s\n      timeout: 5s\n      retries: 5\n\n  redis:\n    image: redis:7-alpine\n    ports:\n      - \"6379:6379\"\n\n  app:\n    build: .\n    ports:\n      - \"8080:8080\"\n    depends_on:\n      mysql:\n        condition: service_healthy\n    environment:\n      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/myapp\n\nvolumes:\n  mysql-data:\n```\n\n### 常用命令\n\n```bash\ndocker-compose up -d          # 后台启动\ndocker-compose logs -f app    # 查看日志\ndocker-compose down -v        # 停止并删除卷\n```\n\n关键点：服务间通过**服务名**互相访问（如 `mysql:3306`），Docker Compose 自动创建网络。', '["docker"]', '2026-03-13 08:01:00');

-- 会话7: 设计模式
INSERT INTO `message` (`id`, `conversation_id`, `role`, `content`, `sources`, `created_at`) VALUES
(17, 7, 'user',      '工厂模式和策略模式分别在什么场景下使用？', NULL, '2026-03-16 15:00:00'),
(18, 7, 'assistant', '## 工厂模式 vs 策略模式\n\n### 工厂模式（Factory）\n\n**场景**：根据条件创建不同类型的对象\n\n```java\npublic interface MessageSender {\n    void send(String message);\n}\n\npublic class SenderFactory {\n    public static MessageSender create(String type) {\n        return switch (type) {\n            case \"email\" -> new EmailSender();\n            case \"sms\" -> new SmsSender();\n            case \"wechat\" -> new WechatSender();\n            default -> throw new IllegalArgumentException(\"Unknown type: \" + type);\n        };\n    }\n}\n```\n\n### 策略模式（Strategy）\n\n**场景**：运行时动态切换算法/行为\n\n```java\npublic interface PricingStrategy {\n    double calculate(double price);\n}\n\n@Service\npublic class OrderService {\n    private final Map<String, PricingStrategy> strategies;\n    \n    public double calculatePrice(String vipLevel, double price) {\n        return strategies.get(vipLevel).calculate(price);\n    }\n}\n```\n\n### 核心区别\n\n| | 工厂模式 | 策略模式 |\n|--|---------|--------|\n| 关注点 | **创建**对象 | **使用**对象 |\n| 目的 | 封装对象创建逻辑 | 封装可互换的算法 |\n| 典型场景 | 数据库驱动选择 | 支付方式、排序算法 |\n\n实际项目中两者经常**组合使用**：工厂负责创建策略对象，策略负责执行具体逻辑。', '["design-patterns", "java"]', '2026-03-16 15:01:00');

-- -------------------- 反馈 --------------------
INSERT INTO `feedback` (`id`, `message_id`, `user_id`, `rating`, `comment`, `created_at`) VALUES
(1, 2,  1, 'useful',   '解释很清楚，代码示例直接可用',       '2026-03-10 09:10:00'),
(2, 4,  1, 'useful',   NULL,                                '2026-03-10 09:15:00'),
(3, 6,  1, 'useful',   '分页插件配置很实用',                 '2026-03-12 14:20:00'),
(4, 10, 1, 'useful',   '对比表格一目了然',                   '2026-03-15 10:10:00'),
(5, 12, 2, 'useful',   NULL,                                '2026-03-11 16:10:00'),
(6, 14, 2, 'useful',   'EXPLAIN 分析这部分很有帮助',         '2026-03-14 11:15:00'),
(7, 16, 3, 'useful',   NULL,                                '2026-03-13 08:20:00'),
(8, 18, 3, 'useless',  '希望能有更多实际项目中的例子',       '2026-03-16 15:15:00');

-- -------------------- 代码片段 --------------------
INSERT INTO `code_snippet` (`id`, `user_id`, `title`, `code`, `language`, `description`, `tags`, `use_count`, `created_at`, `updated_at`) VALUES
(1, 1, 'Spring Boot 跨域配置', '@Configuration\npublic class CorsConfig implements WebMvcConfigurer {\n    @Override\n    public void addCorsMappings(CorsRegistry registry) {\n        registry.addMapping(\"/api/**\")\n            .allowedOriginPatterns(\"*\")\n            .allowedMethods(\"GET\", \"POST\", \"PUT\", \"DELETE\")\n            .allowedHeaders(\"*\")\n            .allowCredentials(true)\n            .maxAge(3600);\n    }\n}', 'java', 'Spring Boot 全局跨域配置，允许前端跨域访问 API', '["spring-boot", "cors", "配置"]', 12, '2026-02-01 10:00:00', '2026-03-18 09:00:00'),

(2, 1, 'MyBatis-Plus 通用分页查询', 'public <T> PageResult<T> queryPage(IPage<T> page, LambdaQueryWrapper<T> wrapper) {\n    IPage<T> result = baseMapper.selectPage(page, wrapper);\n    return new PageResult<>(\n        result.getRecords(),\n        result.getTotal(),\n        (int) result.getCurrent(),\n        (int) result.getSize()\n    );\n}', 'java', 'MyBatis-Plus 封装的通用分页查询方法', '["mybatis-plus", "分页", "工具"]', 8, '2026-02-10 14:00:00', '2026-03-15 11:00:00'),

(3, 1, 'Vue3 防抖 Hook', 'import { ref } from \"vue\"\n\nexport function useDebounce<T>(value: T, delay = 300) {\n  const debouncedValue = ref(value)\n  let timer: ReturnType<typeof setTimeout>\n\n  function update(newValue: T) {\n    clearTimeout(timer)\n    timer = setTimeout(() => {\n      debouncedValue.value = newValue as any\n    }, delay)\n  }\n\n  return { debouncedValue, update }\n}', 'typescript', 'Vue 3 组合式 API 防抖 Hook', '["vue3", "hooks", "typescript"]', 5, '2026-02-15 09:00:00', '2026-03-10 08:00:00'),

(4, 1, 'Axios 请求拦截器', 'import axios from \"axios\"\n\nconst request = axios.create({\n  baseURL: \"/api\",\n  timeout: 15000,\n})\n\nrequest.interceptors.request.use(\n  (config) => {\n    const token = localStorage.getItem(\"token\")\n    if (token) {\n      config.headers.Authorization = `Bearer ${token}`\n    }\n    return config\n  },\n  (error) => Promise.reject(error)\n)\n\nrequest.interceptors.response.use(\n  (response) => response.data,\n  (error) => {\n    if (error.response?.status === 401) {\n      localStorage.removeItem(\"token\")\n      window.location.href = \"/login\"\n    }\n    return Promise.reject(error)\n  }\n)\n\nexport default request', 'typescript', 'Axios 封装：自动附加 JWT Token，401 自动跳转登录', '["axios", "jwt", "typescript"]', 15, '2026-01-20 11:00:00', '2026-03-19 10:00:00'),

(5, 2, 'Java Stream 常用操作', 'List<String> names = users.stream()\n    .filter(u -> u.getAge() > 18)\n    .sorted(Comparator.comparing(User::getName))\n    .map(User::getName)\n    .distinct()\n    .collect(Collectors.toList());\n\n// 分组\nMap<String, List<User>> grouped = users.stream()\n    .collect(Collectors.groupingBy(User::getDepartment));\n\n// 统计\nDoubleSummaryStatistics stats = users.stream()\n    .mapToDouble(User::getSalary)\n    .summaryStatistics();', 'java', 'Java Stream API 常用操作速查', '["java", "stream", "集合"]', 20, '2026-01-25 15:00:00', '2026-03-17 13:00:00'),

(6, 2, 'MySQL 建表模板', 'CREATE TABLE IF NOT EXISTS `table_name` (\n    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT ''主键'',\n    `name`       VARCHAR(100) NOT NULL COMMENT ''名称'',\n    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT ''状态: 0-禁用 1-启用'',\n    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',\n    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',\n    `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT ''逻辑删除: 0-未删除 1-已删除'',\n    PRIMARY KEY (`id`),\n    KEY `idx_status` (`status`),\n    KEY `idx_created_at` (`created_at`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''表注释'';', 'sql', 'MySQL 标准建表模板，包含逻辑删除和时间戳', '["mysql", "sql", "模板"]', 10, '2026-02-05 10:00:00', '2026-03-12 15:00:00'),

(7, 3, 'Docker 多阶段构建 Dockerfile', 'FROM maven:3.9-eclipse-temurin-21 AS build\nWORKDIR /app\nCOPY pom.xml .\nRUN mvn dependency:go-offline -B\nCOPY src ./src\nRUN mvn package -DskipTests -B\n\nFROM eclipse-temurin:21-jre-alpine\nWORKDIR /app\nCOPY --from=build /app/target/*.jar app.jar\nEXPOSE 8080\nENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]', 'dockerfile', 'Spring Boot 项目多阶段构建 Dockerfile，减小镜像体积', '["docker", "spring-boot", "部署"]', 7, '2026-02-20 16:00:00', '2026-03-14 09:00:00'),

(8, 3, 'Git 常用命令速查', '# 分支操作\ngit checkout -b feature/xxx     # 创建并切换分支\ngit merge --no-ff feature/xxx   # 合并分支（保留提交历史）\ngit branch -d feature/xxx       # 删除已合并分支\n\n# 暂存操作\ngit stash                       # 暂存当前修改\ngit stash pop                   # 恢复暂存\n\n# 回退操作\ngit reset --soft HEAD~1         # 撤销最近一次提交（保留修改）\ngit checkout -- file.txt        # 丢弃文件修改\n\n# 查看历史\ngit log --oneline --graph -20   # 图形化查看最近20条', 'bash', 'Git 日常开发常用命令', '["git", "工具", "命令"]', 18, '2026-01-18 14:00:00', '2026-03-20 10:00:00');

-- -------------------- 学习路径 --------------------
INSERT INTO `learning_path` (`id`, `user_id`, `target`, `status`, `created_at`, `updated_at`) VALUES
(1, 1, '掌握 Spring Boot 微服务开发',   'active',    '2026-02-01 10:00:00', '2026-03-18 09:00:00'),
(2, 1, '前端 Vue3 全家桶',              'active',    '2026-03-01 08:00:00', '2026-03-15 10:00:00'),
(3, 2, 'Java 后端开发入门',             'active',    '2026-02-15 14:00:00', '2026-03-19 16:00:00'),
(4, 3, 'DevOps 与容器化部署',           'completed', '2026-01-20 09:00:00', '2026-03-10 11:00:00');

-- -------------------- 学习路径节点 --------------------
-- 路径1: Spring Boot 微服务
INSERT INTO `learning_node` (`id`, `path_id`, `knowledge_id`, `node_order`, `status`, `resource_urls`) VALUES
(1,  1, 'java',             1, 'done',    '["https://docs.oracle.com/javase/tutorial/"]'),
(2,  1, 'spring-boot',      2, 'done',    '["https://spring.io/guides/gs/spring-boot/"]'),
(3,  1, 'spring-mvc',       3, 'done',    '["https://docs.spring.io/spring-framework/reference/web/webmvc.html"]'),
(4,  1, 'spring-security',  4, 'doing',   '["https://spring.io/guides/gs/securing-web/"]'),
(5,  1, 'mybatis',          5, 'todo',    '["https://mybatis.org/mybatis-3/"]'),
(6,  1, 'mysql',            6, 'todo',    '["https://dev.mysql.com/doc/refman/8.0/en/"]'),
(7,  1, 'redis',            7, 'todo',    '["https://redis.io/docs/getting-started/"]'),
(8,  1, 'docker',           8, 'todo',    '["https://docs.docker.com/get-started/"]'),
(9,  1, 'microservices',    9, 'todo',    '["https://microservices.io/"]');

-- 路径2: Vue3 全家桶
INSERT INTO `learning_node` (`id`, `path_id`, `knowledge_id`, `node_order`, `status`, `resource_urls`) VALUES
(10, 2, 'javascript',   1, 'done',  '["https://javascript.info/"]'),
(11, 2, 'typescript',   2, 'done',  '["https://www.typescriptlang.org/docs/"]'),
(12, 2, 'vue3',         3, 'doing', '["https://vuejs.org/guide/introduction.html"]'),
(13, 2, 'vue-router',   4, 'todo',  '["https://router.vuejs.org/"]'),
(14, 2, 'pinia',        5, 'todo',  '["https://pinia.vuejs.org/"]'),
(15, 2, 'vite',         6, 'todo',  '["https://vitejs.dev/guide/"]');

-- 路径3: Java 后端入门
INSERT INTO `learning_node` (`id`, `path_id`, `knowledge_id`, `node_order`, `status`, `resource_urls`) VALUES
(16, 3, 'java',         1, 'doing', '["https://docs.oracle.com/javase/tutorial/"]'),
(17, 3, 'data-structures', 2, 'todo', '["https://visualgo.net/"]'),
(18, 3, 'mysql',        3, 'todo',  '["https://www.mysqltutorial.org/"]'),
(19, 3, 'spring-boot',  4, 'todo',  '["https://spring.io/quickstart"]'),
(20, 3, 'rest-api',     5, 'todo',  '["https://restfulapi.net/"]');

-- 路径4: DevOps（已完成）
INSERT INTO `learning_node` (`id`, `path_id`, `knowledge_id`, `node_order`, `status`, `resource_urls`) VALUES
(21, 4, 'linux',        1, 'done',  '["https://linuxcommand.org/"]'),
(22, 4, 'git',          2, 'done',  '["https://git-scm.com/book/"]'),
(23, 4, 'docker',       3, 'done',  '["https://docs.docker.com/"]'),
(24, 4, 'kubernetes',   4, 'done',  '["https://kubernetes.io/docs/tutorials/"]'),
(25, 4, 'ci-cd',        5, 'done',  '["https://docs.github.com/en/actions"]');

-- -------------------- 学习记录 --------------------
-- 为三个用户生成过去三个月的学习记录，用于仪表盘、热力图和雷达图

-- 用户1 (zhangsan) - 活跃用户，学习记录丰富
INSERT INTO `learning_record` (`user_id`, `action_type`, `target_id`, `created_at`) VALUES
-- 1月记录
(1, 'chat',           '1',            '2026-01-05 09:30:00'),
(1, 'chat',           '1',            '2026-01-05 10:15:00'),
(1, 'code_save',      '4',            '2026-01-05 11:00:00'),
(1, 'graph_explore',  'java',         '2026-01-08 14:00:00'),
(1, 'chat',           '1',            '2026-01-08 14:30:00'),
(1, 'graph_explore',  'spring-boot',  '2026-01-10 09:00:00'),
(1, 'chat',           '1',            '2026-01-10 09:30:00'),
(1, 'path_learn',     '1',            '2026-01-12 16:00:00'),
(1, 'chat',           '1',            '2026-01-15 10:00:00'),
(1, 'code_save',      '1',            '2026-01-15 10:30:00'),
(1, 'graph_explore',  'mysql',        '2026-01-18 08:00:00'),
(1, 'chat',           '1',            '2026-01-20 11:00:00'),
(1, 'chat',           '1',            '2026-01-20 14:00:00'),
(1, 'path_learn',     '2',            '2026-01-22 15:00:00'),
(1, 'code_save',      '2',            '2026-01-25 09:00:00'),
(1, 'chat',           '1',            '2026-01-28 10:00:00'),
(1, 'graph_explore',  'redis',        '2026-01-28 11:00:00'),
(1, 'chat',           '1',            '2026-01-30 16:00:00'),
-- 2月记录
(1, 'chat',           '1',            '2026-02-01 09:00:00'),
(1, 'code_save',      '1',            '2026-02-01 09:30:00'),
(1, 'graph_explore',  'spring-mvc',   '2026-02-03 14:00:00'),
(1, 'chat',           '1',            '2026-02-03 14:30:00'),
(1, 'path_learn',     '3',            '2026-02-05 10:00:00'),
(1, 'chat',           '1',            '2026-02-07 11:00:00'),
(1, 'chat',           '1',            '2026-02-07 15:00:00'),
(1, 'graph_explore',  'docker',       '2026-02-10 09:00:00'),
(1, 'code_save',      '7',            '2026-02-10 09:30:00'),
(1, 'chat',           '1',            '2026-02-12 10:00:00'),
(1, 'chat',           '1',            '2026-02-14 14:00:00'),
(1, 'graph_explore',  'vue3',         '2026-02-14 15:00:00'),
(1, 'path_learn',     '10',           '2026-02-16 16:00:00'),
(1, 'chat',           '1',            '2026-02-18 09:00:00'),
(1, 'code_save',      '3',            '2026-02-18 09:30:00'),
(1, 'chat',           '1',            '2026-02-20 11:00:00'),
(1, 'graph_explore',  'typescript',   '2026-02-22 10:00:00'),
(1, 'chat',           '1',            '2026-02-22 10:30:00'),
(1, 'path_learn',     '11',           '2026-02-24 14:00:00'),
(1, 'chat',           '1',            '2026-02-26 15:00:00'),
(1, 'code_save',      '4',            '2026-02-26 15:30:00'),
(1, 'chat',           '1',            '2026-02-28 10:00:00'),
-- 3月记录
(1, 'chat',           '1',            '2026-03-01 09:00:00'),
(1, 'graph_explore',  'spring-security', '2026-03-01 10:00:00'),
(1, 'chat',           '1',            '2026-03-03 14:00:00'),
(1, 'chat',           '1',            '2026-03-03 14:30:00'),
(1, 'code_save',      '2',            '2026-03-05 09:00:00'),
(1, 'graph_explore',  'mybatis',      '2026-03-05 10:00:00'),
(1, 'chat',           '1',            '2026-03-07 11:00:00'),
(1, 'path_learn',     '4',            '2026-03-08 16:00:00'),
(1, 'chat',           '1',            '2026-03-10 09:00:00'),
(1, 'chat',           '1',            '2026-03-10 09:15:00'),
(1, 'code_save',      '1',            '2026-03-10 10:00:00'),
(1, 'graph_explore',  'rest-api',     '2026-03-12 08:00:00'),
(1, 'chat',           '2',            '2026-03-12 14:00:00'),
(1, 'chat',           '2',            '2026-03-12 14:30:00'),
(1, 'graph_explore',  'vue3',         '2026-03-14 10:00:00'),
(1, 'chat',           '3',            '2026-03-15 10:00:00'),
(1, 'code_save',      '3',            '2026-03-15 10:30:00'),
(1, 'chat',           '1',            '2026-03-17 09:00:00'),
(1, 'graph_explore',  'design-patterns', '2026-03-17 10:00:00'),
(1, 'chat',           '1',            '2026-03-18 14:00:00'),
(1, 'code_save',      '5',            '2026-03-18 15:00:00'),
(1, 'feedback',       '2',            '2026-03-10 09:10:00'),
(1, 'feedback',       '4',            '2026-03-10 09:15:00'),
(1, 'feedback',       '6',            '2026-03-12 14:20:00'),
(1, 'feedback',       '10',           '2026-03-15 10:10:00'),
(1, 'chat',           '1',            '2026-03-19 09:00:00'),
(1, 'chat',           '1',            '2026-03-20 10:00:00');

-- 用户2 (lisi) - 初学者，记录较少
INSERT INTO `learning_record` (`user_id`, `action_type`, `target_id`, `created_at`) VALUES
(2, 'chat',           '4',            '2026-02-15 14:00:00'),
(2, 'graph_explore',  'java',         '2026-02-15 15:00:00'),
(2, 'chat',           '4',            '2026-02-18 10:00:00'),
(2, 'graph_explore',  'data-structures', '2026-02-20 09:00:00'),
(2, 'chat',           '4',            '2026-02-22 14:00:00'),
(2, 'code_save',      '5',            '2026-02-25 11:00:00'),
(2, 'chat',           '4',            '2026-03-01 10:00:00'),
(2, 'graph_explore',  'java',         '2026-03-03 09:00:00'),
(2, 'chat',           '4',            '2026-03-05 14:00:00'),
(2, 'path_learn',     '16',           '2026-03-08 16:00:00'),
(2, 'chat',           '4',            '2026-03-11 16:00:00'),
(2, 'graph_explore',  'mysql',        '2026-03-12 10:00:00'),
(2, 'chat',           '5',            '2026-03-14 11:00:00'),
(2, 'code_save',      '6',            '2026-03-14 12:00:00'),
(2, 'chat',           '5',            '2026-03-16 09:00:00'),
(2, 'graph_explore',  'spring-boot',  '2026-03-18 14:00:00'),
(2, 'feedback',       '12',           '2026-03-11 16:10:00'),
(2, 'feedback',       '14',           '2026-03-14 11:15:00'),
(2, 'chat',           '4',            '2026-03-19 16:00:00');

-- 用户3 (wangwu) - 高级用户
INSERT INTO `learning_record` (`user_id`, `action_type`, `target_id`, `created_at`) VALUES
(3, 'chat',           '6',            '2026-01-20 08:00:00'),
(3, 'graph_explore',  'docker',       '2026-01-20 09:00:00'),
(3, 'path_learn',     '21',           '2026-01-22 16:00:00'),
(3, 'chat',           '6',            '2026-01-25 10:00:00'),
(3, 'graph_explore',  'kubernetes',   '2026-01-25 11:00:00'),
(3, 'path_learn',     '22',           '2026-01-28 15:00:00'),
(3, 'code_save',      '7',            '2026-01-30 09:00:00'),
(3, 'chat',           '6',            '2026-02-01 14:00:00'),
(3, 'path_learn',     '23',           '2026-02-05 16:00:00'),
(3, 'graph_explore',  'ci-cd',        '2026-02-08 10:00:00'),
(3, 'chat',           '6',            '2026-02-10 09:00:00'),
(3, 'path_learn',     '24',           '2026-02-15 15:00:00'),
(3, 'code_save',      '8',            '2026-02-18 11:00:00'),
(3, 'chat',           '6',            '2026-02-20 14:00:00'),
(3, 'graph_explore',  'linux',        '2026-02-22 09:00:00'),
(3, 'path_learn',     '25',           '2026-02-25 16:00:00'),
(3, 'chat',           '7',            '2026-03-01 10:00:00'),
(3, 'graph_explore',  'design-patterns', '2026-03-03 14:00:00'),
(3, 'chat',           '7',            '2026-03-05 11:00:00'),
(3, 'code_save',      '7',            '2026-03-08 09:00:00'),
(3, 'graph_explore',  'microservices','2026-03-10 10:00:00'),
(3, 'chat',           '6',            '2026-03-13 08:00:00'),
(3, 'code_save',      '8',            '2026-03-15 14:00:00'),
(3, 'chat',           '7',            '2026-03-16 15:00:00'),
(3, 'graph_explore',  'java',         '2026-03-18 10:00:00'),
(3, 'feedback',       '16',           '2026-03-13 08:20:00'),
(3, 'feedback',       '18',           '2026-03-16 15:15:00'),
(3, 'chat',           '7',            '2026-03-20 11:00:00');