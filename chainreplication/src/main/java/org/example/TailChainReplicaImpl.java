package org.example;

import edu.sjsu.cs249.chain.GetRequest;
import edu.sjsu.cs249.chain.GetResponse;
import edu.sjsu.cs249.chain.TailChainReplicaGrpc;
import io.grpc.stub.StreamObserver;

public class TailChainReplicaImpl extends TailChainReplicaGrpc.TailChainReplicaImplBase {

    public TailChainReplicaImpl() {
        super();
        System.out.println("TailChainReplicaImpl");
    }

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();
        if(zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.TAIL || zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.ALL){
            responseObserver.onNext(GetResponse.newBuilder().setRc(0).setValue(zookeeperHelper.getHashtable().get(request.getKey())).build());
            responseObserver.onCompleted();
        } else {
            responseObserver.onNext(GetResponse.newBuilder().setRc(1).build());
            responseObserver.onCompleted();
        }
    }
}
