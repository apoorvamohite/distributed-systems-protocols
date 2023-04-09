package edu.sjsu.cs249.zooleader;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

public class DefaultWatcher implements Watcher {
    private ZooKeeper zk;
    private String lunchZnodePath;
    private String zookeeperClientName;
    private LunchDataStorageHelper dataStorageHelper;
    private Thread secondThread;

    public DefaultWatcher(ZooKeeper zk, String lunchZnodePath, String zookeeperClientName,
            LunchDataStorageHelper dataHelper) {
        this.zk = zk;
        this.lunchZnodePath = lunchZnodePath;
        this.zookeeperClientName = zookeeperClientName;
        this.dataStorageHelper = dataHelper;
    }

    @Override
    public void process(WatchedEvent event) {
        System.out.println(
                "!!!" + event.getPath() + event.getType());
        if (event.getPath().equals(lunchZnodePath + ZooLunchConstants.READY_FOR_LUNCH)) {
            try {
                if (event.getType() == EventType.NodeCreated) {
                        // if(dataStorageHelper.skipLunch){
                        //     zk.exists(lunchZnodePath + ZooLunchConstants.READY_FOR_LUNCH, true);
                        //     return;
                        // }
                    // if (zk.exists(lunchZnodePath + ZooLunchConstants.LUNCH_TIME, true) == null) {
                    var res = zk.create(lunchZnodePath + ZooLunchConstants.ZK_PREFIX + zookeeperClientName, null,
                            ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);

                    if (secondThread != null && secondThread.isAlive()) {
                        secondThread.interrupt();
                    }
                    secondThread = new Thread(() -> ZooKeeperHelper.tryLeader(zk, lunchZnodePath,
                            zookeeperClientName, dataStorageHelper));
                    secondThread.start();
                    // Register LunchTimeWatcher
                    System.out.println("Re-registering LunchTime");
                    zk.exists(lunchZnodePath + ZooLunchConstants.LUNCH_TIME, true);
                    // }
                } else if (event.getType() == EventType.NodeDeleted) {
                        // if(dataStorageHelper.skipLunch){
                        //     dataStorageHelper.skipLunch = false;
                        //     zk.exists(lunchZnodePath + ZooLunchConstants.READY_FOR_LUNCH, true);
                        //     return;
                        // }
                    // REMOVE Leader Watcher
                    if (zk.exists(lunchZnodePath + ZooLunchConstants.LEADER, false) != null) {
                        try {
                            if ((new String(zk.getData(lunchZnodePath + ZooLunchConstants.LEADER, false, null),
                                    ZooLunchConstants.UTF_8)).equals(zookeeperClientName)) {
                                zk.delete(lunchZnodePath + ZooLunchConstants.LEADER, -1);
                            }
                        } catch (UnsupportedEncodingException | KeeperException e) {
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
                // if(dataStorageHelper.skipLunch){
                //     return;
                // }
            try {
                if (event.getType() == EventType.NodeDeleted
                        && (zk.exists(lunchZnodePath + ZooLunchConstants.READY_FOR_LUNCH, true) != null)
                        && (zk.exists(lunchZnodePath + ZooLunchConstants.LUNCH_TIME, true) == null)) {
                    if (secondThread != null && secondThread.isAlive()) {
                        secondThread.interrupt();
                    }
                    secondThread = new Thread(() -> ZooKeeperHelper.tryLeader(zk, lunchZnodePath, zookeeperClientName,
                            dataStorageHelper));
                    secondThread.start();
                }
            } catch (KeeperException | InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else if (event.getPath().equals(lunchZnodePath + ZooLunchConstants.LUNCH_TIME)) {
                // if(dataStorageHelper.skipLunch){
                //     return;
                // }
            try {
                if (secondThread != null && secondThread.isAlive()) {
                    secondThread.interrupt();
                }
                if (event.getType() == EventType.NodeCreated) {
                    String leaderName = new String(zk.getData(lunchZnodePath + ZooLunchConstants.LEADER, false, null),
                            ZooLunchConstants.UTF_8);
                    System.out.println("getChildren returns" + zk.getChildren(lunchZnodePath, false));

                    Stat readyStat = zk.exists(lunchZnodePath + ZooLunchConstants.READY_FOR_LUNCH, true);
                    Stat lunchStat = zk.exists(lunchZnodePath + ZooLunchConstants.LUNCH_TIME, true);
                    List<String> attendees = zk.getChildren(lunchZnodePath, false).stream()
                            .filter(child -> child.contains(ZooLunchConstants.ZK_PREFIX_2)).parallel().filter(child -> {
                                Stat childStat;
                                try {
                                    childStat = zk.exists(lunchZnodePath + ZooLunchConstants.ZK_PREFIX + child, false);
                                    if (childStat != null && childStat.getCtime() > readyStat.getCtime()
                                            && childStat.getCtime() < lunchStat.getCtime()) {
                                        return true;
                                    }
                                } catch (KeeperException | InterruptedException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                                return false;
                            })
                            .collect(Collectors.toList());
                    List<String> realAttendees = new ArrayList<String>();
                    // for (String child : attendees) {
                    // Stat childStat = zk.exists(lunchZnodePath + ZooLunchConstants.ZK_PREFIX +
                    // child, false);
                    // if (childStat != null && childStat.getCtime() > readyStat.getCtime()
                    // && childStat.getCtime() < lunchStat.getCtime()) {
                    // realAttendees.add(child);
                    // }
                    // }
                    System.out.println("Attendees are: " + attendees);
                    System.out.println("Real Attendees are: " + realAttendees);
                    System.out.println("Leader is: " + leaderName);
                    if (leaderName.equals(zookeeperClientName)) {
                        // if (leaderName.contains(zookeeperClientName.substring(3))) {
                        Lunch lunch = new Lunch(true, leaderName, ZooLunchConstants.RESTAURANT_NAME, attendees);
                        System.out.println("Re-registering LunchTime");
                        dataStorageHelper.lunchMap
                                .put(zk.exists(lunchZnodePath + ZooLunchConstants.LUNCH_TIME, true).getCzxid(), lunch);
                    } else if (attendees.stream().anyMatch(child -> child.equals(zookeeperClientName)/*child.contains(zookeeperClientName.substring(3))*/)) {
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
                            // if (leaderName.equals(zookeeperClientName.substring(3))) {
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
