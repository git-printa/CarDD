package com.cardamage.api.model.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class ChatQueryResponse {
    private String answer;
    @JsonProperty("query_type")
    private String queryType;
    @JsonProperty("skill_used")
    private String skillUsed;
    @JsonProperty("time_range_days")
    private int timeRangeDays;
    private List<String> sources;
    private Map<String, Object> data;

    public ChatQueryResponse(
        String answer,
        String queryType,
        String skillUsed,
        int timeRangeDays,
        List<String> sources,
        Map<String, Object> data
    ) {
        this.answer = answer;
        this.queryType = queryType;
        this.skillUsed = skillUsed;
        this.timeRangeDays = timeRangeDays;
        this.sources = sources;
        this.data = data;
    }

    public String getAnswer() {
        return answer;
    }

    public String getQueryType() {
        return queryType;
    }

    public String getSkillUsed() {
        return skillUsed;
    }

    public int getTimeRangeDays() {
        return timeRangeDays;
    }

    public List<String> getSources() {
        return sources;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
