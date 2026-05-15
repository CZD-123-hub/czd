package com.coding.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelatedGraphNodeVO {

    private String id;
    private String name;
    private String category;
    private String difficulty;
    private Double score;
}
