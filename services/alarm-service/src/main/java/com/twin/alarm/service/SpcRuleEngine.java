package com.twin.alarm.service;

import com.twin.alarm.entity.SpcBaseline;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class SpcRuleEngine {

    /** 每個 furnace + param 保留最近 20 筆的 rolling buffer */
    private final Map<String, Deque<Double>> buffers = new ConcurrentHashMap<>();

    private static final int BUFFER_SIZE = 20;

    public List<Violation> check(String furnaceId, String paramName, double value, SpcBaseline b) {
        String key = furnaceId + ":" + paramName;
        Deque<Double> buf = buffers.computeIfAbsent(key, k -> new ArrayDeque<>(BUFFER_SIZE));

        synchronized (buf) {
            buf.addLast(value);
            if (buf.size() > BUFFER_SIZE) buf.removeFirst();

            List<Violation> violations = new ArrayList<>();
            double[] arr = buf.stream().mapToDouble(Double::doubleValue).toArray();
            double mean = b.getMean();
            double sigma = b.getStdDev();

            // Rule 1: 1 點超出 ±3σ
            if (value > b.getUcl3sigma() || value < b.getLcl3sigma()) {
                violations.add(new Violation(1, "1 point outside 3-sigma", "CRITICAL"));
            }

            // Rule 2: 連續 9 點同一側
            if (arr.length >= 9 && checkSameSide(arr, 9, mean)) {
                violations.add(new Violation(2, "9 points on same side of centerline", "WARN"));
            }

            // Rule 3: 連續 6 點單調
            if (arr.length >= 6 && checkMonotonic(arr, 6)) {
                violations.add(new Violation(3, "6 points trending in one direction", "WARN"));
            }

            // Rule 4: 連續 14 點交替
            if (arr.length >= 14 && checkAlternating(arr, 14)) {
                violations.add(new Violation(4, "14 points alternating up and down", "WARN"));
            }

            // Rule 5: 連續 3 點中 2 點超出同側 ±2σ
            if (arr.length >= 3 && checkNof(arr, 3, 2, mean, mean + 2 * sigma, mean - 2 * sigma)) {
                violations.add(new Violation(5, "2 of 3 points beyond 2-sigma same side", "WARN"));
            }

            // Rule 6: 連續 5 點中 4 點超出同側 ±1σ
            if (arr.length >= 5 && checkNof(arr, 5, 4, mean, mean + sigma, mean - sigma)) {
                violations.add(new Violation(6, "4 of 5 points beyond 1-sigma same side", "WARN"));
            }

            // Rule 7: 連續 15 點在 ±1σ 內
            if (arr.length >= 15 && checkAllWithin(arr, 15, mean - sigma, mean + sigma)) {
                violations.add(new Violation(7, "15 points within 1-sigma (stratification)", "WARN"));
            }

            // Rule 8: 連續 8 點都在 ±1σ 外
            if (arr.length >= 8 && checkAllOutside(arr, 8, mean - sigma, mean + sigma)) {
                violations.add(new Violation(8, "8 points beyond 1-sigma (mixture)", "WARN"));
            }

            return violations;
        }
    }

    private boolean checkSameSide(double[] arr, int n, double center) {
        int len = arr.length;
        boolean allAbove = true, allBelow = true;
        for (int i = len - n; i < len; i++) {
            if (arr[i] >= center) allBelow = false;
            if (arr[i] <= center) allAbove = false;
        }
        return allAbove || allBelow;
    }

    private boolean checkMonotonic(double[] arr, int n) {
        int len = arr.length;
        boolean up = true, down = true;
        for (int i = len - n + 1; i < len; i++) {
            if (arr[i] <= arr[i - 1]) up = false;
            if (arr[i] >= arr[i - 1]) down = false;
        }
        return up || down;
    }

    private boolean checkAlternating(double[] arr, int n) {
        int len = arr.length;
        for (int i = len - n + 2; i < len; i++) {
            double a = arr[i - 2], b = arr[i - 1], c = arr[i];
            // 需要 (a<b<c 或 a>b>c) 之外 = 交替
            if ((a < b && b < c) || (a > b && b > c)) return false;
        }
        return true;
    }

    private boolean checkNof(double[] arr, int window, int required, double center,
                             double upper, double lower) {
        int len = arr.length;
        int above = 0, below = 0;
        for (int i = len - window; i < len; i++) {
            if (arr[i] > upper) above++;
            else if (arr[i] < lower) below++;
        }
        return above >= required || below >= required;
    }

    private boolean checkAllWithin(double[] arr, int n, double lower, double upper) {
        int len = arr.length;
        for (int i = len - n; i < len; i++) {
            if (arr[i] < lower || arr[i] > upper) return false;
        }
        return true;
    }

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