package edu.sjsu.cs249.zooleader;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

public class DefaultWatcher implements Watcher {
    private ZooKeeper zk;
    private String lunchZnodePath;
    private String zookeeperClientName;
    private LunchDataStorageHelper dataStorageHelper;

    public DefaultWatcher(ZooKeeper zk, String lunchZnodePath, String zookeeperClientName,
            LunchDataStorageHelper dataHelper) {
        this.zk = zk;
        this.lunchZnodePath = lunchZnodePath;
        this.zookeeperClientName = zookeeperClientName;
        this.dataStorageHelper = dataHelper;
    }

    @Override
    public void process(WatchedEvent event) {
        System.out.println("Holllaaa!!!" + event.getPath() + event.getType());
        if (event.getPath().equals(lunchZnodePath + ZooLunchConstants.READY_FOR_LUNCH)) {
            try {
                if (event.getType() == EventType.NodeCreated) {
                    var res = zk.create(lunchZnodePath + ZooLunchConstants.ZK_PREFIX + zookeeperClientName, null,
                            ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                    int numEmployees = zk.getChildren(lunchZnodePath + ZooLunchConstants.EMPLOYEE, false).size();
                    Thread.sleep(numEmployees - Math.min(numEmployees, dataStorageHelper.getLastLeadershipIndex()));
                    ZooKeeperHelper.tryLeader(zk, lunchZnodePath, zookeeperClientName);
                    // Register LunchTimeWatcher
                    System.out.println("Re-registering LunchTime");
                    zk.exists(lunchZnodePath + ZooLunchConstants.LUNCH_TIME, true);
                } else if (event.getType() == EventType.NodeDeleted) {
                    // REMOVE Leader Watcher
                    if (zk.exists(lunchZnodePath + ZooLunchConstants.LEADER, false) != null) {
                        try {
                            if ((new String(zk.getData(lunchZnodePath + ZooLunchConstants.LEADER, false, null),
                                    ZooLunchConstants.UTF_8)).equals(zookeeperClientName)) {
                                zk.delete(lunchZnodePath + ZooLunchConstants.LEADER, -1);
                            }
                        } catch (UnsupportedEncodingException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                    if (zk.exists(lunchZnodePath + ZooLunchConstants.ZK_PREFIX + zookeeperClientName, false) != null) {
                        zk.delete(lunchZnodePath + ZooLunchConstants.ZK_PREFIX + zookeeperClientName, -1);
                    }
                    zk.exists(lunchZnodePath + ZooLunchConstants.LUNCH_TIME, false);
                }
                if (event.getType() != EventType.DataWatchRemoved) {
                    System.out.println("Re-registering readyforlunch");
                    zk.exists(lunchZnodePath + ZooLunchConstants.READY_FOR_LUNCH, true);
                }
            } catch (KeeperException | InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else if (event.getPath().equals(lunchZnodePath + ZooLunchConstants.LEADER)) {
            if (event.getType() == EventType.NodeDeleted) {
                ZooKeeperHelper.tryLeader(zk, lunchZnodePath, zookeeperClientName);
            }
        } else if (event.getPath().equals(lunchZnodePath + ZooLunchConstants.LUNCH_TIME)) {
            try {
                if (event.getType() == EventType.NodeCreated) {
                    String leaderName = new String(zk.getData(lunchZnodePath + ZooLunchConstants.LEADER, false, null),
                            ZooLunchConstants.UTF_8);
                            System.out.println("getChildren returns" + zk.getChildren(lunchZnodePath, false));
                    List<String> attendees = zk.getChildren(lunchZnodePath, false).stream()
                            .filter(child -> child.contains(ZooLunchConstants.ZK_PREFIX_2))
                            .collect(Collectors.toList());
                    System.out.println("Attendees are: " + attendees);
                    if (leaderName.equals(zookeeperClientName)) {
                        Lunch lunch = new Lunch(true, leaderName, ZooLunchConstants.RESTAURANT_NAME, attendees);
                        System.out.println("Re-registering LunchTime");
                        dataStorageHelper.lunchMap
                                .put(zk.exists(lunchZnodePath + ZooLunchConstants.LUNCH_TIME, true).getCzxid(), lunch);
                    } else if (attendees.stream().anyMatch(child -> child.contains(zookeeperClientName))) {
                        Lunch lunch = new Lunch(false, null, null, null);
                        System.out.println("Re-registering LunchTime");
                        dataStorageHelper.lunchMap
                                .put(zk.exists(lunchZnodePath + ZooLunchConstants.LUNCH_TIME, true).getCzxid(), lunch);
                    }
                    System.out.println("lunchMap is : " + dataStorageHelper.lunchMap);
                    dataStorageHelper.sendToFile();
                    // System.out.println("Re-registering LunchTime");
                    // zk.exists("/lunch/lunchtime", true);
                } else if (event.getType() == EventType.NodeDeleted) {
                    if (zk.exists(lunchZnodePath + ZooLunchConstants.ZK_PREFIX + zookeeperClientName, false) != null) {
                        zk.delete(lunchZnodePath + ZooLunchConstants.ZK_PREFIX + zookeeperClientName, -1);
                    }
                    if (zk.exists(lunchZnodePath + ZooLunchConstants.LEADER, false) != null) {
                        String leaderName = new String(
                                zk.getData(lunchZnodePath + ZooLunchConstants.LEADER, false, null),
                                ZooLunchConstants.UTF_8);
                        if (leaderName.equals(zookeeperClientName)) {
                            zk.delete(lunchZnodePath + ZooLunchConstants.LEADER, -1);
                        }
                    }
                }

            } catch (KeeperException | InterruptedException | UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

}
