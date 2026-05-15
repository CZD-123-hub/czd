package com.coding.assistant.service;

import com.coding.assistant.entity.LearningNode;
import com.coding.assistant.entity.LearningPath;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.mapper.LearningNodeMapper;
import com.coding.assistant.mapper.LearningPathMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PathServiceTest {

    @Mock
    private LearningPathMapper learningPathMapper;

    @Mock
    private LearningNodeMapper learningNodeMapper;

    @Mock
    private Driver neo4jDriver;

    @Mock
    private ChatService chatService;

    @InjectMocks
    private PathService pathService;

    @Test
    void updateNodeStatus_shouldUpdateNodeAndPathWhenAuthorized() {
        Long userId = 1L;
        Long nodeId = 10L;
        Long pathId = 20L;

        LearningNode node = LearningNode.builder()
                .id(nodeId)
                .pathId(pathId)
                .status("todo")
                .build();
        LearningPath path = LearningPath.builder()
                .id(pathId)
                .userId(userId)
                .target("Spring Boot")
                .status("active")
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(learningNodeMapper.selectById(nodeId)).thenReturn(node);
        when(learningPathMapper.selectById(pathId)).thenReturn(path);

        pathService.updateNodeStatus(userId, nodeId, "done");

        ArgumentCaptor<LearningNode> nodeCaptor = ArgumentCaptor.forClass(LearningNode.class);
        verify(learningNodeMapper).updateById(nodeCaptor.capture());
        assertEquals("done", nodeCaptor.getValue().getStatus());

        ArgumentCaptor<LearningPath> pathCaptor = ArgumentCaptor.forClass(LearningPath.class);
        verify(learningPathMapper).updateById(pathCaptor.capture());
        assertNotNull(pathCaptor.getValue().getUpdatedAt());
    }

    @Test
    void updateNodeStatus_shouldThrow404WhenNodeNotFound() {
        when(learningNodeMapper.selectById(100L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pathService.updateNodeStatus(1L, 100L, "doing")
        );

        assertEquals(404, exception.getCode());
        verify(learningPathMapper, never()).updateById(any());
    }

    @Test
    void updateNodeStatus_shouldThrow403WhenPathNotOwnedByUser() {
        Long nodeId = 10L;
        Long pathId = 20L;

        LearningNode node = LearningNode.builder()
                .id(nodeId)
                .pathId(pathId)
                .status("todo")
                .build();
        LearningPath path = LearningPath.builder()
                .id(pathId)
                .userId(999L)
                .build();

        when(learningNodeMapper.selectById(nodeId)).thenReturn(node);
        when(learningPathMapper.selectById(pathId)).thenReturn(path);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pathService.updateNodeStatus(1L, nodeId, "done")
        );

        assertEquals(403, exception.getCode());
        verify(learningNodeMapper, never()).updateById(any());
        verify(learningPathMapper, never()).updateById(any());
    }
}
