package com.twin.furnace.dto;
import com.twin.furnace.model.FurnaceMetrics;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
public class FurnaceHistoryDto {
    public static class DataPoint {
        private OffsetDateTime time;
        private String operationMode;
        private Double diameter, heaterTemp, grMean, bodyLength, heaterPowerSv, seedLift, residualWeight;
        public OffsetDateTime getTime()   { return time; }
        public String getOperationMode()  { return operationMode; }
        public Double getDiameter()       { return diameter; }
        public Double getHeaterTemp()     { return heaterTemp; }
        public Double getGrMean()         { return grMean; }
        public Double getBodyLength()     { return bodyLength; }
        public Double getHeaterPowerSv()  { return heaterPowerSv; }
        public Double getSeedLift()       { return seedLift; }
        public Double getResidualWeight() { return residualWeight; }
    }
    private String furnaceId, resolution;
    private OffsetDateTime from, to;
    private int count;
    private List<DataPoint> data;
    public static FurnaceHistoryDto of(String id, OffsetDateTime from, OffsetDateTime to,
                                       String resolution, List<FurnaceMetrics> rows) {
        List<DataPoint> pts = rows.stream().map(m -> {
            DataPoint p = new DataPoint();
            p.time=m.getTime(); p.operationMode=m.getOperationMode();
            p.diameter=m.getDiameter(); p.heaterTemp=m.getHeaterTemp();
            p.grMean=m.getGrMean(); p.bodyLength=m.getBodyLength();
            p.heaterPowerSv=m.getHeaterPowerSv(); p.seedLift=m.getSeedLift();
            p.residualWeight=m.getResidualWeight();
            return p;
        }).collect(Collectors.toList());
        FurnaceHistoryDto d = new FurnaceHistoryDto();
        d.furnaceId=id; d.from=from; d.to=to;
        d.resolution=resolution; d.count=pts.size(); d.data=pts;
        return d;
    }
    public String getFurnaceId()     { return furnaceId; }
    public OffsetDateTime getFrom()  { return from; }
    public OffsetDateTime getTo()    { return to; }
    public String getResolution()    { return resolution; }
    public int getCount()            { return count; }
    public List<DataPoint> getData() { return data; }
}
