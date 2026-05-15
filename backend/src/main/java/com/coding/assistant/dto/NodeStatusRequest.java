package com.coding.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeStatusRequest {

    @NotBlank(message = "status is required")
    @Pattern(
            regexp = "^(todo|doing|done|skipped)$",
            message = "status must be one of: todo, doing, done, skipped"
    )
    private String status;
}
