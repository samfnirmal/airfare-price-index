package com.whoisjson.airfarepriceindex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

public class AirfareFlinkProcessor {

    public static void main(String[] args) throws Exception {
        System.out.println("⚙️ Starting Flink Real-Time Airfare Processor...");
        
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        ObjectMapper mapper = new ObjectMapper();

        // 1. Connect Flink to Kafka Topic
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setTopics("raw-flight-fares")
                .setGroupId("flink-analytics-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> kafkaStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");

        // 2. Deserialize JSON into Java Objects
        DataStream<FlightEvent> flightEvents = kafkaStream.map(json -> mapper.readValue(json, FlightEvent.class));

        // 3. Process the stream: Group by Route, open a 10-second window, and calculate the Index
        DataStream<String> analyticsStream = flightEvents
                .keyBy(event -> event.route)
                .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
                .aggregate(new IndexAggregator());

        // 4. Output the results (For now, print to console. Next step: sink to DB)
        analyticsStream.print();

        env.execute("Airfare Index Streaming Job");
    }

    // Flink Aggregator: Calculates Average Fare and Index on the fly
    public static class IndexAggregator implements AggregateFunction<FlightEvent, double[], String> {
        private static final double BASE_PRICE = 4500.0;

        @Override
        public double[] createAccumulator() {
            // [Total Price Sum, Quote Count, Min Fare, Max Fare]
            return new double[]{0.0, 0.0, Double.MAX_VALUE, 0.0};
        }

        @Override
        public double[] add(FlightEvent value, double[] acc) {
            acc[0] += value.price;
            acc[1] += 1;
            acc[2] = Math.min(acc[2], value.price);
            acc[3] = Math.max(acc[3], value.price);
            return acc;
        }

        @Override
        public String getResult(double[] acc) {
            double avgFare = acc[1] == 0 ? 0 : acc[0] / acc[1];
            double index = (avgFare / BASE_PRICE) * 100.0;
            
            return String.format(
                "🔥 [FLINK ANALYTICS] Window Result | Quotes: %d | Avg: ₹%.0f | Min: ₹%.0f | Max: ₹%.0f | 🎯 ROUTE INDEX: %.2f", 
                (int) acc[1], avgFare, acc[2], acc[3], index
            );
        }

        @Override
        public double[] merge(double[] a, double[] b) {
            return new double[]{a[0] + b[0], a[1] + b[1], Math.min(a[2], b[2]), Math.max(a[3], b[3])};
        }
    }
}