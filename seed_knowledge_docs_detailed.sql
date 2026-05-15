SET NAMES utf8mb4;

UPDATE knowledge_document
SET content = '## 技术说明
Java 集合框架的核心是接口与实现分离，不同实现适用于不同访问模式。
ArrayList 适合随机读取，LinkedList 适合中间插入删除，HashMap 适合高频键值查询，TreeMap 适合有序和范围查询，并发场景需要优先考虑 ConcurrentHashMap。

## 最佳实践
1. 先评估读写比例，再选集合类型，不要凭习惯。
2. 对外暴露接口类型，例如 List 或 Map，降低实现耦合。
3. 大对象集合注意容量预估，避免频繁扩容。
4. 多线程共享集合优先使用并发容器，不要手写复杂锁逻辑。
5. 对只读配置优先使用不可变集合，减少误修改风险。

## 示例代码
```java
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CollectionPractice {
    private static final Map<String, Integer> COUNTER = new ConcurrentHashMap<>();

    public static void increase(String key) {
        COUNTER.merge(key, 1, Integer::sum);
    }

    public static List<String> topKeys() {
        List<String> keys = new ArrayList<>(COUNTER.keySet());
        keys.sort(String::compareTo);
        return keys;
    }
}
```'
WHERE id = 1;

UPDATE knowledge_document
SET content = '## 技术说明
CompletableFuture 用于表达异步任务编排，支持串联、并联、汇总、超时和异常恢复。
thenApply 适合结果转换，thenCompose 适合继续异步调用，allOf 适合并发等待多个任务，exceptionally 和 handle 用于兜底处理。

## 最佳实践
1. 阻塞 IO 任务使用独立线程池，避免占用公共线程池。
2. 外部依赖调用要设置超时和降级逻辑。
3. 链式调用中明确异常处理点，避免异常被吞掉。
4. 汇总多个任务时保留上下文信息，便于排查失败原因。

## 示例代码
```java
import java.util.concurrent.*;

public class AsyncFlow {
    private static final ExecutorService IO_POOL = Executors.newFixedThreadPool(8);

    public static String query() {
        CompletableFuture<String> userFuture = CompletableFuture.supplyAsync(() -> "user:1001", IO_POOL);
        CompletableFuture<String> orderFuture = CompletableFuture.supplyAsync(() -> "order:9001", IO_POOL);

        return userFuture.thenCombine(orderFuture, (u, o) -> u + "|" + o)
                .orTimeout(2, TimeUnit.SECONDS)
                .exceptionally(ex -> "fallback")
                .join();
    }
}
```'
WHERE id = 2;

UPDATE knowledge_document
SET content = '## 技术说明
Spring 声明式事务通过 AOP 代理统一管理提交与回滚，适合大多数业务场景。
事务边界一般放在 Service 层，确保一个业务动作内多个数据库操作具备原子性。

## 最佳实践
1. 在 Service 公共方法上使用 Transactional，保持边界清晰。
2. 明确隔离级别和传播行为，避免默认策略引发隐性问题。
3. 长事务要拆分，减少锁持有时间和超时风险。
4. 避免事务方法中进行耗时远程调用。
5. 对业务异常设置回滚规则，防止部分成功。

## 示例代码
```java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderAppService {

    @Transactional(rollbackFor = Exception.class)
    public void submitOrder() {
        createOrder();
        lockInventory();
        writeOperateLog();
    }

    private void createOrder() {}
    private void lockInventory() {}
    private void writeOperateLog() {}
}
```'
WHERE id = 3;

UPDATE knowledge_document
SET content = '## 技术说明
统一错误响应能显著降低前后端联调成本，推荐固定响应结构。
常见字段包括 code、message、traceId、timestamp、path，既方便用户理解，也方便排查问题。

## 最佳实践
1. 业务异常与系统异常分层处理。
2. 错误信息对用户友好，对日志保留技术细节。
3. 接口返回统一 JSON 结构，不要混用多种格式。
4. 结合日志链路 traceId 做全链路定位。

## 示例代码
```java
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Map<String, Object> handleBiz(IllegalArgumentException ex) {
        return Map.of(
                "code", 400,
                "message", ex.getMessage(),
                "traceId", "demo-trace-001",
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
```'
WHERE id = 4;

UPDATE knowledge_document
SET content = '## 技术说明
缓存一致性核心是数据库与缓存数据在更新时的时序控制。
典型模式是 Cache Aside，即先更新数据库，再删除缓存，让下次读取触发回源重建。

## 最佳实践
1. 热点数据配置合理 TTL，防止缓存雪崩。
2. 缓存击穿使用互斥锁或逻辑过期策略。
3. 缓存穿透使用空值缓存或布隆过滤器。
4. 删除缓存失败要有重试和补偿机制。
5. 对核心数据增加监控指标，关注命中率和回源率。

## 示例代码
```java
public class UserCacheService {
    public void updateUserName(Long userId, String name) {
        updateUserInDb(userId, name);
        deleteCache("user:" + userId);
    }

    private void updateUserInDb(Long userId, String name) {}
    private void deleteCache(String key) {}
}
```'
WHERE id = 5;

UPDATE knowledge_document
SET content = '## 技术说明
MySQL 调优需要基于执行计划和真实耗时，EXPLAIN 用于查看计划，EXPLAIN ANALYZE 用于查看实际执行代价。
重点关注扫描行数、访问类型、是否命中索引、排序和回表成本。

## 最佳实践
1. 先定位慢 SQL，再分析计划，不要盲目加索引。
2. 联合索引遵循最左前缀原则。
3. 避免在索引列上做函数或隐式类型转换。
4. 分页深翻建议用游标分页替代大 OFFSET。
5. 优化后对比执行耗时和扫描行数，形成基准记录。

## 示例代码
```sql
EXPLAIN ANALYZE
SELECT id, title, created_at
FROM article
WHERE status = 1
ORDER BY created_at DESC
LIMIT 20;
```

```sql
CREATE INDEX idx_article_status_time ON article(status, created_at);
```'
WHERE id = 6;

UPDATE knowledge_document
SET content = '## 技术说明
Vue 3 性能优化主要分为首屏加载优化和更新性能优化。
首屏优化关注打包体积和资源加载，更新优化关注响应式依赖、组件拆分和渲染次数。

## 最佳实践
1. 路由级和组件级按需加载。
2. 大列表使用虚拟滚动，避免一次渲染过多节点。
3. 避免在模板中写复杂表达式。
4. 频繁输入场景使用防抖。
5. 通过性能面板定位瓶颈后再优化。

## 示例代码
```ts
import { ref } from "vue";

function debounce<T extends (...args: any[]) => void>(fn: T, delay = 300) {
  let timer: number | undefined;
  return (...args: Parameters<T>) => {
    clearTimeout(timer);
    timer = window.setTimeout(() => fn(...args), delay);
  };
}

const keyword = ref("");
const onSearch = debounce((value: string) => {
  console.log("query", value);
}, 300);
```'
WHERE id = 7;

UPDATE knowledge_document
SET content = '## 技术说明
React 性能优化的关键是减少不必要渲染，常用工具包括 memo、useMemo、useCallback 和状态分层。
优化前应先做性能分析，确认瓶颈位置，再做针对性优化。

## 最佳实践
1. 保持组件纯函数化，避免副作用污染渲染。
2. 频繁变化状态尽量就近管理，减少父组件连带刷新。
3. 大列表搭配虚拟列表。
4. useMemo 和 useCallback 只在高频路径使用。
5. 性能优化后复测，验证收益是否真实。

## 示例代码
```tsx
import React, { useMemo, useCallback, useState } from "react";

export default function Demo() {
  const [keyword, setKeyword] = useState("");
  const list = ["java", "spring", "redis", "mysql"];

  const filtered = useMemo(() => list.filter(item => item.includes(keyword)), [list, keyword]);
  const onChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setKeyword(e.target.value);
  }, []);

  return <input value={keyword} onChange={onChange} placeholder={filtered.join(",")} />;
}
```'
WHERE id = 8;

UPDATE knowledge_document
SET content = '## 技术说明
Docker 多阶段构建将构建环境与运行环境分离，可显著降低镜像体积并提升安全性。
对于 Java 服务，通常在构建阶段使用 Maven 镜像打包，在运行阶段只保留 JRE 与产物。

## 最佳实践
1. 将依赖下载与源码复制分层，提升构建缓存命中率。
2. 运行镜像只保留必要文件，减少攻击面。
3. 配置健康检查、时区和 JVM 参数。
4. 使用 dockerignore 减少上下文体积。
5. 镜像版本固定，避免环境漂移。

## 示例代码
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```'
WHERE id = 9;

UPDATE knowledge_document
SET content = '## 技术说明
动态规划通过保存子问题结果来避免重复计算，适合最优子结构和重叠子问题场景。
常见题型包括路径计数、背包、区间 DP、状态压缩 DP。

## 最佳实践
1. 先定义状态含义，再写转移方程。
2. 明确初始化和边界条件。
3. 优先写出可读版本，再考虑空间优化。
4. 对转移过程进行注释，便于调试和复盘。
5. 复杂题先画状态图，减少推导错误。

## 示例代码
```java
public class ClimbStairs {
    public int climbStairs(int n) {
        if (n <= 2) return n;
        int a = 1;
        int b = 2;
        for (int i = 3; i <= n; i++) {
            int c = a + b;
            a = b;
            b = c;
        }
        return b;
    }
}
```'
WHERE id = 10;

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Java JVM 内存模型与 GC 调优实战',
'## 技术说明
JVM 运行时内存区域包括堆、方法区、虚拟机栈、本地方法栈和程序计数器。
GC 调优目标是降低停顿时间与提升吞吐，常见收集器有 G1、ZGC、Parallel GC。

## 最佳实践
1. 先通过 GC 日志判断问题类型，再调参数。
2. 优先保证稳定，再追求极限性能。
3. 在压测环境验证参数，不直接在生产试错。
4. 关注对象分配速率和晋升失败。

## 示例代码
```bash
java -Xms2g -Xmx2g -XX:+UseG1GC -Xlog:gc*:file=gc.log:time app.jar
```

```text
观察指标：Young GC 次数、Full GC 次数、最大停顿时间、平均停顿时间
```',
'java',
NULL,
NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Java JVM 内存模型与 GC 调优实战');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Spring Boot 接口幂等设计与防重复提交',
'## 技术说明
接口幂等指同一请求多次执行后结果一致，常用于支付、下单、回调等关键链路。
可通过业务唯一键、幂等令牌、状态机控制来实现。

## 最佳实践
1. 对关键写接口强制幂等校验。
2. 幂等键设计要包含业务主键与动作类型。
3. 幂等记录设置合理过期时间。
4. 对重复请求返回明确提示，避免用户误判。

## 示例代码
```java
public boolean checkAndSetIdempotent(String key) {
    Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(10));
    return Boolean.TRUE.equals(ok);
}

public void createOrder(String requestNo) {
    if (!checkAndSetIdempotent("idem:order:" + requestNo)) {
        throw new IllegalStateException("重复请求");
    }
    // 正常下单逻辑
}
```',
'spring',
NULL,
NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Spring Boot 接口幂等设计与防重复提交');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Redis 分布式锁与 Redisson 实践',
'## 技术说明
分布式锁用于控制分布式环境下共享资源并发访问，核心要求是互斥、可重入、自动过期、可恢复。
Redis 常见实现是 set nx px，也可使用 Redisson 提供高级封装。

## 最佳实践
1. 锁持有时间要小于业务超时，避免死锁。
2. 解锁时校验锁拥有者，防止误删他人锁。
3. 对高并发热点资源增加限流和重试退避。
4. 对关键链路优先使用成熟组件库。

## 示例代码
```java
RLock lock = redissonClient.getLock("lock:order:1001");
boolean locked = lock.tryLock(1, 10, TimeUnit.SECONDS);
if (locked) {
    try {
        // 业务逻辑
    } finally {
        lock.unlock();
    }
}
```',
'redis',
NULL,
NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Redis 分布式锁与 Redisson 实践');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'MySQL 事务隔离与锁排查指南',
'## 技术说明
事务隔离级别决定并发可见性，MySQL InnoDB 支持读未提交、读已提交、可重复读、串行化。
锁问题常表现为等待超时、死锁和吞吐下降，需要结合锁监控排查。

## 最佳实践
1. 默认可重复读场景下重点关注间隙锁影响。
2. 更新顺序保持一致，降低死锁概率。
3. 长事务尽量拆分，避免锁长期占用。
4. 建立慢 SQL 与锁等待联动监控。

## 示例代码
```sql
SHOW ENGINE INNODB STATUS;
```

```sql
SELECT * FROM performance_schema.data_locks;
```

```text
排查步骤：定位阻塞会话 -> 定位持锁 SQL -> 优化事务边界与索引
```',
'mysql',
NULL,
NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'MySQL 事务隔离与锁排查指南');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Vue 3 组合式 API 项目组织规范',
'## 技术说明
组合式 API 强调逻辑复用与职责拆分，适合中大型项目。
推荐按业务域划分组件、页面、store 和 composable，提升可维护性。

## 最佳实践
1. composable 命名以 use 开头，单一职责。
2. 页面组件只做编排，复杂逻辑下沉到 composable。
3. 统一请求层和错误处理，避免重复代码。
4. 组件 Props 和 Emits 使用类型约束。

## 示例代码
```ts
import { ref } from "vue";

export function usePager() {
  const page = ref(1);
  const pageSize = ref(20);
  const total = ref(0);

  function reset() {
    page.value = 1;
    total.value = 0;
  }

  return { page, pageSize, total, reset };
}
```',
'vue',
NULL,
NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Vue 3 组合式 API 项目组织规范');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'React 状态管理选型实战',
'## 技术说明
React 状态管理要根据状态作用域和更新频率选型。
局部状态可用 useState，跨层级共享可用 Context，复杂全局状态可使用 Redux Toolkit 或 Zustand。

## 最佳实践
1. 优先局部状态，避免全局状态膨胀。
2. 服务端状态建议用专门缓存工具管理。
3. 全局状态结构扁平化，降低更新成本。
4. 将 UI 状态和业务状态分离。

## 示例代码
```tsx
import { create } from "zustand";

interface CounterStore {
  count: number;
  inc: () => void;
}

export const useCounterStore = create<CounterStore>((set) => ({
  count: 0,
  inc: () => set((s) => ({ count: s.count + 1 })),
}));
```',
'react',
NULL,
NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'React 状态管理选型实战');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Docker Compose 与 Nginx 部署前后端项目',
'## 技术说明
典型部署方案是 Nginx 承载前端静态资源并反向代理后端 API，后端服务与数据库通过 Compose 编排。
通过统一网络和环境变量管理，减少本地与生产环境差异。

## 最佳实践
1. 前后端和数据库放在同一 Compose 网络。
2. 配置健康检查和自动重启策略。
3. 密钥参数通过环境变量注入，不写死到镜像。
4. Nginx 配置静态缓存和 API 代理超时。

## 示例代码
```yaml
services:
  web:
    image: nginx:stable
    ports:
      - "80:80"
  api:
    image: app-backend:latest
  mysql:
    image: mysql:8
```

```nginx
location /api/ {
    proxy_pass http://api:8080;
}
```',
'docker',
NULL,
NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Docker Compose 与 Nginx 部署前后端项目');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT '常见算法复杂度速查与选型策略',
'## 技术说明
算法复杂度用于衡量在输入规模增长时的时间与空间成本。
常见时间复杂度从低到高包括 O(1)、O(log n)、O(n)、O(n log n)、O(n^2)。

## 最佳实践
1. 先看数据规模，再决定算法复杂度上限。
2. 查询多更新少优先预处理，更新多查询少优先在线算法。
3. 竞赛与面试场景优先保证正确性，再优化复杂度。
4. 复杂度分析要同时考虑最坏情况与平均情况。

## 示例代码
```text
二分查找：O(log n)
快速排序平均：O(n log n)
哈希查找平均：O(1)
双重循环遍历：O(n^2)
```

```java
public int binarySearch(int[] nums, int target) {
    int l = 0;
    int r = nums.length - 1;
    while (l <= r) {
        int m = l + (r - l) / 2;
        if (nums[m] == target) return m;
        if (nums[m] < target) l = m + 1;
        else r = m - 1;
    }
    return -1;
}
```',
'algorithm',
NULL,
NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = '常见算法复杂度速查与选型策略');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT 'Git 分支策略与提交规范',
'## 技术说明
规范的 Git 流程能降低协作冲突并提升可追溯性。
常见策略是 main 保持稳定，feature 分支开发，release 分支发布，hotfix 分支紧急修复。

## 最佳实践
1. 提交信息遵循统一格式，例如 feat fix docs refactor。
2. 小步提交，单次提交只做一类变更。
3. 合并前进行代码评审与自动化检查。
4. 禁止直接在主分支开发。

## 示例代码
```bash
git checkout -b feature/doc-detail-ui
git add .
git commit -m "feat: 优化知识文档详情展示"
git push origin feature/doc-detail-ui
```

```text
推荐提交模板：type(scope): summary
```',
'other',
NULL,
NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = 'Git 分支策略与提交规范');

INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT '微服务可观测性入门：日志、指标、链路',
'## 技术说明
可观测性由日志、指标、链路三部分构成。
日志用于还原事件，指标用于看趋势，链路用于定位跨服务耗时瓶颈。

## 最佳实践
1. 统一日志字段，至少包含 traceId、service、endpoint、status。
2. 为关键接口建立延迟和错误率指标。
3. 全链路透传 traceId，避免链路断点。
4. 告警策略分级，避免告警风暴。

## 示例代码
```text
日志字段：timestamp level traceId service endpoint cost status
```

```java
String traceId = request.getHeader("X-Trace-Id");
MDC.put("traceId", traceId == null ? UUID.randomUUID().toString() : traceId);
```

```text
核心指标：QPS、P95 延迟、错误率、下游超时率
```',
'other',
NULL,
NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = '微服务可观测性入门：日志、指标、链路');
