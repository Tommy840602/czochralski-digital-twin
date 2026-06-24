package com.twin.furnace.dto;
import com.twin.furnace.model.FurnaceMetrics;
import com.twin.furnace.model.FurnaceRegistry;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
public class FurnaceLatestDto {
    private String furnaceId, displayName, status, ingotNo, operationMode;
    private OffsetDateTime lastLogTime;
    // 顯式核心欄位（向後相容：TwinView / RealtimeTrends / SectionScene 直接讀）
    private Double diameter, diameterTarget, heaterTemp, heaterPowerSv,
            grMean, bodyLength, seedLift, residualWeight;
    // 通用：所有 numeric 欄位整包（Dashboard 下拉/全欄顯示用）。新增感測器零改動。
    private Map<String, Double> metrics = new LinkedHashMap<>();

    public static FurnaceLatestDto fromRedis(String id, Map<Object,Object> m) {
        FurnaceLatestDto d = new FurnaceLatestDto();
        d.furnaceId=id; d.displayName="長晶爐 "+id; d.status="running";
        d.ingotNo=str(m,"ingotNo"); d.operationMode=str(m,"operationMode");
        d.diameter=dbl(m,"diameter"); d.diameterTarget=dbl(m,"diameterTarget");
        d.heaterTemp=dbl(m,"heaterTemp"); d.heaterPowerSv=dbl(m,"heaterPowerSv");
        d.grMean=dbl(m,"grMean"); d.bodyLength=dbl(m,"bodyLength");
        d.seedLift=dbl(m,"seedLift"); d.residualWeight=dbl(m,"residualWeight");
        // 通用 metrics：把 hash 內所有可轉 double 的欄位整包帶上
        for (Map.Entry<Object,Object> e : m.entrySet()) {
            Double v = parseD(e.getValue());
            if (v != null) d.metrics.put(String.valueOf(e.getKey()), v);
        }
        return d;
    }
    public static FurnaceLatestDto fromMetrics(FurnaceMetrics m, FurnaceRegistry r) {
        FurnaceLatestDto d = new FurnaceLatestDto();
        d.furnaceId=m.getFurnaceId();
        d.displayName=(r!=null&&r.getDisplayName()!=null)?r.getDisplayName():"長晶爐 "+m.getFurnaceId();
        d.status=r!=null?r.getStatus():"idle";
        d.ingotNo=m.getIngotNo(); d.operationMode=m.getOperationMode();
        d.lastLogTime=m.getTime(); d.diameter=m.getDiameter();
        d.diameterTarget=m.getDiameterTarget(); d.heaterTemp=m.getHeaterTemp();
        d.heaterPowerSv=m.getHeaterPowerSv(); d.grMean=m.getGrMean();
        d.bodyLength=m.getBodyLength(); d.seedLift=m.getSeedLift();
        d.residualWeight=m.getResidualWeight();
        // DB fallback：顯式欄位也塞進 metrics，至少下拉有基本欄位
        putM(d.metrics,"diameter",m.getDiameter());       putM(d.metrics,"diameterTarget",m.getDiameterTarget());
        putM(d.metrics,"heaterTemp",m.getHeaterTemp());    putM(d.metrics,"heaterPowerSv",m.getHeaterPowerSv());
        putM(d.metrics,"grMean",m.getGrMean());            putM(d.metrics,"bodyLength",m.getBodyLength());
        putM(d.metrics,"seedLift",m.getSeedLift());        putM(d.metrics,"residualWeight",m.getResidualWeight());
        return d;
    }
    public static FurnaceLatestDto offline(String id) {
        FurnaceLatestDto d = new FurnaceLatestDto();
        d.furnaceId=id; d.displayName="長晶爐 "+id; d.status="offline";
        return d;
    }
    private static String str(Map<Object,Object> m,String k){Object v=m.get(k);return v!=null?v.toString():null;}
    private static Double dbl(Map<Object,Object> m,String k){return parseD(m.get(k));}
    private static Double parseD(Object v){
        if(v==null) return null;
        try { String s=v.toString().trim(); if(s.isEmpty()) return null; return Double.parseDouble(s); }
        catch(Exception e){ return null; }
    }
    private static void putM(Map<String,Double> map,String k,Double v){ if(v!=null) map.put(k,v); }

    public String getFurnaceId()          { return furnaceId; }
    public String getDisplayName()        { return displayName; }
    public String getStatus()             { return status; }
    public String getIngotNo()            { return ingotNo; }
    public String getOperationMode()      { return operationMode; }
    public OffsetDateTime getLastLogTime(){ return lastLogTime; }
    public Double getDiameter()           { return diameter; }
    public Double getDiameterTarget()     { return diameterTarget; }
    public Double getHeaterTemp()         { return heaterTemp; }
    public Double getHeaterPowerSv()      { return heaterPowerSv; }
    public Double getGrMean()             { return grMean; }
    public Double getBodyLength()         { return bodyLength; }
    public Double getSeedLift()           { return seedLift; }
    public Double getResidualWeight()     { return residualWeight; }
    public Map<String, Double> getMetrics() { return metrics; }
}
