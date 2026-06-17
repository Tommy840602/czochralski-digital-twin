package com.twin.furnace.model;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Entity
@Table(name = "furnace_metrics")
@IdClass(FurnaceMetrics.FurnaceMetricsId.class)
public class FurnaceMetrics {

    public static class FurnaceMetricsId implements Serializable {
        private OffsetDateTime time;
        private String furnaceId;
        public FurnaceMetricsId() {}
        public FurnaceMetricsId(OffsetDateTime time, String furnaceId) {
            this.time = time; this.furnaceId = furnaceId;
        }
    }

    @Id @Column(name="time", nullable=false)   private OffsetDateTime time;
    @Id @Column(name="furnace_id", length=20)  private String furnaceId;
    @Column(name="ingot_no", length=30)        private String ingotNo;
    @Column(name="operation_mode", length=30)  private String operationMode;
    @Column(name="sop", length=100)            private String sop;
    @Column(name="diameter")        private Double diameter;
    @Column(name="d_mean")          private Double dMean;
    @Column(name="diameter_target") private Double diameterTarget;
    @Column(name="heater_temp")         private Double heaterTemp;
    @Column(name="heater_temp_target")  private Double heaterTempTarget;
    @Column(name="heater_power_sv")     private Double heaterPowerSv;
    @Column(name="ht_mean")             private Double htMean;
    @Column(name="temp2")  private Double temp2;
    @Column(name="temp4")  private Double temp4;
    @Column(name="temp5")  private Double temp5;
    @Column(name="temp9")  private Double temp9;
    @Column(name="temp29") private Double temp29;
    @Column(name="gr_mean")            private Double grMean;
    @Column(name="body_length")        private Double bodyLength;
    @Column(name="neck_length_accum")  private Double neckLengthAccum;
    @Column(name="seed_lift")          private Double seedLift;
    @Column(name="seed_lift_sp")       private Double seedLiftSp;
    @Column(name="seed_lift_target")   private Double seedLiftTarget;
    @Column(name="seed_rotation_sp")   private Double seedRotationSp;
    @Column(name="crucible_rotation_sp")    private Double crucibleRotationSp;
    @Column(name="cr_mean")                 private Double crMean;
    @Column(name="crucible_lift")           private Double crucibleLift;
    @Column(name="crucible_lift_ratio")     private Double crucibleLiftRatio;
    @Column(name="crucible_position")       private Double cruciblePosition;
    @Column(name="crucible_pos_calibrated") private Double cruciblePosCalibrated;
    @Column(name="ctpfl_pul")               private Double ctpflPul;
    @Column(name="magnet_pv")               private Double magnetPv;
    @Column(name="argon_flow_rate")         private Double argonFlowRate;
    @Column(name="lower_chamber_press")     private Double lowerChamberPress;
    @Column(name="lower_chamber_press_sp")  private Double lowerChamberPressSp;
    @Column(name="thro_valve_open")         private Double throValveOpen;
    @Column(name="bp_mean")    private Double bpMean;
    @Column(name="bpu60mean")  private Double bpu60mean;
    @Column(name="btpl_bpul1") private Double btplBpul1;
    @Column(name="btpl_bpll1") private Double btplBpll1;
    @Column(name="pidsl_ddmean") private Double pidslDdmean;
    @Column(name="pidsl_temp1")  private Double pidslTemp1;
    @Column(name="residual_weight") private Double residualWeight;
    @Column(name="countb")          private Integer countb;
    @Column(name="created_at", updatable=false) private OffsetDateTime createdAt;

    public OffsetDateTime getTime()  { return time; }
    public String getFurnaceId()     { return furnaceId; }
    public String getIngotNo()       { return ingotNo; }
    public String getOperationMode() { return operationMode; }
    public String getSop()           { return sop; }
    public Double getDiameter()      { return diameter; }
    public Double getDMean()         { return dMean; }
    public Double getDiameterTarget(){ return diameterTarget; }
    public Double getHeaterTemp()    { return heaterTemp; }
    public Double getHeaterTempTarget(){ return heaterTempTarget; }
    public Double getHeaterPowerSv() { return heaterPowerSv; }
    public Double getHtMean()        { return htMean; }
    public Double getTemp2()         { return temp2; }
    public Double getTemp4()         { return temp4; }
    public Double getTemp5()         { return temp5; }
    public Double getTemp9()         { return temp9; }
    public Double getTemp29()        { return temp29; }
    public Double getGrMean()        { return grMean; }
    public Double getBodyLength()    { return bodyLength; }
    public Double getNeckLengthAccum(){ return neckLengthAccum; }
    public Double getSeedLift()      { return seedLift; }
    public Double getSeedLiftSp()    { return seedLiftSp; }
    public Double getSeedLiftTarget(){ return seedLiftTarget; }
    public Double getSeedRotationSp(){ return seedRotationSp; }
    public Double getCrucibleRotationSp(){ return crucibleRotationSp; }
    public Double getCrMean()        { return crMean; }
    public Double getCrucibleLift()  { return crucibleLift; }
    public Double getCrucibleLiftRatio(){ return crucibleLiftRatio; }
    public Double getCruciblePosition(){ return cruciblePosition; }
    public Double getCruciblePosCalibrated(){ return cruciblePosCalibrated; }
    public Double getCtpflPul()      { return ctpflPul; }
    public Double getMagnetPv()      { return magnetPv; }
    public Double getArgonFlowRate() { return argonFlowRate; }
    public Double getLowerChamberPress()  { return lowerChamberPress; }
    public Double getLowerChamberPressSp(){ return lowerChamberPressSp; }
    public Double getThroValveOpen() { return throValveOpen; }
    public Double getBpMean()        { return bpMean; }
    public Double getBpu60mean()     { return bpu60mean; }
    public Double getBtplBpul1()     { return btplBpul1; }
    public Double getBtplBpll1()     { return btplBpll1; }
    public Double getPidslDdmean()   { return pidslDdmean; }
    public Double getPidslTemp1()    { return pidslTemp1; }
    public Double getResidualWeight(){ return residualWeight; }
    public Integer getCountb()       { return countb; }
    public OffsetDateTime getCreatedAt(){ return createdAt; }
    public void setFurnaceId(String v)   { furnaceId = v; }
    public void setTime(OffsetDateTime v){ time = v; }
}
