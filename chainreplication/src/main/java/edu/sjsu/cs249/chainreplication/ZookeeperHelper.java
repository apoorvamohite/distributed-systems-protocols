package edu.sjsu.cs249.chainreplication;

import edu.sjsu.cs249.chain.AckRequest;
import edu.sjsu.cs249.chain.NewSuccessorRequest;
import edu.sjsu.cs249.chain.ReplicaGrpc;
import edu.sjsu.cs249.chain.UpdateRequest;
import io.grpc.ManagedChannelBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.*;

public class ZookeeperHelper {

    public static final Logger logger = LogManager.getLogger(ZookeeperHelper.class);
    private ZooKeeper zooKeeper;
    private DefaultWatcher defaultWatcher;
    private static ZookeeperHelper instance;

    public Constants.REPLICA_STATE getCurrentState() {
        return currentState;
    }

    private Constants.REPLICA_STATE currentState;
    private String znodeSequence;

    public String getZnodeSequence() {
        return znodeSequence;
    }

    public String getControlPath() {
        return controlPath;
    }

    private String controlPath;
    private String grpcHostPort;

    public ReplicaNode getPredecessor() {
        return predecessor;
    }

    public ReplicaNode getSuccessor() {
        return successor;
    }

    private ReplicaNode predecessor;
    private ReplicaNode successor;

    public Map<String, TableEntry> getHashtable() {
        return hashtable;
    }

    private Map<String, TableEntry> hashtable;

    public List<HistoryItem> getHistory() {
        return history;
    }

    public List<SentItem> getSentList() {
        return sentList;
    }

    private List<HistoryItem> history;
    private List<SentItem> sentList;
    private int lastXid = -1;

    public int getLastXid() {
//        return history.size() > 0 ? history.get(history.size()-1).getXid() : -1;
//        return history.stream().mapToInt(hi -> hi.getXid()).max().orElse(-1);
        return lastXid;
    }

    synchronized public int generateXid() {
        lastXid++;
        return lastXid;
    }

    synchronized public void insertHistoryItem(HistoryItem hi) {
        // If head, generate new xid
        if(hi.getIncRequest() != null) {
            hi.setXid(getLastXid() + 1);
        }
        lastXid = Math.max(lastXid, hi.getXid());
        history.add(hi);
    }

//    public void setLastXid(int lastXid) {
//        this.lastXid = lastXid;
//    }

    private int lastAck = -1;

    public int getLastAck() {
        return lastAck;
    }

    public void setLastAck(int lastAck) {
        if(lastAck > this.lastAck) {
            this.lastAck = lastAck;
        }
    }

    public long getLastPzxid() {
        return lastPzxid;
    }

    private long lastPzxid;

    private ZookeeperHelper(){

    }
    public static ZookeeperHelper getInstance(){
        if(instance == null) {
            instance = new ZookeeperHelper();
        }
        return instance;
    }

    public void init(String address, String controlPath, String grpcHostPort, String myName) throws IOException {
        this.controlPath = controlPath;
        this.grpcHostPort = grpcHostPort;
        hashtable = Collections.synchronizedMap(new HashMap<>());
        history = Collections.synchronizedList(new LinkedList<>());
        sentList = Collections.synchronizedList(new LinkedList<>());
        zooKeeper = new ZooKeeper(address, 8000, (watchEvent) -> {
            System.out.println("Init default watcher");
        });
        zooKeeper.register(new DefaultWatcher());
        String data = grpcHostPort + "\n" + myName;
        try {
            String createdPath = zooKeeper.create(controlPath + Constants.REPLICA_PREFIX, data.getBytes(),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            znodeSequence = createdPath.replace(controlPath, "").replace("/", "");
            updateReplicaCurrentState();
            if(predecessor != null){
                handleNewPredecessor();
            }
        } catch (KeeperException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        logger.info("Initialization complete");
    }

    public void updateReplicaCurrentState() throws InterruptedException, KeeperException {
        System.out.println("ThreadID 2: " + Thread.currentThread().getName() + " " + Thread.currentThread().threadId());
        List<String> children = zooKeeper.getChildren(controlPath, true);
        Collections.sort(children);
        String predec = null, succ = null;
        if(!children.isEmpty() && children.get(0).equals(znodeSequence)){
            System.out.println("HEAD");
            currentState = Constants.REPLICA_STATE.HEAD;
            succ = children.size() > 1 ? children.get(1) : null;
        } else if (!children.isEmpty() && children.get(children.size() - 1).equals(znodeSequence)) {
            System.out.println("TAIL");
            currentState = Constants.REPLICA_STATE.TAIL;
            predec = children.size() > 1 ? children.get(children.size()-2) : null;
        } else if (!children.isEmpty() && children.stream().anyMatch(r -> r.equals(znodeSequence))) {
            System.out.println("REPLICA");
            currentState = Constants.REPLICA_STATE.REPLICA;
            for(int i=0; i<children.size(); i++){
                if(children.get(i).equals(znodeSequence)){
                    predec = children.get(i-1);
                    succ = children.get(i+1);
                }
            }
        }
        if (!children.isEmpty() && children.size()==1 && children.get(0).equals(znodeSequence)){
            currentState = Constants.REPLICA_STATE.ALL;
        }
        if(predec != null){
            Stat preStat = new Stat();
            predecessor = new ReplicaNode(predec,
                    new String(zooKeeper.getData(controlPath + "/" + predec, true, preStat)));
        } else {
            predecessor = null;
        }
        if(succ != null){
            Stat sucStat = new Stat();
            successor = new ReplicaNode(succ,
                    new String(zooKeeper.getData(controlPath + "/" + succ, true, sucStat)));
        } else {
            successor = null;
        }
        // Possible watch issue
        lastPzxid = zooKeeper.exists(controlPath, true).getPzxid();
        logger.info("Possibly updated replica state");
        logger.debug(Util.logState(""));
    }

    public void handleNewPredecessor() {
        logger.debug("==================================================");
        logger.info("Start of handleNewPredecessor");
        // Make a NewSuccessor call to new predecessor
        var channel = ManagedChannelBuilder.forTarget(predecessor.address).usePlaintext().build();
        var stub = ReplicaGrpc.newBlockingStub(channel);
        NewSuccessorRequest newSuccessorRequest = NewSuccessorRequest.newBuilder()
                .setLastXid(lastXid)
                .setLastAck(lastAck)
                .setZnodeName(getZnodeSequence())
                .setLastZxidSeen(getLastPzxid())
                .build();
        logger.info("nsr {} get {} act {}", newSuccessorRequest.getLastXid(), getLastXid(), lastXid);
        logger.info("nsr {} get {} act {}", newSuccessorRequest.getLastAck(), getLastAck(), lastAck);
        logger.error("lastXid {} lastAck {}", getLastXid(), getLastAck());
        logger.info("Sending NewSuccessorRequest " + newSuccessorRequest);
        var newSuccessorResponse = stub.newSuccessor(newSuccessorRequest);
        channel.shutdownNow();
        logger.info("Got NewSuccessorResponse from successor " + newSuccessorResponse);
        // If rc == 1, does that mean I need to handle it myself? What is missing sent messages?
        if(currentState == Constants.REPLICA_STATE.REPLICA) {
            // Possible issue with xid here
            for(UpdateRequest updateRequest : newSuccessorResponse.getSentList()){
                hashtable.put(updateRequest.getKey(), new TableEntry(updateRequest.getNewValue()));
                HistoryItem hi = new HistoryItem(updateRequest);
                insertHistoryItem(hi);
                sentList.add(new SentItem(hi.getXid(), null));
                setLastAck(hi.getXid());

                channel = ManagedChannelBuilder.forTarget(getSuccessor().address).usePlaintext().build();
                stub = ReplicaGrpc.newBlockingStub(channel);
                var updateResponse = stub.update(updateRequest);
                logger.info("Got UpdateResponse from successor " + updateResponse);
            }
        } else if (currentState == Constants.REPLICA_STATE.TAIL){
            // Possible issue with xid here
            for(UpdateRequest updateRequest : newSuccessorResponse.getSentList()){
                hashtable.put(updateRequest.getKey(), new TableEntry(updateRequest.getNewValue()));
                HistoryItem hi = new HistoryItem(updateRequest);
                insertHistoryItem(hi);
                setLastAck(hi.getXid());
                logger.info("LastAck updated to " + lastAck);
                channel = ManagedChannelBuilder.forTarget(getPredecessor().address).usePlaintext().build();
                stub = ReplicaGrpc.newBlockingStub(channel);
                AckRequest ackRequest = AckRequest.newBuilder()
                        .setXid(hi.getXid())
                        .build();
                logger.info("Sending new AckRequest to predecessor " + ackRequest);
                var ackResponse = stub.ack(ackRequest);
                logger.info("Got AckResponse from predecessor " + ackResponse);
            }
        }
        channel.shutdownNow();
        logger.info("End of handleNewPredecessor");
        logger.debug("==================================================");
    }
}

class ReplicaNode {
    public String znodeSequence;
    public String address;
    public String name;

    public ReplicaNode(String znodeSequence, String znodeData) {
        this.znodeSequence = znodeSequence;
        this.address = znodeData.split("\n")[0];
        this.name = znodeData.split("\n")[1];
    }

    @Override
    public String toString() {
        return "ReplicaNode{" +
                "znodeSequence='" + znodeSequence + '\'' +
                ", address='" + address + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
