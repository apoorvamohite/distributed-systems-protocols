package org.example;

import edu.sjsu.cs249.chain.*;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ReplicaImpl extends ReplicaGrpc.ReplicaImplBase {

    public ReplicaImpl(){
        super();
        System.out.println("ReplicaImpl");
    }

    @Override
    public void update(UpdateRequest request, StreamObserver<UpdateResponse> responseObserver) {
        ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();
        if(zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.REPLICA){
            if(request.getXid() > zookeeperHelper.getLastXid()) {
                zookeeperHelper.getHashtable().put(request.getKey(), request.getNewValue());
            }
            HistoryItem hi = new HistoryItem(request);
            zookeeperHelper.insertHistoryItem(hi);
            zookeeperHelper.getSentList().add(new SentItem(hi.getXid(), null));
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
            var updateResponse = stub.update(updateRequest);
            System.out.println("Got UpdateResponse from successor " + updateResponse);
        } else if(zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.TAIL) {
            if(request.getXid() > zookeeperHelper.getLastXid()) {
                zookeeperHelper.getHashtable().put(request.getKey(), request.getNewValue());
            }
            HistoryItem hi = new HistoryItem(request);
            zookeeperHelper.insertHistoryItem(hi);
            responseObserver.onNext(UpdateResponse.newBuilder().build());
            responseObserver.onCompleted();

            zookeeperHelper.setLastAck(hi.getXid());

            var channel = ManagedChannelBuilder.forTarget(zookeeperHelper.getPredecessor().address).usePlaintext().build();
            var stub = ReplicaGrpc.newBlockingStub(channel);
            AckRequest ackRequest = AckRequest.newBuilder()
                    .setXid(hi.getXid())
                    .build();
            var ackResponse = stub.ack(ackRequest);
            System.out.println("Got AckResponse from predecessor " + ackResponse);
        }
    }

    @Override
    public void newSuccessor(NewSuccessorRequest request, StreamObserver<NewSuccessorResponse> responseObserver) {
        // What do I do with the pzxid?
        ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();
        if(zookeeperHelper.getCurrentState() != Constants.REPLICA_STATE.TAIL) {
            if(request.getZnodeName() != zookeeperHelper.getSuccessor().znodeSequence){
                responseObserver.onNext(NewSuccessorResponse.newBuilder().setRc(-1).build());
                responseObserver.onCompleted();
                return;
            }

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
    }

    @Override
    public void ack(AckRequest request, StreamObserver<AckResponse> responseObserver) {
        ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();
        if(zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.HEAD){
            SentItem sentItem = zookeeperHelper.getSentList().stream().filter(si -> si.getXid() == request.getXid()).toList().get(0);
            sentItem.getResponseObserver().onNext(HeadResponse.newBuilder().setRc(0).build());
            sentItem.getResponseObserver().onCompleted();
            zookeeperHelper.getSentList().removeIf(si -> si.getXid() == request.getXid());
            responseObserver.onNext(AckResponse.newBuilder().build());
            responseObserver.onCompleted();
            zookeeperHelper.setLastAck(request.getXid());
        } else if (zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.REPLICA){
            zookeeperHelper.getSentList().removeIf(si -> si.getXid() == request.getXid());

            responseObserver.onNext(AckResponse.newBuilder().build());
            responseObserver.onCompleted();

            zookeeperHelper.setLastAck(request.getXid());

            var channel = ManagedChannelBuilder.forTarget(zookeeperHelper.getPredecessor().address).usePlaintext().build();
            var stub = ReplicaGrpc.newBlockingStub(channel);
            AckRequest ackRequest = AckRequest.newBuilder().setXid(request.getXid()).build();
            var ackResponse = stub.ack(ackRequest);
            System.out.println("Got AckResponse from predecessor " + ackResponse);
        } else if (zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.TAIL) {
            // Don't do anything
        }
    }
}
