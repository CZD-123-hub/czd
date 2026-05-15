SET NAMES utf8mb4;
INSERT INTO knowledge_document (title, content, category, embedding, created_at)
SELECT '系统设计：缓存与数据库一致性策略全景',
'## 技术说明
缓存与数据库一致性是高并发系统中的核心问题，常见模式包括 Cache Aside、Write Through、Write Back。
不同模式在一致性、性能和实现复杂度上各有取舍，需按业务等级选择。

## 最佳实践
1. 读多写少场景优先 Cache Aside，工程实现简单。
2. 对强一致接口采用读主库或双删策略。
3. 更新链路增加重试与补偿，降低偶发不一致概率。
4. 建立一致性监控指标，例如脏读率和回源率。

## 示例代码
```java
public void updateProduct(Long id, Product dto) {
    productMapper.update(dto);
    redisTemplate.delete("product:" + id);
    // 延迟双删（可选）
    scheduler.schedule(() -> redisTemplate.delete("product:" + id), 500, TimeUnit.MILLISECONDS);
}
```

```text
策略选择建议：
- 业务允许短暂不一致：Cache Aside
- 对一致性要求高：读写链路加补偿和读主兜底
```
',
'other', NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM knowledge_document WHERE title = '系统设计：缓存与数据库一致性策略全景');
