package com.twin.alarm.service;

import com.twin.alarm.entity.SpcBaseline;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Western Electric 8 條規則。
 *
 * 重要語意：
 *  - 送進來的 value 是「一分鐘子群平均」，與 baseline 的 σ 估計尺度一致
 *    （baseline 用 time_bucket('1 minute') 的平均值算 moving range）。
 *    直接拿原始逐筆讀值評估，會因為平均縮小變異而讓 σ 被低估數倍、管制界過窄。
 *  - 管制界一律取用 baseline 已存好的 ucl/lcl 欄位，這樣 sigmaMultiplier
 *    才會一致套用到全部 8 條規則（舊版只有 Rule 1 吃得到）。
 *  - 違規採「邊緣觸發」：條件由不成立轉為成立才回報一次，
 *    避免同一段異常在每個子群被重複計數。
 */
@Service
public class SpcRuleEngine {

    /** 每個 furnace+param+mode 保留最近 20 個子群平均 */
    private final Map<String, Deque<Double>> buffers = new ConcurrentHashMap<>();

    /** 邊緣觸發狀態：每個 key 目前「已成立」的 ruleId */
    private final Map<String, Set<Integer>> activeRules = new ConcurrentHashMap<>();

    private static final int BUFFER_SIZE = 20;

    private static final Violation[] RULES = new Violation[]{
            null,
            new Violation(1, "1 point outside 3-sigma", "CRITICAL"),
            new Violation(2, "9 points on same side of centerline", "WARN"),
            new Violation(3, "6 points trending in one direction", "WARN"),
            new Violation(4, "14 points alternating up and down", "WARN"),
            new Violation(5, "2 of 3 points beyond 2-sigma same side", "WARN"),
            new Violation(6, "4 of 5 points beyond 1-sigma same side", "WARN"),
            new Violation(7, "15 points within 1-sigma (stratification)", "WARN"),
            new Violation(8, "8 points beyond 1-sigma (mixture)", "WARN"),
    };

    private String key(String furnaceId, String paramName, String mode) {
        return furnaceId + ":" + paramName + ":" + mode;
    }

    /** 換晶棒 / 停爐 / 製程階段中斷時清空該爐序列狀態，避免把不連續資料當成連續趨勢 */
    public void resetFurnace(String furnaceId) {
        String prefix = furnaceId + ":";
        buffers.keySet().removeIf(k -> k.startsWith(prefix));
        activeRules.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /**
     * 對一個子群平均值評估 8 條規則。
     * @return 僅包含「本次由不成立轉為成立」的違規
     */
    public List<Violation> check(String furnaceId, String paramName, String mode,
                                 double value, SpcBaseline b) {
        String k = key(furnaceId, paramName, mode);
        Deque<Double> buf = buffers.computeIfAbsent(k, x -> new ArrayDeque<>(BUFFER_SIZE));

        synchronized (buf) {
            buf.addLast(value);
            if (buf.size() > BUFFER_SIZE) buf.removeFirst();

            double[] arr = buf.stream().mapToDouble(Double::doubleValue).toArray();
            double mean = b.getMean();

            // 一律用 baseline 存好的管制界（已含 sigmaMultiplier）
            double ucl3 = b.getUcl3sigma(), lcl3 = b.getLcl3sigma();
            double ucl2 = b.getUcl2sigma(), lcl2 = b.getLcl2sigma();
            double ucl1 = b.getUcl1sigma(), lcl1 = b.getLcl1sigma();

            Set<Integer> nowActive = new HashSet<>();

            if (value > ucl3 || value < lcl3)                             nowActive.add(1);
            if (arr.length >= 9  && checkSameSide(arr, 9, mean))          nowActive.add(2);
            if (arr.length >= 6  && checkMonotonic(arr, 6))               nowActive.add(3);
            if (arr.length >= 14 && checkAlternating(arr, 14))            nowActive.add(4);
            if (arr.length >= 3  && checkNof(arr, 3, 2, ucl2, lcl2))      nowActive.add(5);
            if (arr.length >= 5  && checkNof(arr, 5, 4, ucl1, lcl1))      nowActive.add(6);
            if (arr.length >= 15 && checkAllWithin(arr, 15, lcl1, ucl1))  nowActive.add(7);
            if (arr.length >= 8  && checkAllOutside(arr, 8, lcl1, ucl1))  nowActive.add(8);

            Set<Integer> prevActive = activeRules.computeIfAbsent(k, x -> new HashSet<>());

            List<Violation> fired = new ArrayList<>();
            for (Integer ruleId : nowActive) {
                if (!prevActive.contains(ruleId)) {
                    fired.add(RULES[ruleId]);   // 只在「轉為成立」時觸發
                }
            }
            prevActive.clear();
            prevActive.addAll(nowActive);

            return fired;
        }
    }

    /** 連續 n 點都在中心線同一側（正好落在中心線則中斷） */
    private boolean checkSameSide(double[] arr, int n, double center) {
        int len = arr.length;
        boolean allAbove = true, allBelow = true;
        for (int i = len - n; i < len; i++) {
            if (arr[i] >= center) allBelow = false;
            if (arr[i] <= center) allAbove = false;
        }
        return allAbove || allBelow;
    }

    /** 連續 n 點嚴格單調遞增或遞減 */
    private boolean checkMonotonic(double[] arr, int n) {
        int len = arr.length;
        boolean up = true, down = true;
        for (int i = len - n + 1; i < len; i++) {
            if (arr[i] <= arr[i - 1]) up = false;
            if (arr[i] >= arr[i - 1]) down = false;
        }
        return up || down;
    }

    /**
     * 連續 n 點上下交替（真正的鋸齒）。
     * 相鄰差值必須「正負相間且皆不為 0」。
     * 舊版只排除「三點單調」，導致持平/相等的序列（例如 diameter 一直是 0、
     * heaterPowerSv 一直是 85）被誤判成交替而大量誤報。
     */
    private boolean checkAlternating(double[] arr, int n) {
        int len = arr.length;
        Integer prevSign = null;
        for (int i = len - n + 1; i < len; i++) {
            double d = arr[i] - arr[i - 1];
            if (d == 0) return false;                                  // 持平 → 不是交替
            int sign = (d > 0) ? 1 : -1;
            if (prevSign != null && sign == prevSign) return false;    // 同向 → 不是交替
            prevSign = sign;
        }
        return true;
    }

    /** 最近 window 點中，至少 required 點落在同一側的界外 */
    private boolean checkNof(double[] arr, int window, int required, double upper, double lower) {
        int len = arr.length;
        int above = 0, below = 0;
        for (int i = len - window; i < len; i++) {
            if (arr[i] > upper) above++;
            else if (arr[i] < lower) below++;
        }
        return above >= required || below >= required;
    }

    /** 連續 n 點全部落在 [lower, upper] 之內 */
    private boolean checkAllWithin(double[] arr, int n, double lower, double upper) {
        int len = arr.length;
        for (int i = len - n; i < len; i++) {
            if (arr[i] < lower || arr[i] > upper) return false;
        }
        return true;
    }

    /** 連續 n 點全部落在 [lower, upper] 之外 */
    private boolean checkAllOutside(double[] arr, int n, double lower, double upper) {
        int len = arr.length;
        for (int i = len - n; i < len; i++) {
            if (arr[i] >= lower && arr[i] <= upper) return false;
        }
        return true;
    }

    @Getter
    public static class Violation {
        private final int ruleId;
        private final String ruleName;
        private final String severity;

        public Violation(int ruleId, String ruleName, String severity) {
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.severity = severity;
        }
    }
}
