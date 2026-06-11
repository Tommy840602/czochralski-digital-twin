#!/bin/bash
echo "等待 Flink JobManager 啟動..."
until curl -sf http://localhost:8083/jobs/overview > /dev/null; do
    echo "Flink 尚未就緒，等待 10 秒..."
    sleep 10
done

echo "Flink 已就緒，提交 Job..."
docker exec twin-flink-jobmanager flink run \
    -d \
    -c com.twin.flink.job.FurnaceStreamJob \
    /opt/flink-jobs.jar

echo "Flink Job 已提交！"
