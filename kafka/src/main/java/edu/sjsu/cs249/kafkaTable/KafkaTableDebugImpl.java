package edu.sjsu.cs249.kafkaTable;

import io.grpc.stub.StreamObserver;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.time.OffsetDateTime;

public class KafkaTableDebugImpl extends KafkaTableDebugGrpc.KafkaTableDebugImplBase {

    @Override
    public synchronized void debug(KafkaTableDebugRequest request, StreamObserver<KafkaTableDebugResponse> responseObserver) {
        System.out.println("Got debug request");
        KafkaManager kafkaManager = KafkaManager.getInstance();

//            synchronized (kafkaManager.orderConsumer) {
                TopicPartition opsTp = new TopicPartition(kafkaManager.topicPrefix + Constants.OPERATIONS_TOPIC, 0);
//                OffsetAndMetadata opsOffsetAndMetadata = kafkaManager.operationsConsumer.committed(opsTp);
                TopicPartition ordTp = new TopicPartition(kafkaManager.topicPrefix + Constants.ORDERING_TOPIC, 0);
//                OffsetAndMetadata ordOffsetAndMetadata = kafkaManager.orderConsumer.committed(ordTp);


        synchronized (kafkaManager.clientCounters) {
            synchronized (kafkaManager.hashtable) {
                Snapshot snapshot = Snapshot.newBuilder()
                        .setReplicaId(kafkaManager.replicaName)
                        .putAllTable(kafkaManager.hashtable)
//                        .setOperationsOffset(opsOffsetAndMetadata.offset())
                        .setOperationsOffset(kafkaManager.lastOpsOffset)
                        .putAllClientCounters(kafkaManager.clientCounters)
//                        .setSnapshotOrderingOffset(ordOffsetAndMetadata.offset())
                        .setSnapshotOrderingOffset(kafkaManager.lastOrderOffset)
                        .build();
                responseObserver.onNext(KafkaTableDebugResponse.newBuilder().setSnapshot(snapshot).build());
                responseObserver.onCompleted();
                System.out.println("Debug response sent" + snapshot);
            }
        }
    }

    @Override
    public void exit(ExitRequest request, StreamObserver<ExitResponse> responseObserver) {
        KafkaManager kafkaManager = KafkaManager.getInstance();
        kafkaManager.operationsConsumer.close();
        kafkaManager.snapshotConsumer.close();
        kafkaManager.orderConsumer.close();
        kafkaManager.producer.close();
        responseObserver.onNext(ExitResponse.newBuilder().build());
        responseObserver.onCompleted();
        System.out.println("Got exit");
        System.exit(0);
    }
}
