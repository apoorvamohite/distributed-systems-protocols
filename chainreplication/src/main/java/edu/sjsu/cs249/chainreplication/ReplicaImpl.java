package edu.sjsu.cs249.chainreplication;

import edu.sjsu.cs249.chain.*;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ReplicaImpl extends ReplicaGrpc.ReplicaImplBase {

    public static final Logger logger = LogManager.getLogger(ReplicaImpl.class);

    @Override
    public void update(UpdateRequest request, StreamObserver<UpdateResponse> responseObserver) {
        logger.debug("==================================================");
        logger.info("UpdateRequest received " + request);
        ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();
        logger.debug(Util.logState("BEFORE"));
        Map<String, TableEntry> hashtable = zookeeperHelper.getHashtable();
        if(zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.REPLICA){
            HistoryItem hi = new HistoryItem(request);
            zookeeperHelper.insertHistoryItem(hi);
            zookeeperHelper.getSentList().add(new SentItem(hi.getXid(), null));

            if(request.getXid() > hashtable.getOrDefault(request.getKey(), new TableEntry(0)).getXid()) {
                logger.info("Updating hashtable with new request values");
                TableEntry te = hashtable.getOrDefault(request.getKey(), new TableEntry(0));
                te.setXid(hi.getXid());
                te.setValue(request.getNewValue());
                hashtable.put(request.getKey(), request.getNewValue());
            } else {
                logger.warn("Xid less than my max xid, not updating hashtable");
            }
            logger.info("Updated History and Sent list and sending UpdateResponse to predecessor");
            // Not sure about this one
            responseObserver.onNext(UpdateResponse.newBuilder().build());
            responseObserver.onCompleted();

            var channel = ManagedChannelBuilder.forTarget(zookeeperHelper.getSuccessor().address).usePlaintext().build();
            var stub = ReplicaGrpc.newBlockingStub(channel);
            UpdateRequest updateRequest = UpdateRequest.newBuilder()
                    .setKey(request.getKey())
                    .setXid(hi.getXid())
                    .setNewValue(zookeeperHelper.getHashtable().get(request.getKey()))
                    .build();
            logger.info("Sending UpdateRequest to successor " + updateRequest);
            var updateResponse = stub.update(updateRequest);
            logger.info("Got UpdateResponse from successor " + updateResponse);
            channel.shutdownNow();
        } else if(zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.TAIL) {
//            if(request.getXid() > zookeeperHelper.getLastXid()) {
                logger.info("Updating hashtable with new request values");
                zookeeperHelper.getHashtable().put(request.getKey(), request.getNewValue());
//            } else {
//                logger.warn("Xid less than my max xid, not updating hashtable");
//            }
            HistoryItem hi = new HistoryItem(request);
            zookeeperHelper.insertHistoryItem(hi);
            responseObserver.onNext(UpdateResponse.newBuilder().build());
            responseObserver.onCompleted();
            logger.info("Updated History and Sent list and sent UpdateResponse to predecessor");

            zookeeperHelper.setLastAck(hi.getXid());
            logger.info("Updated lastAck to " + zookeeperHelper.getLastAck());
            var channel = ManagedChannelBuilder.forTarget(zookeeperHelper.getPredecessor().address).usePlaintext().build();
            var stub = ReplicaGrpc.newBlockingStub(channel);
            AckRequest ackRequest = AckRequest.newBuilder()
                    .setXid(hi.getXid())
                    .build();
            logger.info("Sending Ack Request to predecessor " + ackRequest);
            var ackResponse = stub.ack(ackRequest);
            logger.info("Got AckResponse from predecessor " + ackResponse);
            channel.shutdownNow();
        }
        logger.debug(Util.logState("AFTER"));
        logger.info("End of UpdateRequest");
        logger.debug("==================================================");
    }

    @Override
    public void newSuccessor(NewSuccessorRequest request, StreamObserver<NewSuccessorResponse> responseObserver) {
        logger.debug("==================================================");
        logger.info("NewSuccessorRequest received" + request);
        // What do I do with the pzxid?
        ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();
        logger.debug(Util.logState("BEFORE"));
        if(zookeeperHelper.getCurrentState() != Constants.REPLICA_STATE.TAIL) {
            logger.debug("Comparing {} and {}", request.getZnodeName(), zookeeperHelper.getSuccessor().znodeSequence);
            if(!request.getZnodeName().equals(zookeeperHelper.getSuccessor().znodeSequence)){
                responseObserver.onNext(NewSuccessorResponse.newBuilder().setRc(-1).build());
                responseObserver.onCompleted();
                logger.error("NewSuccessorRequest from wrong replica, returning");
                return;
            }

            logger.debug("handling NewSuccessorRequest : lastXid {} , lastAck {}", request.getLastXid() , request.getLastAck());

            Set sentXids = zookeeperHelper.getSentList().stream()
                    .filter(si -> si.getXid() > request.getLastXid())
                    .filter(si -> si.getXid() > request.getLastAck())
                    .map(SentItem::getXid)
                    .collect(Collectors.toSet());
            List<UpdateRequest> updateList = zookeeperHelper.getHistory().stream()
                    .filter(hi -> sentXids.contains(hi.getXid()))
                    .map(HistoryItem::getUpdateRequest)
                    .toList();
            // Ack whatever is in sentlist where xid < lastack
            responseObserver.onNext(NewSuccessorResponse.newBuilder()
                    .setRc(0)
                    .setLastXid(zookeeperHelper.getLastXid())
                    .putAllState(zookeeperHelper.getHashtable())
                    .addAllSent(updateList)
                    .build());
            responseObserver.onCompleted();
        }
        logger.debug(Util.logState("AFTER"));
        logger.info("End of NewSuccessorRequest");
        logger.debug("==================================================");
    }

    @Override
    public void ack(AckRequest request, StreamObserver<AckResponse> responseObserver) {
        logger.debug("==================================================");
        logger.info("AckRequest received " + request);
        ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();
        logger.debug(Util.logState("BEFORE"));
        if(zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.HEAD){
            SentItem sentItem = zookeeperHelper.getSentList().stream().filter(si -> si.getXid() == request.getXid()).toList().get(0);
            sentItem.getResponseObserver().onNext(HeadResponse.newBuilder().setRc(0).build());
            sentItem.getResponseObserver().onCompleted();
            zookeeperHelper.getSentList().removeIf(si -> si.getXid() == request.getXid());
            logger.info("Removed SentItem {} from Sent list and responded back to client", sentItem);
            responseObserver.onNext(AckResponse.newBuilder().build());
            responseObserver.onCompleted();
            zookeeperHelper.setLastAck(request.getXid());
            logger.info("Responded to successor with AckResponse, Set lastAck to " + zookeeperHelper.getLastAck());
        } else if (zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.REPLICA){
            zookeeperHelper.getSentList().removeIf(si -> si.getXid() == request.getXid());
            logger.info("Removed SentItem from Sent list");

            responseObserver.onNext(AckResponse.newBuilder().build());
            responseObserver.onCompleted();

            zookeeperHelper.setLastAck(request.getXid());
            logger.info("Responded to successor with AckResponse, Set lastAck to " + zookeeperHelper.getLastAck());

            var channel = ManagedChannelBuilder.forTarget(zookeeperHelper.getPredecessor().address).usePlaintext().build();
            var stub = ReplicaGrpc.newBlockingStub(channel);
            AckRequest ackRequest = AckRequest.newBuilder().setXid(request.getXid()).build();
            logger.info("Sending AckRequest {} to predecessor", ackRequest);
            var ackResponse = stub.ack(ackRequest);
            logger.info("Got AckResponse from predecessor " + ackResponse);
            channel.shutdownNow();
        } else if (zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.TAIL) {
            // Don't do anything
        }
        logger.debug(Util.logState("AFTER"));
        logger.info("End of AckRequest");
        logger.debug("==================================================");
    }
}
