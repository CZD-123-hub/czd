package com.coding.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVO {

    private String level;
    private String primaryGoal;
    private List<String> weakPoints;
    private List<String> preferredLanguages;
    private String learningStyle;
    private String nextTaskHint;
    private LocalDateTime updatedAt;
}
