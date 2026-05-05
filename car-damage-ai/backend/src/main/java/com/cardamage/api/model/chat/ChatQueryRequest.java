package com.cardamage.api.model.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChatQueryRequest {
    private String prompt;
    @JsonProperty("time_range_days")
    private Integer timeRangeDays;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Integer getTimeRangeDays() {
        return timeRangeDays;
    }

    public void setTimeRangeDays(Integer timeRangeDays) {
        this.timeRangeDays = timeRangeDays;
    }
}
