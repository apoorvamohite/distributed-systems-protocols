package edu.sjsu.cs249.zooleader;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooKeeper;

public class LunchTimeWatcher implements Watcher {
    private ZooKeeper zk;
    private String lunchZnodePath;
    private String zookeeperClientName;
    private LunchDataStorageHelper dataStorageHelper;

    public LunchTimeWatcher(ZooKeeper zk, String lunchZnodePath, String zookeeperClientName,
            LunchDataStorageHelper dataHelper) {
        this.zk = zk;
        this.lunchZnodePath = lunchZnodePath;
        this.zookeeperClientName = zookeeperClientName;
        this.dataStorageHelper = dataHelper;
    }

    @Override
    public void process(WatchedEvent event) {
        // TODO Auto-generated method stub
        try {
            if (event.getType() == EventType.NodeCreated) {
                String leaderName = new String(zk.getData(lunchZnodePath + ZooLunchConstants.LEADER, false, null),
                        "UTF-8");
                List<String> attendees = zk.getChildren(lunchZnodePath, false).stream()
                        .filter(child -> child.startsWith(lunchZnodePath + ZooLunchConstants.ZK_PREFIX))
                        .collect(Collectors.toList());
                if (leaderName.equals(zookeeperClientName)) {
                    Lunch lunch = new Lunch(true, leaderName, "Apoorva's Kitchen", attendees);
                    dataStorageHelper.lunchMap
                            .put(zk.exists(lunchZnodePath + ZooLunchConstants.LUNCH_TIME, false).getCzxid(), lunch);
                } else if (attendees.stream().anyMatch(child -> child.contains(zookeeperClientName))) {
                    Lunch lunch = new Lunch(false, null, null, null);
                    dataStorageHelper.lunchMap
                            .put(zk.exists(lunchZnodePath + ZooLunchConstants.LUNCH_TIME, false).getCzxid(), lunch);
                }
                System.out.println("lunchMap is : "+dataStorageHelper.lunchMap);
                dataStorageHelper.sendToFile();
                System.out.println("Re-registering LunchTimeWatcher");
                zk.exists("/lunch/lunchtime", this);
            } else if (event.getType() == EventType.NodeDeleted) {
                if (zk.exists(lunchZnodePath + ZooLunchConstants.ZK_PREFIX + zookeeperClientName, false) != null) {
                    zk.delete(lunchZnodePath + ZooLunchConstants.ZK_PREFIX + zookeeperClientName, -1);
                }
                String leaderName = new String(zk.getData(lunchZnodePath + ZooLunchConstants.LEADER, false, null),
                        "UTF-8");
                if (leaderName.equals(zookeeperClientName)) {
                    zk.delete(lunchZnodePath + ZooLunchConstants.LEADER, -1);
                }
            }

        } catch (KeeperException | InterruptedException | UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
