package edu.sjsu.cs249.zooleader;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooDefs;

public class ReadyForLunchWatcher implements Watcher {
    private ZooKeeper zk;
    private String lunchZnodePath;
    private String zookeeperClientName;
    private LunchDataStorageHelper dataStorageHelper;

    public ReadyForLunchWatcher(ZooKeeper zk, String lunchZnodePath, String zookeeperClientName,
            LunchDataStorageHelper dataHelper) {
        this.zk = zk;
        this.lunchZnodePath = lunchZnodePath;
        this.zookeeperClientName = zookeeperClientName;
        this.dataStorageHelper = dataHelper;
    }

    @Override
    public void process(WatchedEvent event) {
        // TODO Auto-generated method stub
        System.out.println("From my ReadyForLunchWatcher" + event.getPath() + event.getType());
        try {
            if (event.getType() == EventType.NodeCreated) {
                System.out.println(
                        "From my ReadyForLunchWatcher, am I reaching here?" + event.getPath() + event.getType());
                var res = zk.create(lunchZnodePath + ZooLunchConstants.ZK_PREFIX + zookeeperClientName, null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                System.out.println("Result of creating zk node" + res);
                int numEmployees = zk.getChildren(lunchZnodePath + "/employee", false).size();
                Thread.sleep(numEmployees - Math.min(numEmployees, dataStorageHelper.getLastLeadershipIndex()));
                ZooKeeperHelper.tryLeader(zk, lunchZnodePath, zookeeperClientName);
                // Register LunchTimeWatcher
                zk.exists(lunchZnodePath + "/lunchtime", new LunchTimeWatcher(zk, lunchZnodePath, zookeeperClientName, dataStorageHelper));
            } else if (event.getType() == EventType.NodeDeleted) {
                // REMOVE Leader Watcher
            }
            System.out.println("Re-registering");
            zk.exists("/lunch/readyforlunch", this);
        } catch (KeeperException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
