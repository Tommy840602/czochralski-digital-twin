package com.twin.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FurnaceMessage {
    @JsonProperty("furnaceId")      private String furnaceId;
    @JsonProperty("ingotNo")        private String ingotNo;
    @JsonProperty("logTime")        private String logTime;
    @JsonProperty("event")          private Integer event;
    @JsonProperty("operationMode")  private String operationMode;
    @JsonProperty("diameter")       private Double diameter;
    @JsonProperty("diameterTarget") private Double diameterTarget;
    @JsonProperty("heaterTemp")     private Double heaterTemp;
    @JsonProperty("heaterPowerSv")  private Double heaterPowerSv;
    @JsonProperty("grMean")         private Double grMean;
    @JsonProperty("bodyLength")     private Double bodyLength;
    @JsonProperty("residualWeight") private Double residualWeight;
    @JsonProperty("receivedAt")     private String receivedAt;

    public boolean isNg() {
        return event != null && event == 6;
    }
}
