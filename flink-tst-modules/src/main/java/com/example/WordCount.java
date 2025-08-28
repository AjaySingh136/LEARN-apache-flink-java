package com.example;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

public class WordCount {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Basic restart strategy example
        env.getConfig().setRestartStrategy(RestartStrategies.fixedDelayRestart(
                3, Time.seconds(10)
        ));

        env.fromElements(
                "hello world",
                "hello flink",
                "flink loves streams",
                "hello docker"
        )
        .flatMap(new Tokenizer())
        .keyBy(value -> value.f0)
        .sum(1)
        .print();

        env.execute("Streaming WordCount Example");
    }

    public static final class Tokenizer implements FlatMapFunction<String, Tuple2<String,Integer>> {
        @Override
        public void flatMap(String value, Collector<Tuple2<String, Integer>> out) {
            for (String token : value.toLowerCase().split("\\W+")) {
                if (!token.isEmpty()) {
                    out.collect(Tuple2.of(token, 1));
                }
            }
        }
    }
}