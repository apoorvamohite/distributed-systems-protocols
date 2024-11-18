package edu.sjsu.cs249.kafkaTable;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.concurrent.Callable;

public class Main {

    // TODO
    // seekToEnd() Lazy load, how to handle?
    // Should I respond to duplicate requests?
    private static Logger logger = LoggerFactory.getLogger(Main.class);

    static {
        // quiet some kafka messages
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
    }

    @Command
    static class Cli {
        @Command
        public void replica(@Parameters(description = "Kafka Host:Port") String kafkaAddress,
                            @Parameters(description = "Replica Name") String replicaName,
                            @Parameters(description = "gRPC Port") Integer grpcPort,
                            @Parameters(description = "X") Integer X,
                            @Parameters(description = "Topic Prefix") String topicPrefix) throws IOException, InterruptedException {
            KafkaManager kafkaManager = KafkaManager.getInstance();
            kafkaManager.init(kafkaAddress, replicaName, X, topicPrefix);
            new Thread(() -> {
                kafkaManager.processKafkaOperations();
            }).start();
            Server server = ServerBuilder
                    .forPort(grpcPort)
                    .addService(new KafkaTableImpl())
                    .addService(new KafkaTableDebugImpl())
                    .build();
            logger.info("Replica listening on {}, Control path: {}\n", grpcPort, topicPrefix);
            server.start();
            server.awaitTermination();
        }
    }
    public static void main(String[] args) {
        System.exit(new CommandLine(new Cli()).execute(args));
    }
}