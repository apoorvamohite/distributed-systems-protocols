package org.example;

import edu.sjsu.cs249.chain.NewSuccessorRequest;
import edu.sjsu.cs249.chain.ReplicaGrpc;
import edu.sjsu.cs249.chain.UpdateRequest;
import io.grpc.ManagedChannelBuilder;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

public class DefaultWatcher implements Watcher {
    @Override
    public void process(WatchedEvent event) {
        System.out.println("WatchedEvent:" + event.getPath() + " " + event.getType());
        System.out.println("ThreadID: " + Thread.currentThread().getName() + " " + Thread.currentThread().threadId());
        ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();
        if(event.getPath()!=null && event.getPath().equals(zookeeperHelper.getControlPath()) && event.getType() == Event.EventType.NodeChildrenChanged){
            try {
                zookeeperHelper.updateReplicaCurrentState();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (KeeperException e) {
                throw new RuntimeException(e);
            }
        } else if(event.getPath()!=null &&
                event.getPath().equals(zookeeperHelper.getControlPath() + "/" + zookeeperHelper.getPredecessor().znodeSequence)){
            System.out.println("Predecessor changed!! Make a call to the new predecessor");
            try {
                zookeeperHelper.updateReplicaCurrentState();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (KeeperException e) {
                throw new RuntimeException(e);
            }
            zookeeperHelper.handleNewPredecessor();
        } else if(event.getPath()!=null &&
                event.getPath().equals(zookeeperHelper.getControlPath() + "/" + zookeeperHelper.getSuccessor().znodeSequence)){
            System.out.println("Successor changed!!");
        }
    }
}
