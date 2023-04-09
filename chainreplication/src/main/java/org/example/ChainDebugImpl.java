package org.example;

import edu.sjsu.cs249.chain.*;
import io.grpc.stub.StreamObserver;

public class ChainDebugImpl extends ChainDebugGrpc.ChainDebugImplBase {

    @Override
    public void debug(ChainDebugRequest request, StreamObserver<ChainDebugResponse> responseObserver) {
        ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();
        responseObserver.onNext(ChainDebugResponse.newBuilder()
                .putAllState(zookeeperHelper.getHashtable())
                .setXid(zookeeperHelper.getLastXid())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void exit(ExitRequest request, StreamObserver<ExitResponse> responseObserver) {
        responseObserver.onNext(ExitResponse.newBuilder().build());
        responseObserver.onCompleted();
        System.exit(0);
    }
}
