package edu.sjsu.cs249.chainreplication;

import edu.sjsu.cs249.chain.*;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class HeadChainReplicaImpl extends HeadChainReplicaGrpc.HeadChainReplicaImplBase {

    public static final Logger logger = LogManager.getLogger(HeadChainReplicaImpl.class);

    @Override
    synchronized public void increment(IncRequest request, StreamObserver<HeadResponse> responseObserver) {
        logger.debug("==================================================");
        logger.info("IncRequest received " + request);
        // Possible thread interference ------------>
        ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();

        logger.debug(Util.logState("BEFORE"));

        if(zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.HEAD){
            HistoryItem hi = new HistoryItem(request);
            zookeeperHelper.insertHistoryItem(hi);
            zookeeperHelper.getSentList().add(new SentItem(hi.getXid(), responseObserver));
            Map<String, TableEntry> hashtable = zookeeperHelper.getHashtable();
//            hashtable.put(request.getKey(), hashtable.computeIfAbsent(request.getKey(), f->0) + request.getIncValue());
            TableEntry te = hashtable.getOrDefault(request.getKey(), new TableEntry(0));
            te.setXid(hi.getXid());
            te.setValue(te.getValue() + request.getIncValue());
            hashtable.put(request.getKey(), te);
            logger.info("Updated History and Sent list, sending Update request to Successor");

            // Send UpdateRequest to successor
            var channel = ManagedChannelBuilder.forTarget(zookeeperHelper.getSuccessor().address).usePlaintext().build();
            var stub = ReplicaGrpc.newBlockingStub(channel);
            UpdateRequest updateRequest = UpdateRequest.newBuilder()
                    .setKey(request.getKey())
                    .setXid(hi.getXid())
                    .setNewValue(hashtable.get(request.getKey()).getValue())
                    .build();
            var updateResponse = stub.update(updateRequest);
            logger.info("Got UpdateResponse from successor " + updateResponse);
            logger.info("Waiting for AckRequest " + hi.getXid());
            channel.shutdownNow();
        } else if(zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.ALL){
            Map<String, TableEntry> hashtable = zookeeperHelper.getHashtable();
//            hashtable.put(request.getKey(), hashtable.computeIfAbsent(request.getKey(), f->0) + request.getIncValue());
            HistoryItem hi = new HistoryItem(request);
            zookeeperHelper.insertHistoryItem(hi);

            TableEntry te = hashtable.getOrDefault(request.getKey(), new TableEntry(0));
            te.setXid(hi.getXid());
            te.setValue(te.getValue() + request.getIncValue());
            hashtable.put(request.getKey(), te);

            responseObserver.onNext(HeadResponse.newBuilder().setRc(0).build());
            responseObserver.onCompleted();
            zookeeperHelper.setLastAck(hi.getXid());
            logger.info("Single replica responding with rc 0");
        } else {
            responseObserver.onNext(HeadResponse.newBuilder().setRc(1).build());
            responseObserver.onCompleted();
            logger.warn("Replica responding rc 1, I am not the head");
        }
        logger.info(Util.logState("AFTER"));
        logger.info("End of IncRequest");
        logger.debug("==================================================");
    }
}
