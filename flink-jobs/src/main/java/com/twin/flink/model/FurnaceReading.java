package com.twin.flink.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FurnaceReading implements Serializable {

    @JsonProperty("furnaceId")      private String  furnaceId;
    @JsonProperty("ingotNo")        private String  ingotNo;
    @JsonProperty("logTime")        private String  logTime;
    @JsonProperty("event")          private Integer event;
    @JsonProperty("operationMode")  private String  operationMode;
    @JsonProperty("diameter")       private Double  diameter;
    @JsonProperty("diameterTarget") private Double  diameterTarget;
    @JsonProperty("heaterTemp")     private Double  heaterTemp;
    @JsonProperty("heaterPowerSv")  private Double  heaterPowerSv;
    @JsonProperty("grMean")         private Double  grMean;
    @JsonProperty("bodyLength")     private Double  bodyLength;
    @JsonProperty("residualWeight") private Double  residualWeight;
    @JsonProperty("receivedAt")     private String  receivedAt;

    public String  getFurnaceId()     { return furnaceId; }
    public String  getIngotNo()       { return ingotNo; }
    public String  getLogTime()       { return logTime; }
    public Integer getEvent()         { return event; }
    public String  getOperationMode() { return operationMode; }
    public Double  getDiameter()      { return diameter; }
    public Double  getDiameterTarget(){ return diameterTarget; }
    public Double  getHeaterTemp()    { return heaterTemp; }
    public Double  getHeaterPowerSv() { return heaterPowerSv; }
    public Double  getGrMean()        { return grMean; }
    public Double  getBodyLength()    { return bodyLength; }
    public Double  getResidualWeight(){ return residualWeight; }
    public String  getReceivedAt()    { return receivedAt; }

    public void setFurnaceId(String v)      { furnaceId = v; }
    public void setIngotNo(String v)        { ingotNo = v; }
    public void setLogTime(String v)        { logTime = v; }
    public void setEvent(Integer v)         { event = v; }
    public void setOperationMode(String v)  { operationMode = v; }
    public void setDiameter(Double v)       { diameter = v; }
    public void setDiameterTarget(Double v) { diameterTarget = v; }
    public void setHeaterTemp(Double v)     { heaterTemp = v; }
    public void setHeaterPowerSv(Double v)  { heaterPowerSv = v; }
    public void setGrMean(Double v)         { grMean = v; }
    public void setBodyLength(Double v)     { bodyLength = v; }
    public void setResidualWeight(Double v) { residualWeight = v; }
    public void setReceivedAt(String v)     { receivedAt = v; }

    public boolean isNg() { return event != null && event == 6; }
}
