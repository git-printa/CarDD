package com.cardamage.api.model.training;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TrainingStartRequest {

    @JsonProperty("data_yaml")
    private String dataYaml;

    public String getDataYaml() {
        return dataYaml;
    }

    public void setDataYaml(String dataYaml) {
        this.dataYaml = dataYaml;
    }
}
