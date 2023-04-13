package edu.sjsu.cs249.chainreplication;

import edu.sjsu.cs249.chain.GetRequest;
import edu.sjsu.cs249.chain.GetResponse;
import edu.sjsu.cs249.chain.TailChainReplicaGrpc;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TailChainReplicaImpl extends TailChainReplicaGrpc.TailChainReplicaImplBase {

    public static final Logger logger = LogManager.getLogger(TailChainReplicaImpl.class);

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        logger.debug("==================================================");
        logger.info("GetRequest received");
        ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();
        logger.debug(Util.logState("BEFORE"));
        if(zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.TAIL || zookeeperHelper.getCurrentState() == Constants.REPLICA_STATE.ALL){
            int value = zookeeperHelper.getHashtable().getOrDefault(request.getKey(), new TableEntry(0)).getValue();
            responseObserver.onNext(GetResponse.newBuilder().setRc(0).setValue(value).build());
            responseObserver.onCompleted();
            logger.info("Current Hashtable:\n" + zookeeperHelper.getHashtable());
            logger.info("Replica responding to GetRequest({}) with [ key: {}, value: {} ]", request.getKey(), request.getKey(), value);
        } else {
            responseObserver.onNext(GetResponse.newBuilder().setRc(1).build());
            responseObserver.onCompleted();
            logger.warn("Replica responding rc 1, I am not the tail");
        }
        logger.debug(Util.logState("AFTER"));
        logger.info("End of GetRequest");
        logger.debug("==================================================");
    }
}
