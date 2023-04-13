package edu.sjsu.cs249.chainreplication;

import edu.sjsu.cs249.chain.*;
import io.grpc.stub.StreamObserver;

import java.util.Arrays;

public class ChainDebugImpl extends ChainDebugGrpc.ChainDebugImplBase {

    @Override
    public void debug(ChainDebugRequest request, StreamObserver<ChainDebugResponse> responseObserver) {
        ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();
        responseObserver.onNext(ChainDebugResponse.newBuilder()
//                .putAllState(zookeeperHelper.getHashtable())
                .setXid(zookeeperHelper.getLastXid())
                .addAllLogs(Arrays.asList(Util.logState("")))
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
