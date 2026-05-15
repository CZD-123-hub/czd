package com.coding.assistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VideoWatchRequest {

    @NotNull
    @Min(1)
    @Max(86400)
    private Integer watchedSeconds;
}
