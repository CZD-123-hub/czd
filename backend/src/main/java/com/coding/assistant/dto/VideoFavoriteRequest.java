package com.coding.assistant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VideoFavoriteRequest {

    @NotNull
    private Boolean favorite;
}
