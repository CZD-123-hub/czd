SET NAMES utf8mb4;

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Spring Boot 请求日志切面',
'@Aspect
@Component
@Slf4j
public class RequestLogAspect {

    @Around("execution(* com.example..controller..*(..))")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String method = pjp.getSignature().toShortString();
        try {
            Object result = pjp.proceed();
            log.info("api={} cost={}ms", method, System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("api={} failed cost={}ms msg={}", method, System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }
}',
'java',
'统一记录接口耗时与异常，便于性能分析和排障。',
'["spring-boot", "aop", "logging"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Spring Boot 请求日志切面');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Spring Boot 参数绑定失败处理',
'@RestControllerAdvice
public class BindExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, Object> handle(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ":" + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Map.of("code", 400, "message", msg);
    }
}',
'java',
'统一处理 DTO 参数校验失败并输出字段级错误信息。',
'["spring-boot", "validation", "exception"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Spring Boot 参数绑定失败处理');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Java 文件类型与大小校验工具',
'public class FileValidator {
    private static final Set<String> ALLOW = Set.of("png", "jpg", "jpeg", "pdf");
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("文件大小超过限制");
        }
        String name = Objects.requireNonNull(file.getOriginalFilename()).toLowerCase();
        String ext = name.substring(name.lastIndexOf(".") + 1);
        if (!ALLOW.contains(ext)) {
            throw new IllegalArgumentException("文件类型不支持");
        }
    }
}',
'java',
'上传场景常用校验逻辑：空文件、大小限制、扩展名白名单。',
'["java", "upload", "validate"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Java 文件类型与大小校验工具');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Java BigDecimal 金额计算模板',
'public class AmountCalc {
    public static BigDecimal fee(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal total(BigDecimal amount, BigDecimal discount, BigDecimal tax) {
        return amount.subtract(discount).add(tax).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }
}',
'java',
'避免浮点精度问题的金额计算模板。',
'["java", "bigdecimal", "finance"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Java BigDecimal 金额计算模板');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Java CompletableFuture 批量并发查询',
'public List<UserVO> batchQuery(List<Long> userIds) {
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
        List<CompletableFuture<UserVO>> futures = userIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> userService.getById(id), pool))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return futures.stream().map(CompletableFuture::join).toList();
    } finally {
        pool.shutdown();
    }
}',
'java',
'批量并发查询常用模板，减少串行 IO 耗时。',
'["java", "concurrency", "completablefuture"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Java CompletableFuture 批量并发查询');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'MyBatis XML foreach 批量插入',
'<insert id="batchInsert" parameterType="java.util.List">
  INSERT INTO user (username, email, status)
  VALUES
  <foreach collection="list" item="item" separator=",">
    (#{item.username}, #{item.email}, #{item.status})
  </foreach>
</insert>',
'xml',
'MyBatis 批量插入 XML 模板，适合中等规模批处理。',
'["mybatis", "batch", "xml"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='MyBatis XML foreach 批量插入');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'SQL 最近7天活跃用户统计',
'SELECT DATE(created_at) AS dt, COUNT(DISTINCT user_id) AS active_users
FROM learning_record
WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
GROUP BY DATE(created_at)
ORDER BY dt;',
'sql',
'按天统计最近 7 天活跃用户数量。',
'["sql", "mysql", "statistics"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='SQL 最近7天活跃用户统计');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'SQL 条件聚合行转列模板',
'SELECT
  SUM(CASE WHEN status = "todo" THEN 1 ELSE 0 END) AS todo_count,
  SUM(CASE WHEN status = "doing" THEN 1 ELSE 0 END) AS doing_count,
  SUM(CASE WHEN status = "done" THEN 1 ELSE 0 END) AS done_count
FROM learning_node
WHERE path_id = #{pathId};',
'sql',
'通过 CASE WHEN 做状态分桶统计。',
'["sql", "aggregation", "mysql"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='SQL 条件聚合行转列模板');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Redis Lua 限流脚本',
'local key = KEYS[1]
local limit = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])
local current = tonumber(redis.call("get", key) or "0")
if current + 1 > limit then
  return 0
end
current = redis.call("incr", key)
if current == 1 then
  redis.call("expire", key, ttl)
end
return 1',
'lua',
'固定窗口限流脚本，适合接口基础限流。',
'["redis", "lua", "rate-limit"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Redis Lua 限流脚本');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Redis 延迟双删策略模板',
'public void updateAndInvalidate(Long id, ProductUpdateReq req) {
    productMapper.update(req);
    String key = "product:" + id;
    redisTemplate.delete(key);
    scheduler.schedule(() -> redisTemplate.delete(key), 500, TimeUnit.MILLISECONDS);
}',
'java',
'写库后立即删缓存 + 延迟再次删除，降低并发脏读概率。',
'["redis", "cache", "consistency"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Redis 延迟双删策略模板');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Vue3 权限指令 v-permission',
'import type { Directive } from "vue";

export const permissionDirective: Directive = {
  mounted(el, binding) {
    const need = String(binding.value || "");
    const perms = JSON.parse(localStorage.getItem("permissions") || "[]");
    if (need && !perms.includes(need)) {
      el.parentNode?.removeChild(el);
    }
  },
};',
'typescript',
'前端按钮级权限控制常用指令。',
'["vue3", "directive", "permission"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Vue3 权限指令 v-permission');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Vue3 文件下载封装',
'import axios from "axios";

export async function downloadFile(url: string, filename: string) {
  const res = await axios.get(url, { responseType: "blob" });
  const blob = new Blob([res.data]);
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
}',
'typescript',
'统一处理前端 Blob 下载逻辑。',
'["vue3", "download", "typescript"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Vue3 文件下载封装');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'React 错误边界组件',
'import React from "react";

type State = { hasError: boolean };

export class ErrorBoundary extends React.Component<{ children: React.ReactNode }, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error: any) {
    console.error("UI Error:", error);
  }

  render() {
    if (this.state.hasError) return <div>页面异常，请刷新重试。</div>;
    return this.props.children;
  }
}',
'typescript',
'捕获 React 子树渲染异常，避免整页崩溃。',
'["react", "error-boundary", "typescript"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='React 错误边界组件');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'React 表单提交防重复 Hook',
'import { useState } from "react";

export function useSubmitLock() {
  const [submitting, setSubmitting] = useState(false);

  async function run(task: () => Promise<void>) {
    if (submitting) return;
    setSubmitting(true);
    try {
      await task();
    } finally {
      setSubmitting(false);
    }
  }

  return { submitting, run };
}',
'typescript',
'防止用户重复点击提交按钮导致重复请求。',
'["react", "form", "hook"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='React 表单提交防重复 Hook');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'TypeScript 深拷贝工具（结构化）',
'export function deepClone<T>(obj: T): T {
  if (typeof structuredClone === "function") {
    return structuredClone(obj);
  }
  return JSON.parse(JSON.stringify(obj));
}',
'typescript',
'优先使用 structuredClone，兼容场景回退 JSON 方案。',
'["typescript", "utils", "clone"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='TypeScript 深拷贝工具（结构化）');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Python FastAPI 健康检查接口',
'from fastapi import FastAPI
from datetime import datetime

app = FastAPI()

@app.get("/health")
def health():
    return {
        "status": "ok",
        "timestamp": datetime.utcnow().isoformat()
    }',
'python',
'服务可用性探针接口模板，便于容器与网关探活。',
'["python", "fastapi", "health"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Python FastAPI 健康检查接口');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Python CSV 清洗去重模板',
'import pandas as pd

def clean_csv(path: str, out: str):
    df = pd.read_csv(path)
    df = df.drop_duplicates()
    df = df.dropna(how="all")
    df.columns = [c.strip() for c in df.columns]
    df.to_csv(out, index=False)

if __name__ == "__main__":
    clean_csv("input.csv", "output.csv")',
'python',
'数据预处理常用：去重、去空行、列名清洗。',
'["python", "pandas", "csv"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Python CSV 清洗去重模板');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Bash 按天归档日志脚本',
'#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="./logs"
ARCHIVE_DIR="./archive"
DATE=$(date +%Y-%m-%d)

mkdir -p "$ARCHIVE_DIR/$DATE"
find "$LOG_DIR" -type f -name "*.log" -mtime +0 -print0 | while IFS= read -r -d "" file; do
  mv "$file" "$ARCHIVE_DIR/$DATE/"
done

echo "archive done: $DATE"',
'bash',
'每天归档旧日志文件，减小运行目录压力。',
'["bash", "log", "archive"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Bash 按天归档日志脚本');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Docker Buildx 多架构构建命令',
'docker buildx create --use --name multiarch-builder
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t your-registry/your-app:latest \
  --push .',
'bash',
'一次构建并推送多架构镜像，适配不同部署环境。',
'["docker", "buildx", "multiarch"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Docker Buildx 多架构构建命令');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Nginx Gzip 与静态缓存配置',
'http {
    gzip on;
    gzip_types text/plain text/css application/json application/javascript;
    gzip_min_length 1024;

    server {
        location /assets/ {
            expires 30d;
            add_header Cache-Control "public, max-age=2592000";
        }
    }
}',
'nginx',
'提升静态资源传输效率与浏览器缓存命中率。',
'["nginx", "gzip", "cache"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Nginx Gzip 与静态缓存配置');
