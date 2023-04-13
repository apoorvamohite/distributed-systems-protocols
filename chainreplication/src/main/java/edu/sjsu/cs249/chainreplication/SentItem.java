package edu.sjsu.cs249.chainreplication;

import edu.sjsu.cs249.chain.HeadResponse;
import io.grpc.stub.StreamObserver;

public class SentItem {
    private int xid;
    private StreamObserver<HeadResponse> responseObserver;

    @Override
    public String toString() {
        return "SentItem{" +
                "xid=" + xid +
                ", responseObserver=" + responseObserver +
                '}';
    }

    public SentItem(int xid, StreamObserver<HeadResponse> responseObserver) {
        this.xid = xid;
        this.responseObserver = responseObserver;
    }

    public int getXid() {
        return xid;
    }

    public StreamObserver<HeadResponse> getResponseObserver() {
        return responseObserver;
    }
}
