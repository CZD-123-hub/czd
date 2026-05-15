package com.coding.assistant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequest {

    @NotNull(message = "messageId is required")
    private Long messageId;

    @Pattern(regexp = "^(useful|useless)$", message = "rating must be useful or useless")
    private String rating;

    private String comment;
}
