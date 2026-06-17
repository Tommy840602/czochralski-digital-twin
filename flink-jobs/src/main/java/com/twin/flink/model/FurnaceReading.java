package com.twin.flink.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/**
 * FurnaceReading — Flink 串流數據模型（彈性多爐版）
 * 對應 CSV 全部 44 個感測器欄位 + PULLER → furnaceId
 * event 欄位保留相容舊格式（C1/C2 時代）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FurnaceReading implements Serializable {

    // ── 識別 ────────────────────────────────────────────────────────────
    @JsonProperty("furnaceId")          private String  furnaceId;     // PULLER 值
    @JsonProperty("ingotNo")            private String  ingotNo;
    @JsonProperty("logTime")            private String  logTime;
    @JsonProperty("receivedAt")         private String  receivedAt;

    // 相容舊 C1/C2 event 欄位（新格式不使用，但 CEP 判斷保留）
    @JsonProperty("event")              private Integer event;

    // ── 作業資訊 ────────────────────────────────────────────────────────
    @JsonProperty("operationMode")      private String  operationMode;
    @JsonProperty("sop")                private String  sop;

    // ── 直徑 ────────────────────────────────────────────────────────────
    @JsonProperty("diameter")           private Double  diameter;
    @JsonProperty("dMean")              private Double  dMean;
    @JsonProperty("diameterTarget")     private Double  diameterTarget;

    // ── 溫度 ────────────────────────────────────────────────────────────
    @JsonProperty("heaterTemp")         private Double  heaterTemp;
    @JsonProperty("heaterTempTarget")   private Double  heaterTempTarget;
    @JsonProperty("heaterPowerSv")      private Double  heaterPowerSv;
    @JsonProperty("htMean")             private Double  htMean;
    @JsonProperty("temp2")              private Double  temp2;
    @JsonProperty("temp4")              private Double  temp4;
    @JsonProperty("temp5")              private Double  temp5;
    @JsonProperty("temp9")              private Double  temp9;
    @JsonProperty("temp29")             private Double  temp29;

    // ── 生長 ────────────────────────────────────────────────────────────
    @JsonProperty("grMean")             private Double  grMean;
    @JsonProperty("bodyLength")         private Double  bodyLength;
    @JsonProperty("neckLengthAccum")    private Double  neckLengthAccum;

    // ── 晶種 ────────────────────────────────────────────────────────────
    @JsonProperty("seedLift")           private Double  seedLift;
    @JsonProperty("seedLiftSp")         private Double  seedLiftSp;
    @JsonProperty("seedLiftTarget")     private Double  seedLiftTarget;
    @JsonProperty("seedRotationSp")     private Double  seedRotationSp;

    // ── 坩堝 ────────────────────────────────────────────────────────────
    @JsonProperty("crucibleRotationSp") private Double  crucibleRotationSp;
    @JsonProperty("crMean")             private Double  crMean;
    @JsonProperty("crucibleLift")       private Double  crucibleLift;
    @JsonProperty("crucibleLiftRatio")  private Double  crucibleLiftRatio;
    @JsonProperty("cruciblePosition")   private Double  cruciblePosition;
    @JsonProperty("cruciblePosCalibrated") private Double cruciblePosCalibrated;
    @JsonProperty("ctpflPul")           private Double  ctpflPul;

    // ── 磁場 ────────────────────────────────────────────────────────────
    @JsonProperty("magnetPv")           private Double  magnetPv;

    // ── 壓力 / 氣流 ─────────────────────────────────────────────────────
    @JsonProperty("argonFlowRate")      private Double  argonFlowRate;
    @JsonProperty("lowerChamberPress")  private Double  lowerChamberPress;
    @JsonProperty("lowerChamberPressSp") private Double lowerChamberPressSp;
    @JsonProperty("throValveOpen")      private Double  throValveOpen;
    @JsonProperty("bpMean")             private Double  bpMean;
    @JsonProperty("bpu60mean")          private Double  bpu60mean;
    @JsonProperty("btplBpul1")          private Double  btplBpul1;
    @JsonProperty("btplBpll1")          private Double  btplBpll1;

    // ── PID ─────────────────────────────────────────────────────────────
    @JsonProperty("pidslDdmean")        private Double  pidslDdmean;
    @JsonProperty("pidslTemp1")         private Double  pidslTemp1;

    // ── 其他 ────────────────────────────────────────────────────────────
    @JsonProperty("residualWeight")     private Double  residualWeight;
    @JsonProperty("countb")             private Integer countb;

    // ── Getters ──────────────────────────────────────────────────────────
    public String  getFurnaceId()           { return furnaceId; }
    public String  getIngotNo()             { return ingotNo; }
    public String  getLogTime()             { return logTime; }
    public String  getReceivedAt()          { return receivedAt; }
    public Integer getEvent()              { return event; }
    public String  getOperationMode()       { return operationMode; }
    public String  getSop()                 { return sop; }
    public Double  getDiameter()            { return diameter; }
    public Double  getDMean()               { return dMean; }
    public Double  getDiameterTarget()      { return diameterTarget; }
    public Double  getHeaterTemp()          { return heaterTemp; }
    public Double  getHeaterTempTarget()    { return heaterTempTarget; }
    public Double  getHeaterPowerSv()       { return heaterPowerSv; }
    public Double  getHtMean()              { return htMean; }
    public Double  getTemp2()               { return temp2; }
    public Double  getTemp4()               { return temp4; }
    public Double  getTemp5()               { return temp5; }
    public Double  getTemp9()               { return temp9; }
    public Double  getTemp29()              { return temp29; }
    public Double  getGrMean()              { return grMean; }
    public Double  getBodyLength()          { return bodyLength; }
    public Double  getNeckLengthAccum()     { return neckLengthAccum; }
    public Double  getSeedLift()            { return seedLift; }
    public Double  getSeedLiftSp()          { return seedLiftSp; }
    public Double  getSeedLiftTarget()      { return seedLiftTarget; }
    public Double  getSeedRotationSp()      { return seedRotationSp; }
    public Double  getCrucibleRotationSp()  { return crucibleRotationSp; }
    public Double  getCrMean()              { return crMean; }
    public Double  getCrucibleLift()        { return crucibleLift; }
    public Double  getCrucibleLiftRatio()   { return crucibleLiftRatio; }
    public Double  getCruciblePosition()    { return cruciblePosition; }
    public Double  getCruciblePosCalibrated() { return cruciblePosCalibrated; }
    public Double  getCtpflPul()            { return ctpflPul; }
    public Double  getMagnetPv()            { return magnetPv; }
    public Double  getArgonFlowRate()       { return argonFlowRate; }
    public Double  getLowerChamberPress()   { return lowerChamberPress; }
    public Double  getLowerChamberPressSp() { return lowerChamberPressSp; }
    public Double  getThroValveOpen()       { return throValveOpen; }
    public Double  getBpMean()              { return bpMean; }
    public Double  getBpu60mean()           { return bpu60mean; }
    public Double  getBtplBpul1()           { return btplBpul1; }
    public Double  getBtplBpll1()           { return btplBpll1; }
    public Double  getPidslDdmean()         { return pidslDdmean; }
    public Double  getPidslTemp1()          { return pidslTemp1; }
    public Double  getResidualWeight()      { return residualWeight; }
    public Integer getCountb()              { return countb; }

    // ── Setters ──────────────────────────────────────────────────────────
    public void setFurnaceId(String v)           { furnaceId = v; }
    public void setIngotNo(String v)             { ingotNo = v; }
    public void setLogTime(String v)             { logTime = v; }
    public void setReceivedAt(String v)          { receivedAt = v; }
    public void setEvent(Integer v)              { event = v; }
    public void setOperationMode(String v)       { operationMode = v; }
    public void setSop(String v)                 { sop = v; }
    public void setDiameter(Double v)            { diameter = v; }
    public void setDMean(Double v)               { dMean = v; }
    public void setDiameterTarget(Double v)      { diameterTarget = v; }
    public void setHeaterTemp(Double v)          { heaterTemp = v; }
    public void setHeaterTempTarget(Double v)    { heaterTempTarget = v; }
    public void setHeaterPowerSv(Double v)       { heaterPowerSv = v; }
    public void setHtMean(Double v)              { htMean = v; }
    public void setTemp2(Double v)               { temp2 = v; }
    public void setTemp4(Double v)               { temp4 = v; }
    public void setTemp5(Double v)               { temp5 = v; }
    public void setTemp9(Double v)               { temp9 = v; }
    public void setTemp29(Double v)              { temp29 = v; }
    public void setGrMean(Double v)              { grMean = v; }
    public void setBodyLength(Double v)          { bodyLength = v; }
    public void setNeckLengthAccum(Double v)     { neckLengthAccum = v; }
    public void setSeedLift(Double v)            { seedLift = v; }
    public void setSeedLiftSp(Double v)          { seedLiftSp = v; }
    public void setSeedLiftTarget(Double v)      { seedLiftTarget = v; }
    public void setSeedRotationSp(Double v)      { seedRotationSp = v; }
    public void setCrucibleRotationSp(Double v)  { crucibleRotationSp = v; }
    public void setCrMean(Double v)              { crMean = v; }
    public void setCrucibleLift(Double v)        { crucibleLift = v; }
    public void setCrucibleLiftRatio(Double v)   { crucibleLiftRatio = v; }
    public void setCruciblePosition(Double v)    { cruciblePosition = v; }
    public void setCruciblePosCalibrated(Double v) { cruciblePosCalibrated = v; }
    public void setCtpflPul(Double v)            { ctpflPul = v; }
    public void setMagnetPv(Double v)            { magnetPv = v; }
    public void setArgonFlowRate(Double v)       { argonFlowRate = v; }
    public void setLowerChamberPress(Double v)   { lowerChamberPress = v; }
    public void setLowerChamberPressSp(Double v) { lowerChamberPressSp = v; }
    public void setThroValveOpen(Double v)       { throValveOpen = v; }
    public void setBpMean(Double v)              { bpMean = v; }
    public void setBpu60mean(Double v)           { bpu60mean = v; }
    public void setBtplBpul1(Double v)           { btplBpul1 = v; }
    public void setBtplBpll1(Double v)           { btplBpll1 = v; }
    public void setPidslDdmean(Double v)         { pidslDdmean = v; }
    public void setPidslTemp1(Double v)          { pidslTemp1 = v; }
    public void setResidualWeight(Double v)      { residualWeight = v; }
    public void setCountb(Integer v)             { countb = v; }

    @Override
    public String toString() {
        return String.format("[%s|%s] mode=%s Ø=%.1f T=%.1f GR=%.3f",
                furnaceId, ingotNo, operationMode,
                diameter != null ? diameter : 0.0,
                heaterTemp != null ? heaterTemp : 0.0,
                grMean != null ? grMean : 0.0);
    }
}
