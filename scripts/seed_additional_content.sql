USE coding_assistant;

-- ============================================================
-- Existing DB incremental content seed (idempotent style)
-- Purpose:
-- 1) Add extra code snippets for one existing user (the smallest user id)
-- 2) Add extra knowledge documents if title not exists
-- Notes:
-- - No table drop / truncate
-- - Safe to run multiple times
-- ============================================================

-- --------------------------------------
-- Resolve one valid owner user id
-- --------------------------------------
SET @seed_user_id := (SELECT id FROM `user` ORDER BY id ASC LIMIT 1);

-- --------------------------------------
-- Extra code snippets (for @seed_user_id)
-- --------------------------------------
INSERT INTO `code_snippet`
(`user_id`, `title`, `code`, `language`, `description`, `tags`, `use_count`, `created_at`, `updated_at`)
SELECT
  @seed_user_id,
  'Spring 全局异常处理器',
  '@RestControllerAdvice\npublic class GlobalExceptionHandler {\n\n    @ExceptionHandler(IllegalArgumentException.class)\n    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {\n        Map<String, Object> body = new HashMap<>();\n        body.put("code", 400);\n        body.put("message", ex.getMessage());\n        body.put("timestamp", System.currentTimeMillis());\n        return ResponseEntity.badRequest().body(body);\n    }\n}',
  'java',
  'Spring Boot 通用异常返回模板，便于统一接口错误格式',
  '["spring-boot", "exception", "api"]',
  6,
  '2026-03-21 10:00:00',
  '2026-03-21 10:00:00'
FROM DUAL
WHERE @seed_user_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `code_snippet`
    WHERE `user_id` = @seed_user_id
      AND `title` = 'Spring 全局异常处理器'
  );

INSERT INTO `code_snippet`
(`user_id`, `title`, `code`, `language`, `description`, `tags`, `use_count`, `created_at`, `updated_at`)
SELECT
  @seed_user_id,
  'Vue useRequest 组合函数',
  'import { ref } from "vue"\n\nexport function useRequest<T>() {\n  const loading = ref(false)\n  const data = ref<T | null>(null)\n  const error = ref<string>("")\n\n  async function run(task: () => Promise<T>) {\n    loading.value = true\n    error.value = ""\n    try {\n      data.value = await task()\n    } catch (e: any) {\n      error.value = e?.message || "Request failed"\n    } finally {\n      loading.value = false\n    }\n  }\n\n  return { loading, data, error, run }\n}',
  'typescript',
  '封装请求加载态、错误态和结果数据的通用 Composable',
  '["vue3", "typescript", "composable"]',
  4,
  '2026-03-22 09:30:00',
  '2026-03-22 09:30:00'
FROM DUAL
WHERE @seed_user_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `code_snippet`
    WHERE `user_id` = @seed_user_id
      AND `title` = 'Vue useRequest 组合函数'
  );

INSERT INTO `code_snippet`
(`user_id`, `title`, `code`, `language`, `description`, `tags`, `use_count`, `created_at`, `updated_at`)
SELECT
  @seed_user_id,
  'Redis 简易分布式锁',
  'public boolean tryLock(String key, String requestId, long seconds) {\n    Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, requestId, Duration.ofSeconds(seconds));\n    return Boolean.TRUE.equals(ok);\n}\n\npublic void unlock(String key, String requestId) {\n    String current = stringRedisTemplate.opsForValue().get(key);\n    if (requestId.equals(current)) {\n        stringRedisTemplate.delete(key);\n    }\n}',
  'java',
  '基于 Redis setIfAbsent 的轻量级分布式锁工具方法',
  '["redis", "lock", "java"]',
  9,
  '2026-03-23 08:00:00',
  '2026-03-23 08:00:00'
FROM DUAL
WHERE @seed_user_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `code_snippet`
    WHERE `user_id` = @seed_user_id
      AND `title` = 'Redis 简易分布式锁'
  );

INSERT INTO `code_snippet`
(`user_id`, `title`, `code`, `language`, `description`, `tags`, `use_count`, `created_at`, `updated_at`)
SELECT
  @seed_user_id,
  'MySQL Keyset 分页',
  'SELECT id, title, created_at\nFROM article\nWHERE id < #{lastId}\nORDER BY id DESC\nLIMIT #{pageSize};',
  'sql',
  '大表分页时优先使用 Keyset，避免深分页扫描开销',
  '["mysql", "sql", "pagination"]',
  5,
  '2026-03-24 14:20:00',
  '2026-03-24 14:20:00'
FROM DUAL
WHERE @seed_user_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `code_snippet`
    WHERE `user_id` = @seed_user_id
      AND `title` = 'MySQL Keyset 分页'
  );

-- --------------------------------------
-- Extra knowledge documents
-- --------------------------------------
INSERT INTO `knowledge_document` (`title`, `content`, `category`, `embedding`, `created_at`)
SELECT
  'Java 并发实践速览',
  'Java 并发开发建议优先使用线程池，避免频繁创建原生线程。共享状态优先使用不可变对象，读写并发场景使用 ConcurrentHashMap。异步流程建议用 CompletableFuture 组合，并且为阻塞调用设置超时。',
  'java',
  NULL,
  '2026-03-21 09:00:00'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `knowledge_document` WHERE `title` = 'Java 并发实践速览'
);

INSERT INTO `knowledge_document` (`title`, `content`, `category`, `embedding`, `created_at`)
SELECT
  'Spring Boot 错误响应规范',
  '建议统一 API 错误结构，至少包含 code、message、traceId。业务异常在 service 层抛出，在全局异常处理器中转换为标准响应。对外不暴露堆栈细节，详细信息仅保留在日志中。',
  'spring',
  NULL,
  '2026-03-21 09:10:00'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `knowledge_document` WHERE `title` = 'Spring Boot 错误响应规范'
);

INSERT INTO `knowledge_document` (`title`, `content`, `category`, `embedding`, `created_at`)
SELECT
  'Redis 缓存问题处理策略',
  '缓存击穿可以通过互斥锁与逻辑过期缓解。缓存穿透可通过空值缓存和布隆过滤器处理。缓存雪崩建议使用随机过期时间并配合降级兜底策略。',
  'redis',
  NULL,
  '2026-03-21 09:20:00'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `knowledge_document` WHERE `title` = 'Redis 缓存问题处理策略'
);

INSERT INTO `knowledge_document` (`title`, `content`, `category`, `embedding`, `created_at`)
SELECT
  'MySQL 索引设计检查清单',
  '索引应基于查询模式设计，而不是凭感觉。联合索引遵循最左前缀原则，避免在 WHERE 中对索引列做函数运算。使用 EXPLAIN 观察 type、key 等字段，并控制索引数量平衡写入成本。',
  'mysql',
  NULL,
  '2026-03-21 09:30:00'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `knowledge_document` WHERE `title` = 'MySQL 索引设计检查清单'
);

INSERT INTO `knowledge_document` (`title`, `content`, `category`, `embedding`, `created_at`)
SELECT
  'Vue 3 性能优化笔记',
  '衍生状态优先使用 computed，避免模板中出现复杂表达式。大列表按需拆分组件并合理设置 key，减少不必要深度监听。输入触发请求时建议防抖，降低渲染和网络压力。',
  'vue',
  NULL,
  '2026-03-21 09:40:00'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `knowledge_document` WHERE `title` = 'Vue 3 性能优化笔记'
);

INSERT INTO `knowledge_document` (`title`, `content`, `category`, `embedding`, `created_at`)
SELECT
  'Docker Compose 部署基线',
  '推荐在 compose 中补齐 healthcheck、restart 策略和持久化卷。服务依赖建议等待健康状态而非仅等待容器启动。敏感配置放到 env 文件，避免硬编码密钥。',
  'docker',
  NULL,
  '2026-03-21 09:50:00'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `knowledge_document` WHERE `title` = 'Docker Compose 部署基线'
);
