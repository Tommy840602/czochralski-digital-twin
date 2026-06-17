package com.twin.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FurnaceMessage — MQTT payload 解析模型（彈性多爐版）
 * 不依賴 Lombok，手動宣告 getter，避免 annotation processor 問題。
 * furnaceId 對應 CSV PULLER 欄位（D1 / D3 / DB / F7 / FA / …）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FurnaceMessage {

    @JsonProperty("furnaceId")          private String  furnaceId;
    @JsonProperty("ingotNo")            private String  ingotNo;
    @JsonProperty("logTime")            private String  logTime;
    @JsonProperty("receivedAt")         private String  receivedAt;
    @JsonProperty("event")              private Integer event;        // 相容舊格式
    @JsonProperty("operationMode")      private String  operationMode;
    @JsonProperty("sop")                private String  sop;
    @JsonProperty("diameter")           private Double  diameter;
    @JsonProperty("dMean")              private Double  dMean;
    @JsonProperty("diameterTarget")     private Double  diameterTarget;
    @JsonProperty("heaterTemp")         private Double  heaterTemp;
    @JsonProperty("heaterTempTarget")   private Double  heaterTempTarget;
    @JsonProperty("heaterPowerSv")      private Double  heaterPowerSv;
    @JsonProperty("htMean")             private Double  htMean;
    @JsonProperty("grMean")             private Double  grMean;
    @JsonProperty("bodyLength")         private Double  bodyLength;
    @JsonProperty("seedLift")           private Double  seedLift;
    @JsonProperty("residualWeight")     private Double  residualWeight;

    // ── Getters ──────────────────────────────────────────────
    public String  getFurnaceId()       { return furnaceId; }
    public String  getIngotNo()         { return ingotNo; }
    public String  getLogTime()         { return logTime; }
    public String  getReceivedAt()      { return receivedAt; }
    public Integer getEvent()           { return event; }
    public String  getOperationMode()   { return operationMode; }
    public String  getSop()             { return sop; }
    public Double  getDiameter()        { return diameter; }
    public Double  getDMean()           { return dMean; }
    public Double  getDiameterTarget()  { return diameterTarget; }
    public Double  getHeaterTemp()      { return heaterTemp; }
    public Double  getHeaterTempTarget(){ return heaterTempTarget; }
    public Double  getHeaterPowerSv()   { return heaterPowerSv; }
    public Double  getHtMean()          { return htMean; }
    public Double  getGrMean()          { return grMean; }
    public Double  getBodyLength()      { return bodyLength; }
    public Double  getSeedLift()        { return seedLift; }
    public Double  getResidualWeight()  { return residualWeight; }

    // ── Setters ──────────────────────────────────────────────
    public void setFurnaceId(String v)        { furnaceId = v; }
    public void setIngotNo(String v)          { ingotNo = v; }
    public void setLogTime(String v)          { logTime = v; }
    public void setReceivedAt(String v)       { receivedAt = v; }
    public void setEvent(Integer v)           { event = v; }
    public void setOperationMode(String v)    { operationMode = v; }
    public void setSop(String v)              { sop = v; }
    public void setDiameter(Double v)         { diameter = v; }
    public void setDMean(Double v)            { dMean = v; }
    public void setDiameterTarget(Double v)   { diameterTarget = v; }
    public void setHeaterTemp(Double v)       { heaterTemp = v; }
    public void setHeaterTempTarget(Double v) { heaterTempTarget = v; }
    public void setHeaterPowerSv(Double v)    { heaterPowerSv = v; }
    public void setHtMean(Double v)           { htMean = v; }
    public void setGrMean(Double v)           { grMean = v; }
    public void setBodyLength(Double v)       { bodyLength = v; }
    public void setSeedLift(Double v)         { seedLift = v; }
    public void setResidualWeight(Double v)   { residualWeight = v; }
}