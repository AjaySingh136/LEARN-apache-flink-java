package com.example;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.Properties;

public class KafkaProducerExample {
    public static void main(String[] args) throws Exception {
        String bootstrapServers = "localhost:29092";
        String topic = "test-topic-v2";

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        KafkaProducer<String, String> producer = new KafkaProducer<>(props);

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, "key1", "Hello from Java Kafka!");

        RecordMetadata metadata = producer.send(record).get();
        System.out.printf("Sent record to topic %s partition %d offset %d%n",
                metadata.topic(), metadata.partition(), metadata.offset());

        producer.close();
    }
}