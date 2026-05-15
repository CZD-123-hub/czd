SET NAMES utf8mb4;

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Java NIO 与 Netty 网络编程实战',
'## 技术说明
Java NIO 通过 Channel、Buffer、Selector 实现非阻塞 IO，适合高并发连接场景。Netty 在 NIO 基础上封装了事件驱动模型和高性能编解码体系，常用于网关、IM、RPC。

## 最佳实践
1. 将网络读写与业务处理线程隔离，避免 IO 线程阻塞。
2. 对消息协议做长度字段和版本字段设计，便于扩展。
3. 连接生命周期中统一处理心跳、超时和异常关闭。
4. 大流量场景下使用对象池和零拷贝优化内存分配。

## 示例代码
```java
EventLoopGroup boss = new NioEventLoopGroup(1);
EventLoopGroup worker = new NioEventLoopGroup();
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(boss, worker)
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new StringDecoder());
                ch.pipeline().addLast(new StringEncoder());
                ch.pipeline().addLast(new SimpleChannelInboundHandler<String>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                        ctx.writeAndFlush("echo:" + msg);
                    }
                });
            }
        });
```',
'java', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Java NIO 与 Netty 网络编程实战');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Java 反射与泛型在工程中的应用边界',
'## 技术说明
反射提供运行时类型访问能力，泛型提供编译期类型安全。反射能提升框架灵活性，但滥用会带来性能损耗和可读性下降。

## 最佳实践
1. 业务代码优先静态类型，反射用于框架层或低频初始化路径。
2. 对反射结果做缓存，避免重复查找 Method 和 Field。
3. 泛型边界使用 extends 和 super 明确读写语义。
4. 保留必要注释说明反射目的和风险点。

## 示例代码
```java
public class GenericRepo<T> {
    private final Class<T> type;
    public GenericRepo(Class<T> type) { this.type = type; }

    public T newInstance() throws Exception {
        return type.getDeclaredConstructor().newInstance();
    }
}

List<? extends Number> nums = List.of(1, 2, 3);
Number first = nums.get(0);
```',
'java', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Java 反射与泛型在工程中的应用边界');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Spring Boot 事件驱动架构与领域事件',
'## 技术说明
事件驱动架构将业务流程拆分为事件发布与事件消费，降低模块耦合。Spring 提供 ApplicationEventPublisher 和 EventListener 支持同步或异步事件处理。

## 最佳实践
1. 事件对象只包含必要字段，避免传递过重上下文。
2. 关键事件处理增加重试与补偿机制。
3. 异步监听器线程池独立配置。
4. 事件命名体现业务语义，便于追踪。

## 示例代码
```java
public record OrderCreatedEvent(Long orderId, Long userId) {}

@Service
public class OrderService {
  @Autowired private ApplicationEventPublisher publisher;
  public void createOrder(Long orderId, Long userId) {
    // 保存订单
    publisher.publishEvent(new OrderCreatedEvent(orderId, userId));
  }
}

@Component
public class OrderListener {
  @EventListener
  public void handle(OrderCreatedEvent event) {
    // 发券、积分、通知
  }
}
```',
'spring', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Spring Boot 事件驱动架构与领域事件');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Spring Boot 参数校验与错误信息国际化',
'## 技术说明
参数校验可以在接口边界提前拦截非法请求，减少业务层防御代码。Bean Validation 配合全局异常处理可形成统一校验反馈。

## 最佳实践
1. DTO 层做结构和格式校验，Service 层做业务校验。
2. 校验错误响应结构统一，便于前端展示。
3. 多语言系统将校验信息放入消息资源文件。
4. 对复杂校验编写自定义注解和校验器。

## 示例代码
```java
public class UserCreateReq {
  @NotBlank(message = "user.name.required")
  private String username;

  @Email(message = "user.email.invalid")
  private String email;
}

@PostMapping("/users")
public ApiResponse<Void> create(@Valid @RequestBody UserCreateReq req) {
  return ApiResponse.success();
}
```',
'spring', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Spring Boot 参数校验与错误信息国际化');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Redis Lua 脚本与原子操作设计',
'## 技术说明
Redis 单线程执行命令，Lua 脚本可将多步逻辑组合为原子操作，适合库存扣减、限流计数、分布式锁校验等场景。

## 最佳实践
1. 脚本入参通过 KEYS 和 ARGV 传递，避免硬编码。
2. 脚本保持短小，避免长时间阻塞 Redis。
3. 对脚本执行结果定义明确返回码。
4. 在生产环境中缓存脚本 SHA，减少传输开销。

## 示例代码
```lua
local stock = tonumber(redis.call("GET", KEYS[1]) or "0")
if stock <= 0 then
  return -1
end
redis.call("DECR", KEYS[1])
return 1
```

```java
Long result = redisTemplate.execute(script, List.of("stock:1001"));
```',
'redis', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Redis Lua 脚本与原子操作设计');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Redis Sentinel 与 Cluster 高可用方案对比',
'## 技术说明
Sentinel 主要解决主从故障转移，Cluster 解决数据分片与扩展性。两者关注点不同，应按业务容量与可用性目标选型。

## 最佳实践
1. 中小规模先主从加 Sentinel，快速落地。
2. 数据量增长明显时规划 Cluster 迁移路径。
3. 客户端要具备自动重连与拓扑刷新能力。
4. 定期演练故障切换，验证恢复时间目标。

## 示例代码
```text
Sentinel：监控 master，故障时提升 slave
Cluster：16384 槽位分布，多主多从
```

```yaml
spring:
  data:
    redis:
      sentinel:
        master: mymaster
        nodes: 127.0.0.1:26379,127.0.0.1:26380
```',
'redis', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Redis Sentinel 与 Cluster 高可用方案对比');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'MySQL 慢查询治理闭环与容量规划',
'## 技术说明
慢查询治理不仅是单条 SQL 优化，还包括发现、分析、治理、回归验证和持续监控的闭环。
容量规划要结合 QPS、数据增长率、磁盘 IO 和索引规模综合评估。

## 最佳实践
1. 建立慢查询阈值分级和告警策略。
2. 每次优化记录前后执行计划对比。
3. 对高频 SQL 建立压测基线。
4. 评估峰值流量下的资源余量。

## 示例代码
```sql
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 0.5;
```

```sql
EXPLAIN ANALYZE SELECT * FROM order_info WHERE user_id = 1001 ORDER BY created_at DESC LIMIT 20;
```',
'mysql', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'MySQL 慢查询治理闭环与容量规划');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'MySQL 主从复制延迟排查与优化',
'## 技术说明
主从架构中读写分离可提升吞吐，但复制延迟会导致读到旧数据。延迟来源包括大事务、IO 瓶颈、SQL 回放压力。

## 最佳实践
1. 拆分大事务，降低单次 binlog 回放成本。
2. 监控 Seconds_Behind_Master 和回放队列。
3. 对强一致读请求回源主库或采用半同步策略。
4. 建立延迟阈值告警并自动降级。

## 示例代码
```sql
SHOW SLAVE STATUS\G
```

```text
关注字段：Seconds_Behind_Master、Relay_Log_Space、Last_SQL_Error
```',
'mysql', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'MySQL 主从复制延迟排查与优化');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Vue Pinia 状态管理进阶实践',
'## 技术说明
Pinia 提供轻量、类型友好的状态管理方式，适合 Vue3 组合式 API 场景。通过模块化 store 可以清晰管理复杂页面状态。

## 最佳实践
1. store 按业务域拆分，避免巨型全局 store。
2. 持久化仅保存必要字段，减少本地存储污染。
3. 异步 action 统一错误处理和加载态。
4. 对关键状态变更增加日志埋点。

## 示例代码
```ts
export const useUserStore = defineStore("user", {
  state: () => ({ profile: null as any, loading: false }),
  actions: {
    async loadProfile() {
      this.loading = true;
      try {
        this.profile = await api.getProfile();
      } finally {
        this.loading = false;
      }
    }
  }
});
```',
'vue', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Vue Pinia 状态管理进阶实践');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Vue 组件通信模式全景：Props Emits Provide Inject',
'## 技术说明
Vue 组件通信方式取决于组件层级关系和数据流方向。父子组件常用 Props 与 Emits，跨层级可用 Provide Inject，全局共享可用 Pinia。

## 最佳实践
1. 单向数据流优先，避免子组件直接改父状态。
2. 事件命名清晰表达业务动作。
3. Provide Inject 适合中间层透传配置，不宜滥用存业务状态。
4. 复杂跨页面状态交给状态管理库。

## 示例代码
```vue
<script setup lang="ts">
const props = defineProps<{ value: string }>();
const emit = defineEmits<{ (e: "update:value", v: string): void }>();
</script>

<template>
  <input :value="props.value" @input="emit(''update:value'', ($event.target as HTMLInputElement).value)" />
</template>
```',
'vue', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Vue 组件通信模式全景：Props Emits Provide Inject');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'React 渲染机制与批量更新原理',
'## 技术说明
React 通过 Fiber 架构实现可中断渲染和优先级调度。状态更新会触发 render 阶段与 commit 阶段，批量更新可减少重复渲染。

## 最佳实践
1. 避免在 render 中执行昂贵计算。
2. 合理拆分组件边界，降低更新范围。
3. 使用 memo 控制纯展示组件重复渲染。
4. 通过 Profiler 观察真实渲染耗时。

## 示例代码
```tsx
const ListItem = React.memo(({ name }: { name: string }) => {
  return <li>{name}</li>;
});

function App() {
  const [count, setCount] = useState(0);
  return <button onClick={() => setCount(c => c + 1)}>count:{count}</button>;
}
```',
'react', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'React 渲染机制与批量更新原理');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'React 路由鉴权与页面懒加载实战',
'## 技术说明
路由鉴权用于控制访问权限，懒加载用于降低首屏体积。二者结合可提升安全性与加载性能。

## 最佳实践
1. 路由守卫统一处理未登录与无权限状态。
2. 页面组件按路由维度懒加载。
3. 对关键页面设置骨架屏避免白屏。
4. 鉴权信息与刷新 token 逻辑统一在基础层。

## 示例代码
```tsx
const AdminPage = React.lazy(() => import("./pages/Admin"));

function PrivateRoute({ children }: { children: JSX.Element }) {
  const token = localStorage.getItem("token");
  return token ? children : <Navigate to="/login" replace />;
}
```',
'react', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'React 路由鉴权与页面懒加载实战');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Docker 网络模式与服务发现实践',
'## 技术说明
Docker 网络模式包括 bridge、host、none。微服务部署中通常使用 bridge 网络结合服务名解析实现容器间通信。

## 最佳实践
1. 同一业务栈放同一个自定义网络。
2. 服务间调用优先使用容器服务名。
3. 对外暴露端口最小化，降低攻击面。
4. 结合健康检查和重启策略保障可用性。

## 示例代码
```bash
docker network create app-net
docker run -d --name mysql --network app-net mysql:8
docker run -d --name backend --network app-net app-backend:latest
```

```text
容器内可通过 mysql:3306 访问数据库
```',
'docker', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Docker 网络模式与服务发现实践');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Docker Volume 持久化与备份恢复',
'## 技术说明
容器是临时性的，数据需要通过 Volume 做持久化。数据库、上传文件、日志目录都应挂载持久卷。

## 最佳实践
1. 关键数据目录必须挂载命名卷或宿主机路径。
2. 备份流程自动化并定期演练恢复。
3. 卷命名与环境标识统一，避免误删。
4. 生产环境对备份文件做加密与权限控制。

## 示例代码
```yaml
services:
  mysql:
    image: mysql:8
    volumes:
      - mysql-data:/var/lib/mysql
volumes:
  mysql-data:
```

```bash
docker run --rm -v mysql-data:/data -v $PWD:/backup alpine tar czf /backup/mysql-data.tgz /data
```',
'docker', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Docker Volume 持久化与备份恢复');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT '算法专题：并查集模板与连通性问题',
'## 技术说明
并查集用于维护动态连通性，支持快速合并集合和判断是否同集合，常用于图连通分量、冗余连接、岛屿问题。

## 最佳实践
1. 使用路径压缩和按秩合并优化复杂度。
2. 初始化时注意节点编号范围。
3. 对输入边做合法性校验，避免越界。
4. 将模板抽离复用，减少重复实现错误。

## 示例代码
```java
class DSU {
  int[] p, rank;
  DSU(int n) {
    p = new int[n + 1]; rank = new int[n + 1];
    for (int i = 0; i <= n; i++) p[i] = i;
  }
  int find(int x) {
    if (p[x] != x) p[x] = find(p[x]);
    return p[x];
  }
  void union(int a, int b) {
    int pa = find(a), pb = find(b);
    if (pa == pb) return;
    if (rank[pa] < rank[pb]) p[pa] = pb;
    else if (rank[pa] > rank[pb]) p[pb] = pa;
    else { p[pb] = pa; rank[pa]++; }
  }
}
```',
'algorithm', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = '算法专题：并查集模板与连通性问题');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT '算法专题：最短路径 Dijkstra 与 BFS 对比',
'## 技术说明
无权图最短路通常用 BFS，非负权图最短路用 Dijkstra。选择算法要基于边权特性与图规模。

## 最佳实践
1. 无权图优先 BFS，复杂度更低。
2. Dijkstra 使用优先队列优化到 O((V+E)logV)。
3. 大图场景注意邻接表存储和内存占用。
4. 路径恢复时保存前驱数组。

## 示例代码
```java
PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
int[] dist = new int[n];
Arrays.fill(dist, Integer.MAX_VALUE);
dist[src] = 0;
pq.offer(new int[]{src, 0});
while (!pq.isEmpty()) {
  int[] cur = pq.poll();
  int u = cur[0], d = cur[1];
  if (d != dist[u]) continue;
  for (int[] e : graph[u]) {
    int v = e[0], w = e[1];
    if (dist[v] > d + w) {
      dist[v] = d + w;
      pq.offer(new int[]{v, dist[v]});
    }
  }
}
```',
'algorithm', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = '算法专题：最短路径 Dijkstra 与 BFS 对比');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT '接口安全：防重放、防篡改、防越权',
'## 技术说明
接口安全不仅是鉴权，还包括请求签名、防重放、参数校验和权限边界控制。对关键接口应形成防护组合拳。

## 最佳实践
1. 请求头加入时间戳和随机串，配合签名校验。
2. 关键写接口启用幂等键防重放。
3. 服务端做资源级权限校验，避免越权访问。
4. 敏感字段脱敏并加强审计日志。

## 示例代码
```text
签名串：method + path + timestamp + nonce + bodyHash
Header: X-Timestamp, X-Nonce, X-Sign
```

```java
if (Math.abs(now - timestamp) > 300000) {
  throw new SecurityException("请求已过期");
}
```',
'other', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = '接口安全：防重放、防篡改、防越权');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT '日志规范：结构化日志与 TraceId 贯穿',
'## 技术说明
结构化日志可被检索和聚合分析，TraceId 贯穿可帮助快速关联多服务日志。日志规范是运维与排障效率的关键基础。

## 最佳实践
1. 日志字段统一：traceId、userId、path、cost、status。
2. 错误日志保留上下文但避免敏感数据泄露。
3. 关键业务节点记录业务事件日志。
4. 日志采集、存储、告警形成闭环。

## 示例代码
```java
String traceId = Optional.ofNullable(req.getHeader("X-Trace-Id"))
    .orElse(UUID.randomUUID().toString());
MDC.put("traceId", traceId);
log.info("request path={}, userId={}", req.getRequestURI(), userId);
```',
'other', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = '日志规范：结构化日志与 TraceId 贯穿');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT '代码评审 Checklist：可读性、正确性、可维护性',
'## 技术说明
高质量代码评审应覆盖功能正确性、边界条件、异常处理、性能、安全和可维护性，而不仅是代码风格。

## 最佳实践
1. 先看业务正确性，再看实现细节。
2. 检查是否有隐含副作用和并发问题。
3. 关注错误处理、日志和监控是否完善。
4. 给出具体可执行建议，避免笼统评价。

## 示例代码
```text
评审清单示例：
- 输入边界是否覆盖
- 空值和异常是否处理
- SQL 是否命中索引
- 是否有 XSS 或注入风险
- 单测是否覆盖核心路径
```

```text
建议格式：问题描述 + 风险等级 + 修改建议 + 验证方式
```',
'other', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = '代码评审 Checklist：可读性、正确性、可维护性');

