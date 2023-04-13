package edu.sjsu.cs249.chainreplication;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

public class DefaultWatcher implements Watcher {

    public static final Logger logger = LogManager.getLogger(DefaultWatcher.class);
    @Override
    public void process(WatchedEvent event) {
        logger.info("WatchedEvent:" + event.getPath() + " " + event.getType());
        logger.debug("ThreadID: " + Thread.currentThread().getName() + " " + Thread.currentThread().threadId());
        ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();
        logger.debug(Util.logState(""));
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
            logger.info("Predecessor changed!! Make a call to the new predecessor");
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
            logger.info("Successor changed!!");
        }
    }
}
