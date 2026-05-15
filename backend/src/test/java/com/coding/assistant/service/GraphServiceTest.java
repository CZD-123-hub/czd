package com.coding.assistant.service;

import com.coding.assistant.dto.KnowledgeNodeVO;
import com.coding.assistant.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.Node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphServiceTest {

    @Mock
    private Driver neo4jDriver;

    @Mock
    private Session session;

    @Mock
    private Result result;

    @Mock
    private Record record;

    @Mock
    private Value nodeValue;

    @Mock
    private Node node;

    @InjectMocks
    private GraphService graphService;

    @Test
    void getNodeDetail_shouldReturnNodeWhenFound() {
        when(neo4jDriver.session()).thenReturn(session);
        when(session.run(anyString(), any(Value.class))).thenReturn(result);
        when(result.hasNext()).thenReturn(true, false);
        when(result.next()).thenReturn(record);
        when(record.get("n")).thenReturn(nodeValue);
        when(nodeValue.asNode()).thenReturn(node);

        when(node.containsKey("keywords")).thenReturn(false);
        when(node.containsKey("id")).thenReturn(true);
        when(node.containsKey("name")).thenReturn(true);
        when(node.containsKey("category")).thenReturn(true);
        when(node.containsKey("description")).thenReturn(true);
        when(node.containsKey("difficulty")).thenReturn(true);

        when(node.get("id")).thenReturn(Values.value("node-x"));
        when(node.get("name")).thenReturn(Values.value("Node X"));
        when(node.get("category")).thenReturn(Values.value("concept"));
        when(node.get("description")).thenReturn(Values.value("中文描述"));
        when(node.get("difficulty")).thenReturn(Values.value("medium"));

        KnowledgeNodeVO detail = graphService.getNodeDetail("node-x");

        assertEquals("node-x", detail.getId());
        assertEquals("Node X", detail.getName());
        assertEquals("concept", detail.getCategory());
        assertEquals("中文描述", detail.getDescription());
        assertEquals("medium", detail.getDifficulty());
        assertTrue(detail.getKeywords().isEmpty());
    }

    @Test
    void getNodeDetail_shouldThrow404WhenNodeNotFound() {
        when(neo4jDriver.session()).thenReturn(session);
        when(session.run(anyString(), any(Value.class))).thenReturn(result);
        when(result.hasNext()).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> graphService.getNodeDetail("missing"));

        assertEquals(404, exception.getCode());
    }

    @Test
    void getNodeDetail_shouldThrow500WhenNeo4jFails() {
        when(neo4jDriver.session()).thenReturn(session);
        when(session.run(anyString(), any(Value.class))).thenThrow(new RuntimeException("neo4j error"));

        BusinessException exception = assertThrows(BusinessException.class, () -> graphService.getNodeDetail("java"));

        assertEquals(500, exception.getCode());
    }
}
