package org.example;

import edu.sjsu.cs249.chain.*;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.Map;

public class HeadChainReplicaImpl extends HeadChainReplicaGrpc.HeadChainReplicaImplBase {

    public HeadChainReplicaImpl(){
        super();
        System.out.println("HeadChainReplicaImpl");
    }

    @Override
    public void increment(IncRequest request, StreamObserver<HeadResponse> responseObserver) {
        // Possible thread interference ------------>
        ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();
        if(zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.HEAD){
            Map<String, Integer> hashtable = zookeeperHelper.getHashtable();
            hashtable.put(request.getKey(), hashtable.computeIfAbsent(request.getKey(), f->0) + request.getIncValue());
            HistoryItem hi = new HistoryItem(request);
            zookeeperHelper.insertHistoryItem(hi);
            zookeeperHelper.getSentList().add(new SentItem(hi.getXid(), responseObserver));

            // Send UpdateRequest to successor
            var channel = ManagedChannelBuilder.forTarget(zookeeperHelper.getSuccessor().address).usePlaintext().build();
            var stub = ReplicaGrpc.newBlockingStub(channel);
            UpdateRequest updateRequest = UpdateRequest.newBuilder()
                    .setKey(request.getKey())
                    .setXid(hi.getXid())
                    .setNewValue(hashtable.get(request.getKey()))
                    .build();
            var updateResponse = stub.update(updateRequest);
            System.out.println("Got UpdateResponse from successor " + updateResponse);
        } else if(zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.ALL){
            Map<String, Integer> hashtable = zookeeperHelper.getHashtable();
            hashtable.put(request.getKey(), hashtable.computeIfAbsent(request.getKey(), f->0) + request.getIncValue());
            HistoryItem hi = new HistoryItem(request);
            zookeeperHelper.insertHistoryItem(hi);
            responseObserver.onNext(HeadResponse.newBuilder().setRc(0).build());
            responseObserver.onCompleted();
        } else {
            responseObserver.onNext(HeadResponse.newBuilder().setRc(1).build());
            responseObserver.onCompleted();
        }
    }
}
