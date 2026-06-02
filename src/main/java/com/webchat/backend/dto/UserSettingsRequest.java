package com.webchat.backend.dto;

import lombok.Data;

@Data
public class UserSettingsRequest {
    private Float temperature;
    private Integer maxTokens;
}
