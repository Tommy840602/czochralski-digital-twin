package com.twin.flink.model;

import java.io.Serializable;

public class AlarmEvent implements Serializable {
    private String  alarmType;
    private String  furnaceId;
    private String  ingotNo;
    private String  severity;
    private String  message;
    private String  triggeredAt;
    private Double  diameter;
    private Double  heaterTemp;
    private Integer event;
    private String  operationMode;

    public AlarmEvent() {}

    public AlarmEvent(String alarmType, String furnaceId, String ingotNo,
                      String severity, String message, String triggeredAt,
                      Double diameter, Double heaterTemp, Integer event, String operationMode) {
        this.alarmType     = alarmType;
        this.furnaceId     = furnaceId;
        this.ingotNo       = ingotNo;
        this.severity      = severity;
        this.message       = message;
        this.triggeredAt   = triggeredAt;
        this.diameter      = diameter;
        this.heaterTemp    = heaterTemp;
        this.event         = event;
        this.operationMode = operationMode;
    }

    public String  getAlarmType()     { return alarmType; }
    public String  getFurnaceId()     { return furnaceId; }
    public String  getIngotNo()       { return ingotNo; }
    public String  getSeverity()      { return severity; }
    public String  getMessage()       { return message; }
    public String  getTriggeredAt()   { return triggeredAt; }
    public Double  getDiameter()      { return diameter; }
    public Double  getHeaterTemp()    { return heaterTemp; }
    public Integer getEvent()         { return event; }
    public String  getOperationMode() { return operationMode; }

    @Override
    public String toString() {
        return "[" + severity + "] " + alarmType + " furnace=" + furnaceId + " " + message;
    }
}
