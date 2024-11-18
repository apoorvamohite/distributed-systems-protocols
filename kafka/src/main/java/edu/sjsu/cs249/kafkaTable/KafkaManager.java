package edu.sjsu.cs249.kafkaTable;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class KafkaManager<T> {
    private static Logger logger = LoggerFactory.getLogger(KafkaManager.class);

    private static KafkaManager instance;
    private KafkaManager(){}
    public static KafkaManager getInstance() {
        if(instance == null) {
            instance = new KafkaManager();
        }
        return instance;
    }

    public Map<String, Integer> hashtable;
    public String kafkaAddress;
    public String replicaName;
    public Integer X;
    public String topicPrefix;
    public KafkaProducer<String, byte[]> producer;
    public KafkaConsumer<String, byte[]> operationsConsumer;
    public KafkaConsumer<String, byte[]> snapshotConsumer;
    public KafkaConsumer<String, byte[]> orderConsumer;

    public Map<String, StreamObserver<?>> streamObserverMap;
    public Map<String, Integer> clientCounters;

    public Queue<SnapshotOrdering> orderingQueue;

    public long lastOpsOffset = -1;
    public long lastOrderOffset = -1;

    static {
        // quiet some kafka messages
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
    }

    public void init(String kafkaAddress, String replicaName, Integer X, String topicPrefix) throws InterruptedException {
        this.kafkaAddress = kafkaAddress;
        this.replicaName = replicaName;
        this.X = X;
        this.topicPrefix = topicPrefix;

        // Initialize KafkaProducer
        var properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaAddress);
        producer = new KafkaProducer<>(properties, new StringSerializer(), new ByteArraySerializer());

        this.clientCounters = new ConcurrentHashMap<String, Integer>();

        // Initialize KafkaConsumer
        operationsConsumer = initConsumer(topicPrefix + Constants.OPERATIONS_TOPIC);
        snapshotConsumer = initConsumer(topicPrefix + Constants.SNAPSHOT_TOPIC);
        orderConsumer = initConsumer(topicPrefix + Constants.ORDERING_TOPIC);
        logger.info("Initialized all consumers");
        // Assign partitions
//        TopicPartition opsTp = new TopicPartition(topicPrefix + Constants.OPERATIONS_TOPIC, 0);
//        TopicPartition ordTp = new TopicPartition(topicPrefix + Constants.ORDERING_TOPIC, 0);
//        TopicPartition snapTp = new TopicPartition(topicPrefix + Constants.SNAPSHOT_TOPIC, 0);
//        operationsConsumer.assign(List.of(opsTp));
//        orderConsumer.assign(List.of(ordTp));
//        snapshotConsumer.assign(List.of(snapTp));

//        try {
//            Thread.sleep(Duration.ofSeconds(12));
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }

        this.hashtable = new ConcurrentHashMap<String, Integer>();
        initHashtable();

//        this.clientCounters = Collections.synchronizedMap(new ConcurrentHashMap<String, Integer>());
        this.streamObserverMap = Collections.synchronizedMap(new HashMap<String, StreamObserver<?>>());
    }


    private KafkaConsumer initConsumer(String topic) throws InterruptedException {
        var properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaAddress);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.setProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "10000");
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, topicPrefix+topic);
        var consumer = new KafkaConsumer<>(properties, new StringDeserializer(), new ByteArrayDeserializer());
        Semaphore sem = new Semaphore(0);
        consumer.subscribe(List.of(topic), new ConsumerRebalanceListener(){

            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                logger.error("Partition revoked for topics {} {}", topic, partitions.stream().map(TopicPartition::topic));
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                partitions.stream().forEach(tp -> consumer.seek(tp, 0));
                logger.info("Partition assigned for topics {} {}", topic, partitions.stream().map(TopicPartition::toString));
                sem.release();
            }
        });
        consumer.poll(1);
        sem.acquire();
        logger.debug("Returning consumer");
        return consumer;
    }

    private void initHashtable(){
        TopicPartition tp = new TopicPartition(topicPrefix + Constants.SNAPSHOT_TOPIC, 0);
        // Lazy ??
//        logger.info("first poll {}", snapshotConsumer.poll(Duration.ofSeconds(10)).count());
//        snapshotConsumer.seekToEnd(List.of(tp));
        AtomicLong maxTimestamp = new AtomicLong();
        AtomicReference<ConsumerRecord<String, byte[]>> latestRecord = new AtomicReference<>();
        snapshotConsumer.endOffsets(List.of(tp)).forEach(((topicPartition, offset) -> {
            System.out.println("offset: "+offset);

            // seek to the last offset of each partition
            snapshotConsumer.seek(topicPartition, (offset==0) ? offset:offset - 1);

            // poll to get the last record in each partition
            snapshotConsumer.poll(Duration.ofSeconds(10)).forEach(record -> {

                // the latest record in the 'topic' is the one with the highest timestamp
                if (record.timestamp() > maxTimestamp.get()) {
                    maxTimestamp.set(record.timestamp());
                    latestRecord.set(record);
                }
            });
        }));
        System.out.println("================="+latestRecord.get());
        var lastRecord = latestRecord.get();
        if(lastRecord != null) {
            try {
                Snapshot snapshot = Snapshot.parseFrom(lastRecord.value());
                hashtable.putAll(snapshot.getTableMap());
                logger.info("Initialized hashtable from snapshot {}", hashtable);
                logger.info("snapshot offset is {}", snapshot.getOperationsOffset());
                if(snapshot.getOperationsOffset() > -1) {
                    operationsConsumer.seek(new TopicPartition(topicPrefix + Constants.OPERATIONS_TOPIC, 0), snapshot.getOperationsOffset() + 1);
                }

                if(canPublishOrdering(snapshot.getSnapshotOrderingOffset())) {
                    publishToOrdering();
                }
                lastOpsOffset = snapshot.getOperationsOffset();
                lastOrderOffset = snapshot.getSnapshotOrderingOffset();
                if(snapshot.getClientCountersMap() != null) {
                    clientCounters.putAll(snapshot.getClientCountersMap());
                }
            } catch (InvalidProtocolBufferException e) {
                logger.error("InvalidProtocolBufferException exception {}", e);
            }
        }
//        do {
//            var records = snapshotConsumer.poll(Duration.ofSeconds(1));
//            var lastRecord = records.
//        } while(snapshotConsumer.position(tp) != (lastPosition+1));
    }

    public void processKafkaOperations() {
        while(true) {
            var records = operationsConsumer.poll(Duration.ofSeconds(1));
            for (var record: records) {
                try {
                    System.out.println(record.headers());
                    System.out.println(record.timestamp());
                    System.out.println(record.timestampType());
                    System.out.println(record.offset());
                    PublishedItem publishedItem = PublishedItem.parseFrom(record.value());
                    logger.info("Processing Published Item {}", publishedItem);
                    if(publishedItem.hasGet()){
                        GetRequest getRequest = publishedItem.getGet();
                        System.out.println("+++++++++++GET");
                        if(!clientCounters.containsKey(getRequest.getXid().getClientid()) || clientCounters.get(getRequest.getXid().getClientid()) < getRequest.getXid().getCounter()) {
                            clientCounters.put(getRequest.getXid().getClientid(), getRequest.getXid().getCounter());
                            System.out.println("New get request");
                            if (streamObserverMap.containsKey(Util.getXidKey(getRequest.getXid()))) {
                                System.out.println("streamObserverMap contains key");
                                GetResponse response = GetResponse.newBuilder()
                                        .setValue(hashtable.getOrDefault(getRequest.getKey(), 0))
                                        .build();
                                StreamObserver<GetResponse> streamObserver = (StreamObserver<GetResponse>) streamObserverMap.get(Util.getXidKey(getRequest.getXid()));
                                streamObserver.onNext(response);
                                streamObserver.onCompleted();
                                logger.info("Get response sent {}", response);
                                streamObserverMap.remove(Util.getXidKey(getRequest.getXid()));
                                lastOpsOffset = record.offset();
                            }
                            clientCounters.put(getRequest.getXid().getClientid(), getRequest.getXid().getCounter());
                        } else {
                            System.out.println("Got an old get request");
                            if (streamObserverMap.containsKey(Util.getXidKey(getRequest.getXid()))) {
                                System.out.println("streamObserverMap contains key");
                                GetResponse response = GetResponse.newBuilder()
                                        .setValue(hashtable.getOrDefault(getRequest.getKey(), 0))
                                        .build();
                                StreamObserver<GetResponse> streamObserver = (StreamObserver<GetResponse>) streamObserverMap.get(Util.getXidKey(getRequest.getXid()));
                                streamObserver.onNext(response);
                                streamObserver.onCompleted();
                                logger.info("Get response sent {}", response);
                                streamObserverMap.remove(Util.getXidKey(getRequest.getXid()));
                                lastOpsOffset = record.offset();
                            }
                        }
                    } else {
                        IncRequest incRequest = publishedItem.getInc();
                        System.out.println("+++++++++++INc");
                        if(!clientCounters.containsKey(incRequest.getXid().getClientid()) || clientCounters.get(incRequest.getXid().getClientid()) < incRequest.getXid().getCounter()) {
                            clientCounters.put(incRequest.getXid().getClientid(), incRequest.getXid().getCounter());
                            if ((hashtable.getOrDefault(incRequest.getKey(), 0) + incRequest.getIncValue()) >= 0) {
                                hashtable.put(incRequest.getKey(),
                                        hashtable.getOrDefault(incRequest.getKey(), 0) + incRequest.getIncValue());
                                logger.info("updated hash table");
                            }
                            logger.info("streamObserverMap {}", streamObserverMap.keySet());
                            if (streamObserverMap.containsKey(Util.getXidKey(incRequest.getXid()))) {
                                StreamObserver<IncResponse> streamObserver = (StreamObserver<IncResponse>) streamObserverMap.get(Util.getXidKey(incRequest.getXid()));
                                streamObserver.onNext(IncResponse.newBuilder().build());
                                streamObserver.onCompleted();
                                logger.info("Inc response sent");
                                streamObserverMap.remove(Util.getXidKey(incRequest.getXid()));
                                lastOpsOffset = record.offset();
                            }
                            clientCounters.put(incRequest.getXid().getClientid(), incRequest.getXid().getCounter());
                        } else {
                            System.out.println("Got an old request");
                            if (streamObserverMap.containsKey(Util.getXidKey(incRequest.getXid()))) {
                                StreamObserver<IncResponse> streamObserver = (StreamObserver<IncResponse>) streamObserverMap.get(Util.getXidKey(incRequest.getXid()));
                                streamObserver.onNext(IncResponse.newBuilder().build());
                                streamObserver.onCompleted();
                                logger.info("Inc response sent");
                                streamObserverMap.remove(Util.getXidKey(incRequest.getXid()));
                            }
                        }
                    }
                    // Snapshot time
                    if(record.offset() % X == 0) {
                        System.out.println("Is it snapshot time? " + record.offset() + "  " + (record.offset() % X == 0));
                        logger.info("calling poll on order");
                        var orderRecords = orderConsumer.poll(Duration.ofSeconds(5));
                        logger.info("calling poll on order finished {}", orderRecords.count());
                        var iter = orderRecords.iterator();
                        if(iter.hasNext()) {
                            var firstRecord = iter.next();
                            if (firstRecord != null) {
                                var snapshotOrdering = SnapshotOrdering.parseFrom(firstRecord.value());
                                if (snapshotOrdering.getReplicaId().equals(replicaName)) {
                                    System.out.println("Publishing my snapshot");
                                    publishMySnapshot(record.offset(), firstRecord.offset());
                                }
                                lastOrderOffset = firstRecord.offset();
                                orderConsumer.seek(new TopicPartition(topicPrefix + Constants.ORDERING_TOPIC, 0), firstRecord.offset() + 1);
                            }
                        }
                    }
                    System.out.println("hashtable" + hashtable);
                    System.out.println("========================================================");
                } catch (InvalidProtocolBufferException e) {
                    logger.error("InvalidProtocolBufferException exception {}", e);
                }
            }
        }
    }

    public void publishMySnapshot(long operationsOffset, long orderOffset){
        Snapshot snapshot = Snapshot.newBuilder()
                .setReplicaId(replicaName)
                .putAllTable(hashtable)
                .setOperationsOffset(operationsOffset)
                .putAllClientCounters(clientCounters)
                .setSnapshotOrderingOffset(orderOffset)
                .build();
        var record = new ProducerRecord<String, byte[]>(topicPrefix+Constants.SNAPSHOT_TOPIC, snapshot.toByteArray());
        producer.send(record);
        publishToOrdering();
        logger.info("Published my snapshot");
    }

    private void publishToOrdering(){
        SnapshotOrdering ordering = SnapshotOrdering.newBuilder().setReplicaId(replicaName).build();
        var orderRecord = new ProducerRecord<String, byte[]>(topicPrefix+Constants.ORDERING_TOPIC, ordering.toByteArray());
        RecordMetadata res = null;
        try {
            res = producer.send(orderRecord).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        logger.info("Published ordering record {}, {}", ordering, res);
    }

    private boolean canPublishOrdering(long snapshotOrderingOffset){
        long orig = snapshotOrderingOffset;
        if(snapshotOrderingOffset <= -1){
            snapshotOrderingOffset = 0;
        }
        // What if there are more than 500 events
        TopicPartition tp = new TopicPartition(topicPrefix + Constants.ORDERING_TOPIC, 0);
        orderConsumer.seek(tp, snapshotOrderingOffset);
        var records = orderConsumer.poll(Duration.ofSeconds(1));
        // ?????????????
        if(orig <= -1){
            orderConsumer.seek(tp, 0);
        } else {
            orderConsumer.seek(tp, snapshotOrderingOffset);
        }
        for(var record : records){
            try {
                SnapshotOrdering snapshotOrdering = SnapshotOrdering.parseFrom(record.value());
                if(snapshotOrdering.getReplicaId().equals(replicaName)){
                    return false;
                }
            } catch (InvalidProtocolBufferException e) {
                logger.error("InvalidProtocolBufferException exception {}", e);
            }
        }
        return true;
    }
}
