package com.coding.assistant.service;

import com.coding.assistant.mapper.CodeSnippetMapper;
import com.coding.assistant.mapper.ConversationMapper;
import com.coding.assistant.mapper.LearningNodeMapper;
import com.coding.assistant.mapper.LearningPathMapper;
import com.coding.assistant.mapper.LearningRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    @Mock
    private LearningRecordMapper learningRecordMapper;

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private CodeSnippetMapper codeSnippetMapper;

    @Mock
    private LearningPathMapper learningPathMapper;

    @Mock
    private LearningNodeMapper learningNodeMapper;

    @InjectMocks
    private ProgressService progressService;

    @Test
    void generateReport_shouldReturnPdfBytes_whenDataIsAvailable() {
        Long userId = 1L;

        when(learningRecordMapper.countDistinctDaysByUserId(userId)).thenReturn(0);
        when(conversationMapper.selectCount(any())).thenReturn(0L);
        when(codeSnippetMapper.selectCount(any())).thenReturn(0L);
        when(learningRecordMapper.countByUserIdGroupByActionType(userId)).thenReturn(List.of());
        when(learningRecordMapper.countByUserIdGroupByDate(eq(userId), any(), any())).thenReturn(List.of());
        when(learningRecordMapper.selectByUserIdAndDateRange(eq(userId), any(), any())).thenReturn(List.of());
        when(learningPathMapper.selectList(any())).thenReturn(List.of());
        when(learningRecordMapper.selectDistinctTargetIdsByUserIdAndActionType(eq(userId), anyString())).thenReturn(List.of());

        byte[] report = progressService.generateReport(userId);

        assertNotNull(report);
        assertTrue(report.length > 4);
        assertEquals("%PDF", new String(report, 0, 4, StandardCharsets.US_ASCII));
    }
}
