SET NAMES utf8mb4;

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Java 并发工具包实战：ThreadPoolExecutor 与 CompletableFuture',
'## 技术说明
Java 并发开发的核心是任务拆分、线程池隔离和结果编排。ThreadPoolExecutor 负责线程资源治理，CompletableFuture 负责异步编排。
合理配置核心线程数、队列长度、拒绝策略，可以避免线程暴涨和请求雪崩。

## 最佳实践
1. CPU 密集任务与 IO 密集任务分开线程池。
2. 为关键异步链路设置超时与降级逻辑。
3. 线程池命名规范化，便于日志排查。
4. 拒绝策略优先选择可观测方案，例如记录日志并告警。

## 示例代码
```java
ThreadPoolExecutor pool = new ThreadPoolExecutor(
    8, 16, 60, TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(200),
    new ThreadPoolExecutor.CallerRunsPolicy()
);

CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> "A", pool);
CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> "B", pool);
String result = f1.thenCombine(f2, (a, b) -> a + b)
                  .orTimeout(2, TimeUnit.SECONDS)
                  .exceptionally(ex -> "fallback")
                  .join();
```',
'java', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Java 并发工具包实战：ThreadPoolExecutor 与 CompletableFuture');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Java 异常体系设计与统一封装',
'## 技术说明
异常体系用于表达业务失败与系统失败。清晰的异常分层能让调用方快速定位错误来源。
推荐建立统一业务异常基类，配合错误码和友好文案，避免出现大量不可读的通用异常。

## 最佳实践
1. 业务异常与系统异常分离。
2. 错误码语义稳定，不随文案频繁变动。
3. 对外响应隐藏栈信息，对内日志保留全量细节。
4. 在网关或全局异常处理中统一转换响应结构。

## 示例代码
```java
public class BizException extends RuntimeException {
    private final int code;
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
    public int getCode() { return code; }
}

if (stock < 1) {
    throw new BizException(40001, "库存不足");
}
```',
'java', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Java 异常体系设计与统一封装');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Spring Security JWT 鉴权全链路',
'## 技术说明
JWT 鉴权链路通常包含登录签发、请求拦截校验、权限判定、刷新机制。无状态认证能提升横向扩展能力。
在 Spring Security 中通常通过过滤器解析 Token，并将用户身份写入 SecurityContext。

## 最佳实践
1. Token 设置过期时间并区分 access 与 refresh。
2. 签名密钥放在安全配置中心，不硬编码。
3. 关键权限点使用注解或统一拦截策略。
4. 对鉴权失败统一返回 401 或 403，避免前端处理分裂。

## 示例代码
```java
String token = Jwts.builder()
    .setSubject(username)
    .claim("userId", userId)
    .setIssuedAt(new Date())
    .setExpiration(new Date(System.currentTimeMillis() + 3600_000))
    .signWith(secretKey, SignatureAlgorithm.HS384)
    .compact();
```',
'spring', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Spring Security JWT 鉴权全链路');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Spring Boot 配置管理与多环境发布',
'## 技术说明
多环境配置管理用于区分开发、测试、生产环境参数，减少环境切换错误。
Spring Boot 支持 profile 机制按环境加载不同配置文件，结合环境变量可提升发布安全性。

## 最佳实践
1. 敏感配置通过环境变量或密钥管理系统注入。
2. profile 文件仅保留差异项，公共配置统一在基础文件。
3. 发布前做配置校验，避免缺失关键参数。
4. 记录版本与配置变更，支持快速回滚。

## 示例代码
```yaml
spring:
  profiles:
    active: dev
---
spring:
  config:
    activate:
      on-profile: prod
server:
  port: 8080
```',
'spring', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Spring Boot 配置管理与多环境发布');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Redis 发布订阅与 Stream 消息队列选型',
'## 技术说明
Redis PubSub 适合实时广播但不保证消息持久化，Redis Stream 适合可追溯、可消费组管理的消息场景。
两者应根据业务可靠性要求选型，不应混用导致语义不清。

## 最佳实践
1. 强一致消息场景优先 Stream。
2. 广播通知场景可选 PubSub。
3. 消费端实现重试和死信策略。
4. 监控积压长度与消费延迟，避免消息堆积。

## 示例代码
```bash
XADD order_stream * orderId 1001 status created
XGROUP CREATE order_stream order_group 0 MKSTREAM
XREADGROUP GROUP order_group c1 COUNT 10 STREAMS order_stream >
```',
'redis', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Redis 发布订阅与 Stream 消息队列选型');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Redis 缓存预热与热点 Key 治理',
'## 技术说明
缓存预热用于在业务高峰前将核心数据提前加载到缓存，降低冷启动压力。
热点 Key 治理用于防止单点高并发打爆缓存或后端数据库。

## 最佳实践
1. 启动阶段按优先级分批预热。
2. 热点 Key 使用本地缓存与多副本分散压力。
3. 对热点接口增加限流与降级。
4. 定期统计 Top Key 并优化过期策略。

## 示例代码
```java
List<Long> hotIds = List.of(101L, 102L, 103L);
for (Long id : hotIds) {
    String key = "product:" + id;
    String value = loadFromDb(id);
    redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(30));
}
```',
'redis', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Redis 缓存预热与热点 Key 治理');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'MySQL 分库分表与分页查询优化',
'## 技术说明
当单表数据量和写入压力持续增长时，可通过分库分表提升容量与并发能力。
分片后分页查询要避免跨分片大范围扫描，建议结合业务时间和主键范围进行路由。

## 最佳实践
1. 先做读写分离和索引优化，再考虑分库分表。
2. 分片键优先选择高离散且稳定字段。
3. 分页优先游标方式，减少深分页成本。
4. 建立分片路由与扩容预案。

## 示例代码
```sql
SELECT id, order_no, created_at
FROM order_2026_04
WHERE id < 980000
ORDER BY id DESC
LIMIT 20;
```',
'mysql', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'MySQL 分库分表与分页查询优化');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'MySQL 索引失效案例与排查清单',
'## 技术说明
索引失效常见原因包括函数计算、隐式类型转换、前导模糊匹配、复合索引使用顺序错误。
排查应结合 EXPLAIN、慢日志和执行耗时，形成可复用清单。

## 最佳实践
1. 避免 where 条件对索引列做函数处理。
2. 保持字段类型一致，防止隐式转换。
3. like 查询尽量避免前置百分号。
4. 联合索引遵循最左前缀原则。

## 示例代码
```sql
-- 失效示例
SELECT * FROM user WHERE DATE(created_at) = "2026-04-15";

-- 优化示例
SELECT * FROM user
WHERE created_at >= "2026-04-15 00:00:00"
  AND created_at <  "2026-04-16 00:00:00";
```',
'mysql', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'MySQL 索引失效案例与排查清单');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Vue 3 路由权限控制与动态菜单',
'## 技术说明
路由权限控制通常基于用户角色、资源权限和菜单配置实现。动态菜单可由后端返回权限树，再映射为前端路由。
这样可以实现按角色展示页面并避免未授权访问。

## 最佳实践
1. 路由守卫统一做鉴权和重定向。
2. 权限模型保持前后端一致。
3. 菜单渲染与路由注册解耦。
4. 未授权页面返回统一提示，增强可用性。

## 示例代码
```ts
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem("token");
  if (!token && to.path !== "/login") return next("/login");
  const roles = authStore.roles;
  if (to.meta.role && !roles.includes(String(to.meta.role))) {
    return next("/403");
  }
  next();
});
```',
'vue', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Vue 3 路由权限控制与动态菜单');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Vue 3 表单校验与可复用组件封装',
'## 技术说明
中后台场景常见大量表单，统一校验规则和复用组件可显著提升开发效率与一致性。
推荐将规则配置、字段渲染、提交逻辑解耦，构建可扩展的表单框架。

## 最佳实践
1. 通用校验规则抽离成独立模块。
2. 表单组件保持受控模式，便于状态追踪。
3. 提交前执行统一校验与去重提交控制。
4. 对复杂字段使用插槽扩展，避免组件爆炸。

## 示例代码
```ts
const rules = {
  username: [{ required: true, message: "用户名必填", trigger: "blur" }],
  email: [{ type: "email", message: "邮箱格式错误", trigger: "blur" }],
};

async function submit(formRef: any) {
  await formRef.validate();
  await api.save(formModel.value);
}
```',
'vue', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Vue 3 表单校验与可复用组件封装');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'React Hooks 最佳实践与陷阱规避',
'## 技术说明
Hooks 提供函数组件状态与副作用能力，但依赖管理不当容易造成闭包陷阱、重复渲染和请求竞态。
通过规范化依赖和副作用清理可以提升稳定性。

## 最佳实践
1. useEffect 依赖项完整声明。
2. 异步请求组件卸载时要取消或忽略回写。
3. 复杂状态优先 useReducer。
4. 自定义 Hook 保持单一职责。

## 示例代码
```tsx
useEffect(() => {
  let active = true;
  fetchData().then((res) => {
    if (active) setData(res);
  });
  return () => { active = false; };
}, [query]);
```',
'react', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'React Hooks 最佳实践与陷阱规避');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'React Query 服务端状态管理实战',
'## 技术说明
服务端状态与本地 UI 状态应该分离管理。React Query 能提供缓存、去重请求、自动重试和失效刷新能力。
在列表、详情、搜索场景能显著减少手写状态逻辑。

## 最佳实践
1. queryKey 设计要稳定且可表达查询维度。
2. 变更后主动失效相关缓存。
3. 合理配置 staleTime 和 gcTime。
4. 对高频查询开启防抖，避免抖动请求。

## 示例代码
```tsx
const query = useQuery({
  queryKey: ["users", page, keyword],
  queryFn: () => api.getUsers({ page, keyword }),
  staleTime: 30_000,
});

const mutation = useMutation({
  mutationFn: api.updateUser,
  onSuccess: () => queryClient.invalidateQueries({ queryKey: ["users"] }),
});
```',
'react', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'React Query 服务端状态管理实战');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Docker 镜像安全扫描与最小化实践',
'## 技术说明
容器安全包括基础镜像可信、依赖漏洞扫描、运行权限最小化、敏感信息保护。
最小化镜像可以降低攻击面并减少部署体积。

## 最佳实践
1. 使用官方或可信基础镜像。
2. 多阶段构建移除编译工具链。
3. 运行用户使用非 root。
4. 发布前执行漏洞扫描并建立阈值门禁。

## 示例代码
```dockerfile
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY app.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

```bash
trivy image my-app:latest
```',
'docker', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Docker 镜像安全扫描与最小化实践');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Docker 容器资源限制与故障排查',
'## 技术说明
容器默认可占用较多宿主机资源，生产环境应设置 CPU 与内存上限，避免单容器抢占资源影响整体稳定性。
故障排查要结合日志、事件、资源监控综合判断。

## 最佳实践
1. 为关键服务设置资源配额。
2. 开启健康检查并结合重启策略。
3. 关注 OOMKilled 和重启频次。
4. 统一日志采集和容器指标监控。

## 示例代码
```yaml
services:
  backend:
    image: app-backend:latest
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 1024M
```

```bash
docker inspect --format="{{.State.OOMKilled}}" backend
```',
'docker', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Docker 容器资源限制与故障排查');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT '算法专题：单调栈应用模板',
'## 技术说明
单调栈用于在 O(n) 时间内求解下一个更大元素、柱状图最大矩形、接雨水等问题。
核心思想是维护栈内元素单调性，在破坏单调时弹栈计算答案。

## 最佳实践
1. 明确栈中存索引还是值。
2. 统一处理边界，常用哨兵元素降低分支复杂度。
3. 弹栈时立即计算贡献，避免漏算。
4. 写完后用递增和递减数组做对拍。

## 示例代码
```java
public int[] nextGreater(int[] nums) {
    int n = nums.length;
    int[] ans = new int[n];
    Arrays.fill(ans, -1);
    Deque<Integer> st = new ArrayDeque<>();
    for (int i = 0; i < n; i++) {
        while (!st.isEmpty() && nums[i] > nums[st.peek()]) {
            ans[st.pop()] = nums[i];
        }
        st.push(i);
    }
    return ans;
}
```',
'algorithm', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = '算法专题：单调栈应用模板');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT '算法专题：滑动窗口与双指针',
'## 技术说明
滑动窗口适用于连续区间问题，双指针适用于有序数组或区间收缩问题。
通过维护左右边界和窗口状态，可将暴力 O(n^2) 降到 O(n)。

## 最佳实践
1. 先定义窗口含义和合法条件。
2. 右指针负责扩张，左指针负责收缩。
3. 维护窗口内计数结构，保证更新 O(1)。
4. 重点测试边界输入和空窗口场景。

## 示例代码
```java
public int minSubArrayLen(int target, int[] nums) {
    int l = 0, sum = 0, ans = Integer.MAX_VALUE;
    for (int r = 0; r < nums.length; r++) {
        sum += nums[r];
        while (sum >= target) {
            ans = Math.min(ans, r - l + 1);
            sum -= nums[l++];
        }
    }
    return ans == Integer.MAX_VALUE ? 0 : ans;
}
```',
'algorithm', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = '算法专题：滑动窗口与双指针');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'CI/CD 流水线门禁设计与回滚策略',
'## 技术说明
流水线门禁用于在合并前自动执行质量检查，减少缺陷进入主干。
核心阶段通常包括静态检查、单元测试、构建打包、部署验证、灰度发布。

## 最佳实践
1. 关键门禁失败必须阻断合并。
2. 流水线耗时控制在可接受范围内，避免团队绕过。
3. 发布前后保留版本元数据，支持快速回滚。
4. 生产发布采用灰度或金丝雀降低风险。

## 示例代码
```yaml
jobs:
  quality:
    steps:
      - run: npm run type-check
      - run: npm test
      - run: mvn test
```

```text
回滚策略：记录上一个稳定版本号，发布失败自动切回。
```',
'other', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'CI/CD 流水线门禁设计与回滚策略');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT '接口设计规范：REST 版本化与幂等',
'## 技术说明
REST 接口规范化能提升可维护性和兼容性。版本化用于平滑升级，幂等用于防止重复请求导致数据污染。
常见版本化方式是路径版本和请求头版本。

## 最佳实践
1. 资源命名使用名词复数，动作语义交给 HTTP 方法。
2. 写接口设计幂等键和去重机制。
3. 错误响应结构统一，便于前端处理。
4. 大变更走新版本，旧版本保留过渡期。

## 示例代码
```http
POST /api/v1/orders
Idempotency-Key: 20260415-1001
```

```json
{
  "code": 200,
  "message": "success",
  "data": {"orderId": 1001}
}
```',
'other', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = '接口设计规范：REST 版本化与幂等');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT '系统设计：限流、熔断与降级策略',
'## 技术说明
高并发系统需要通过限流、熔断、降级三层机制保护核心服务。
限流控制入口流量，熔断阻断故障扩散，降级保障基础可用。

## 最佳实践
1. 按接口和用户维度实施分层限流。
2. 对下游依赖配置超时与熔断阈值。
3. 降级策略明确兜底返回，避免空白页。
4. 定期压测验证阈值是否合理。

## 示例代码
```java
if (!rateLimiter.tryAcquire()) {
    return ApiResponse.error(429, "请求过于频繁");
}

if (circuitBreaker.isOpen()) {
    return ApiResponse.error(503, "服务繁忙，请稍后重试");
}
```',
'other', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = '系统设计：限流、熔断与降级策略');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT '测试工程：单元测试、集成测试、端到端测试分层',
'## 技术说明
测试分层能平衡覆盖率与执行成本。单元测试关注函数逻辑，集成测试关注模块协作，端到端测试关注真实用户路径。
合理分层能让问题更快定位并减少回归风险。

## 最佳实践
1. 单元测试覆盖核心分支与边界条件。
2. 集成测试覆盖关键业务链路。
3. 端到端测试聚焦高价值场景，避免过度依赖。
4. 测试纳入 CI 门禁并输出稳定报告。

## 示例代码
```text
测试金字塔建议比例：
单元测试 70% + 集成测试 20% + 端到端测试 10%
```

```java
@Test
void should_return_error_when_stock_not_enough() {
    assertThrows(BizException.class, () -> service.createOrder("p1", 999));
}
```',
'other', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = '测试工程：单元测试、集成测试、端到端测试分层');
