package edu.sjsu.cs249.kafkaTable;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaTableImpl extends KafkaTableGrpc.KafkaTableImplBase {
    private static Logger logger = LoggerFactory.getLogger(KafkaTableImpl.class);
    @Override
    public void inc(IncRequest request, StreamObserver<IncResponse> responseObserver) {
        publishToOperations(
                PublishedItem.newBuilder().setInc(request).build(),
                Util.getXidKey(request.getXid()),
                responseObserver);
        logger.info("Published Inc Operation {}", request);
    }

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        publishToOperations(
                PublishedItem.newBuilder().setGet(request).build(),
                Util.getXidKey(request.getXid()),
                responseObserver);
        logger.info("Published Get Operation {}", request);
    }

    private <T> void publishToOperations(PublishedItem request, String xid, StreamObserver<T> streamObserver){
        KafkaManager kafkaManager = KafkaManager.getInstance();
        var record = new ProducerRecord<String, byte[]>(
                kafkaManager.topicPrefix + Constants.OPERATIONS_TOPIC, request.toByteArray());
        kafkaManager.producer.send(record);
        kafkaManager.streamObserverMap.put(xid, streamObserver);
        System.out.println("streammmm " + kafkaManager.streamObserverMap.keySet());
    }
}
