package edu.sjsu.cs249.zooleader;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooKeeper;

public class LeaderWatcher implements Watcher {
    private ZooKeeper zk;
    private String lunchZnodePath;
    private String zookeeperClientName;

    public LeaderWatcher(ZooKeeper zk, String lunchZnodePath, String zookeeperClientName) {
        this.zk = zk;
        this.lunchZnodePath = lunchZnodePath;
        this.zookeeperClientName = zookeeperClientName;
    }

    @Override
    public void process(WatchedEvent event) {
        // TODO Auto-generated method stub
        try {
            if (event.getType() == EventType.NodeDeleted) {
                ZooKeeperHelper.tryLeader(zk, lunchZnodePath, zookeeperClientName);
            }

            System.out.println("Re-registering LeaderWatcher");
            zk.exists("/lunch/leader", this);
        } catch (KeeperException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
