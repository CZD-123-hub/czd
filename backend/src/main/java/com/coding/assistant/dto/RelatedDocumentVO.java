package com.coding.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelatedDocumentVO {

    private Long id;
    private String title;
    private String category;
    private LocalDateTime createdAt;
    private Double score;
    private String reason;
}
