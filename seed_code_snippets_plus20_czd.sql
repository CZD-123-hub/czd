SET NAMES utf8mb4;

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Spring Boot 全局异常处理器',
'@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", 400);
        body.put("message", ex.getMessage());
        body.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleServerError(Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", 500);
        body.put("message", "系统异常，请稍后重试");
        body.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(500).body(body);
    }
}',
'java',
'统一拦截参数异常与系统异常，输出标准化错误结构。',
'["spring-boot", "exception", "api"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Spring Boot 全局异常处理器');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Spring Boot 统一返回结果封装',
'public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.message = message;
        return r;
    }
}',
'java',
'统一接口响应结构，减少前后端联调歧义。',
'["spring-boot", "response", "java"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Spring Boot 统一返回结果封装');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'MyBatis-Plus 动态查询模板',
'public IPage<User> queryUsers(UserQueryReq req) {
    LambdaQueryWrapper<User> wrapper = Wrappers.lambdaQuery();
    wrapper.like(StringUtils.hasText(req.getKeyword()), User::getUsername, req.getKeyword())
           .eq(req.getStatus() != null, User::getStatus, req.getStatus())
           .between(req.getStartTime() != null && req.getEndTime() != null,
                   User::getCreatedAt, req.getStartTime(), req.getEndTime())
           .orderByDesc(User::getCreatedAt);

    Page<User> page = new Page<>(req.getPageNum(), req.getPageSize());
    return userMapper.selectPage(page, wrapper);
}',
'java',
'按条件动态拼接查询，适合列表页筛选场景。',
'["mybatis-plus", "query", "java"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='MyBatis-Plus 动态查询模板');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Redis 分布式锁（加锁与安全释放）',
'public boolean tryLock(String key, String requestId, long seconds) {
    Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
            key, requestId, Duration.ofSeconds(seconds));
    return Boolean.TRUE.equals(ok);
}

public void unlock(String key, String requestId) {
    String script = "if redis.call(\"get\", KEYS[1]) == ARGV[1] then " +
            "return redis.call(\"del\", KEYS[1]) else return 0 end";
    stringRedisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
            List.of(key), requestId);
}',
'java',
'使用 requestId 校验锁拥有者，防止误删他人锁。',
'["redis", "lock", "java"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Redis 分布式锁（加锁与安全释放）');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Redis 缓存旁路（Cache Aside）更新模板',
'public ProductVO getProduct(Long id) {
    String key = "product:" + id;
    String cached = stringRedisTemplate.opsForValue().get(key);
    if (cached != null) {
        return JsonUtils.parse(cached, ProductVO.class);
    }

    ProductVO db = productMapper.selectVOById(id);
    if (db != null) {
        stringRedisTemplate.opsForValue().set(key, JsonUtils.toJson(db), Duration.ofMinutes(30));
    }
    return db;
}

@Transactional
public void updateProduct(ProductUpdateReq req) {
    productMapper.update(req);
    stringRedisTemplate.delete("product:" + req.getId());
}',
'java',
'典型缓存旁路读写策略，读缓存未命中回源，写库后删缓存。',
'["redis", "cache", "java"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Redis 缓存旁路（Cache Aside）更新模板');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'MySQL Keyset 分页 SQL',
'SELECT id, title, created_at
FROM article
WHERE id < #{lastId}
ORDER BY id DESC
LIMIT #{pageSize};',
'sql',
'深分页性能优化，避免 OFFSET 大扫描。',
'["mysql", "pagination", "sql"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='MySQL Keyset 分页 SQL');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'MySQL UPSERT 模板',
'INSERT INTO user_stats(user_id, login_count, updated_at)
VALUES(#{userId}, 1, NOW())
ON DUPLICATE KEY UPDATE
    login_count = login_count + 1,
    updated_at = NOW();',
'sql',
'主键或唯一键冲突时更新，用于计数类场景。',
'["mysql", "upsert", "sql"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='MySQL UPSERT 模板');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Vue3 useRequest 通用请求 Hook',
'import { ref } from "vue";

export function useRequest<T>() {
  const loading = ref(false);
  const data = ref<T | null>(null);
  const error = ref("");

  async function run(task: () => Promise<T>) {
    loading.value = true;
    error.value = "";
    try {
      data.value = await task();
    } catch (e: any) {
      error.value = e?.message || "Request failed";
    } finally {
      loading.value = false;
    }
  }

  return { loading, data, error, run };
}',
'typescript',
'统一封装 loading/data/error，减少页面重复请求逻辑。',
'["vue3", "typescript", "composable"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Vue3 useRequest 通用请求 Hook');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Vue3 防抖搜索 composable',
'import { ref } from "vue";

export function useDebounce<T>(initial: T, delay = 300) {
  const value = ref<T>(initial);
  const debounced = ref<T>(initial);
  let timer: number | undefined;

  function setValue(next: T) {
    value.value = next;
    clearTimeout(timer);
    timer = window.setTimeout(() => {
      debounced.value = next;
    }, delay);
  }

  return { value, debounced, setValue };
}',
'typescript',
'输入联想和搜索请求常用防抖逻辑。',
'["vue3", "debounce", "typescript"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Vue3 防抖搜索 composable');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'React useDebounce Hook',
'import { useEffect, useState } from "react";

export function useDebounce<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);

  return debounced;
}',
'typescript',
'React 输入框联想、远程搜索常用 Hook。',
'["react", "hook", "typescript"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='React useDebounce Hook');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'React 请求状态管理 Hook',
'import { useState } from "react";

export function useAsync<T>() {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string>("");

  async function run(task: () => Promise<T>) {
    setLoading(true);
    setError("");
    try {
      const result = await task();
      setData(result);
    } catch (e: any) {
      setError(e?.message || "Request failed");
    } finally {
      setLoading(false);
    }
  }

  return { loading, data, error, run };
}',
'typescript',
'封装异步请求状态，提升组件复用性。',
'["react", "hook", "async"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='React 请求状态管理 Hook');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'JWT Token 生成与校验示例',
'public String createToken(String username, Long userId, SecretKey key) {
    return Jwts.builder()
            .setSubject(username)
            .claim("userId", userId)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 24 * 3600_000L))
            .signWith(key, SignatureAlgorithm.HS384)
            .compact();
}

public Claims parseToken(String token, SecretKey key) {
    return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
}',
'java',
'登录鉴权常用 JWT 生成与解析模板。',
'["jwt", "security", "java"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='JWT Token 生成与校验示例');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Spring 定时任务分布式锁模板',
'@Scheduled(cron = "0 */5 * * * ?")
public void syncJob() {
    String key = "job:sync:lock";
    String reqId = UUID.randomUUID().toString();
    boolean locked = tryLock(key, reqId, 240);
    if (!locked) {
        return;
    }
    try {
        executeSync();
    } finally {
        unlock(key, reqId);
    }
}

private void executeSync() {
    // TODO business logic
}',
'java',
'多实例部署下避免定时任务重复执行。',
'["spring", "schedule", "redis-lock"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Spring 定时任务分布式锁模板');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Docker Compose 前后端部署模板',
'version: "3.9"
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: 12345678
      MYSQL_DATABASE: coding_assistant
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql

  backend:
    image: coding-assistant-backend:latest
    environment:
      SPRING_PROFILES_ACTIVE: prod
      MYSQL_HOST: mysql
    depends_on:
      - mysql
    ports:
      - "8080:8080"

  frontend:
    image: coding-assistant-frontend:latest
    ports:
      - "80:80"

volumes:
  mysql-data:',
'yaml',
'本地或测试环境快速起整套服务。',
'["docker", "compose", "deploy"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Docker Compose 前后端部署模板');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Nginx 反向代理后端 API 模板',
'server {
    listen 80;
    server_name localhost;

    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 60s;
    }
}',
'nginx',
'前后端分离部署时常用 Nginx 配置。',
'["nginx", "proxy", "deploy"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Nginx 反向代理后端 API 模板');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Python Requests 重试封装',
'import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

def create_session() -> requests.Session:
    retry = Retry(
        total=3,
        backoff_factor=0.5,
        status_forcelist=[429, 500, 502, 503, 504],
        allowed_methods=["GET", "POST", "PUT", "DELETE"],
    )
    adapter = HTTPAdapter(max_retries=retry)
    session = requests.Session()
    session.mount("http://", adapter)
    session.mount("https://", adapter)
    return session

if __name__ == "__main__":
    s = create_session()
    r = s.get("https://httpbin.org/get", timeout=8)
    print(r.status_code)',
'python',
'外部接口调用常用重试策略，降低瞬时失败影响。',
'["python", "requests", "retry"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Python Requests 重试封装');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Python 结构化日志配置',
'import logging

def setup_logger(name: str = "app") -> logging.Logger:
    logger = logging.getLogger(name)
    logger.setLevel(logging.INFO)

    if not logger.handlers:
        handler = logging.StreamHandler()
        fmt = "%(asctime)s %(levelname)s traceId=%(trace_id)s %(message)s"
        handler.setFormatter(logging.Formatter(fmt))
        logger.addHandler(handler)

    return logger

class TraceAdapter(logging.LoggerAdapter):
    def process(self, msg, kwargs):
        extra = kwargs.get("extra", {})
        extra.setdefault("trace_id", "-")
        kwargs["extra"] = extra
        return msg, kwargs
',
'python',
'统一日志格式，便于链路追踪与检索。',
'["python", "logging", "trace"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Python 结构化日志配置');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Bash 自动化部署脚本模板',
'#!/usr/bin/env bash
set -euo pipefail

APP_NAME="coding-assistant-backend"
JAR_PATH="target/app.jar"
PID_FILE="${APP_NAME}.pid"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "jar not found: $JAR_PATH"
  exit 1
fi

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat $PID_FILE)" 2>/dev/null; then
  echo "stopping old process"
  kill "$(cat $PID_FILE)"
  sleep 2
fi

nohup java -jar "$JAR_PATH" > app.log 2>&1 &
echo $! > "$PID_FILE"
echo "started, pid=$(cat $PID_FILE)"',
'bash',
'简洁可用的后端重启部署脚本。',
'["bash", "deploy", "script"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Bash 自动化部署脚本模板');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'GitHub Actions Java + Node CI 模板',
'name: ci

on:
  push:
    branches: [ main ]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - uses: actions/setup-node@v4
        with:
          node-version: "20"

      - name: Frontend Type Check
        run: |
          cd frontend
          npm ci
          npm run type-check

      - name: Backend Test
        run: |
          cd backend
          mvn test',
'yaml',
'前后端混合项目 CI 流水线基础模板。',
'["github-actions", "ci", "yaml"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='GitHub Actions Java + Node CI 模板');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, '算法：滑动窗口最小覆盖子串模板',
'public String minWindow(String s, String t) {
    Map<Character, Integer> need = new HashMap<>();
    for (char c : t.toCharArray()) {
        need.put(c, need.getOrDefault(c, 0) + 1);
    }

    Map<Character, Integer> window = new HashMap<>();
    int left = 0, valid = 0;
    int start = 0, len = Integer.MAX_VALUE;

    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (need.containsKey(c)) {
            window.put(c, window.getOrDefault(c, 0) + 1);
            if (window.get(c).intValue() == need.get(c).intValue()) {
                valid++;
            }
        }

        while (valid == need.size()) {
            if (right - left + 1 < len) {
                start = left;
                len = right - left + 1;
            }
            char d = s.charAt(left++);
            if (need.containsKey(d)) {
                if (window.get(d).intValue() == need.get(d).intValue()) {
                    valid--;
                }
                window.put(d, window.get(d) - 1);
            }
        }
    }

    return len == Integer.MAX_VALUE ? "" : s.substring(start, start + len);
}',
'java',
'经典滑动窗口模板，可迁移到多种子串问题。',
'["algorithm", "window", "java"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='算法：滑动窗口最小覆盖子串模板');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, '算法：并查集模板（路径压缩+按秩合并）',
'class DSU {
    private final int[] parent;
    private final int[] rank;

    DSU(int n) {
        parent = new int[n + 1];
        rank = new int[n + 1];
        for (int i = 0; i <= n; i++) {
            parent[i] = i;
        }
    }

    int find(int x) {
        if (parent[x] != x) {
            parent[x] = find(parent[x]);
        }
        return parent[x];
    }

    void union(int a, int b) {
        int pa = find(a), pb = find(b);
        if (pa == pb) return;
        if (rank[pa] < rank[pb]) parent[pa] = pb;
        else if (rank[pa] > rank[pb]) parent[pb] = pa;
        else {
            parent[pb] = pa;
            rank[pa]++;
        }
    }
}',
'java',
'图连通性、冗余边检测等问题常用模板。',
'["algorithm", "dsu", "java"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='算法：并查集模板（路径压缩+按秩合并）');

INSERT INTO code_snippet (user_id, title, code, language, description, tags, use_count, created_at, updated_at)
SELECT 4, 'Axios Token 刷新拦截器模板',
'import axios from "axios";

const api = axios.create({ baseURL: "/api" });
let refreshing = false;
let queue: Array<(token: string) => void> = [];

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const { response, config } = error;
    if (response?.status !== 401 || config._retry) {
      return Promise.reject(error);
    }
    config._retry = true;

    if (!refreshing) {
      refreshing = true;
      const token = await refreshToken();
      queue.forEach((cb) => cb(token));
      queue = [];
      refreshing = false;
    }

    return new Promise((resolve) => {
      queue.push((token) => {
        config.headers.Authorization = `Bearer ${token}`;
        resolve(api(config));
      });
    });
  }
);',
'typescript',
'处理访问令牌过期的并发刷新场景。',
'["axios", "token", "typescript"]',
0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM code_snippet WHERE user_id=4 AND title='Axios Token 刷新拦截器模板');
