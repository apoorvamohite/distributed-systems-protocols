package edu.sjsu.cs249.zooleader;

import java.io.IOException;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

public class ZooKeeperHelper {
    public static ZooKeeper connectToZooKeeperInstance(String host, String lunchZnodePath, String name,
            LunchDataStorageHelper dataHelper) throws IOException, InterruptedException {
        ZooKeeper zoo = new ZooKeeper(host, 10000, (watchEvent) -> {
            System.out.println("The old default watcher ---- " + watchEvent);
        });
        zoo.register(new DefaultWatcher(zoo, lunchZnodePath, name, dataHelper));
        signUpForLunchIfReady(zoo, lunchZnodePath, name, dataHelper);
        // signUpForLunch(zoo, lunchZnodePath, name, dataHelper);
        watchReadyForLunch(zoo, lunchZnodePath, name, dataHelper);
        Thread.currentThread().getThreadGroup().list();
        return zoo;
    }

    public static void createEmployeeNode(ZooKeeper zk, String lunchZnodePath, String name, String addr) {
        try {
            zk.create(lunchZnodePath + ZooLunchConstants.EMPLOYEE + ZooLunchConstants.ZK_PREFIX + name, addr.getBytes(),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        } catch (KeeperException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void signUpForLunchIfReady(ZooKeeper zk, String lunchZnodePath, String zookeeperClientName,
            LunchDataStorageHelper dataStorageHelper) {
        try {
            if (zk.exists(lunchZnodePath + ZooLunchConstants.READY_FOR_LUNCH, true) != null
                    && zk.exists(lunchZnodePath + ZooLunchConstants.LUNCH_TIME, true) == null) {
                signUpForLunch(zk, lunchZnodePath, zookeeperClientName, dataStorageHelper);
            }
        } catch (KeeperException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void signUpForLunch(ZooKeeper zk, String lunchZnodePath, String zookeeperClientName,
            LunchDataStorageHelper dataStorageHelper) {
        try {
            var res = zk.create(lunchZnodePath + ZooLunchConstants.ZK_PREFIX + zookeeperClientName, null,
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
            new Thread(() -> ZooKeeperHelper.tryLeader(zk, lunchZnodePath, zookeeperClientName, dataStorageHelper))
                    .start();
            // Register LunchTimeWatcher
            System.out.println("Re-registering LunchTime");
            zk.exists(lunchZnodePath + ZooLunchConstants.LUNCH_TIME, true);
        } catch (KeeperException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void watchReadyForLunch(ZooKeeper zk, String lunchZnodePath, String name,
            LunchDataStorageHelper dataHelper) {
        try {
            // zk.exists(lunchZnodePath + ZooLunchConstants.READY_FOR_LUNCH,
            // new ReadyForLunchWatcher(zk, lunchZnodePath, name, dataHelper));
            zk.exists(lunchZnodePath + ZooLunchConstants.READY_FOR_LUNCH, true);
        } catch (KeeperException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void tryLeader(ZooKeeper zk, String lunchZnodePath, String zookeeperClientName,
            LunchDataStorageHelper dataStorageHelper) {
        try {
            Thread.currentThread().getThreadGroup().list();
            Stat readyForLunchZxidBefore = zk.exists(lunchZnodePath + ZooLunchConstants.READY_FOR_LUNCH, true);
            int numEmployees = zk.getChildren(lunchZnodePath + ZooLunchConstants.EMPLOYEE, false).size();
            System.out.println("Num emp" + numEmployees + "last "
                    + Math.min(numEmployees, dataStorageHelper.getLastLeadershipIndex()));
            System.out.println("Sleeping for"
                    + (numEmployees - Math.min(numEmployees, dataStorageHelper.getLastLeadershipIndex()) + 1));
            System.out.println("last leadership" + dataStorageHelper.getLastLeadershipIndex());
            Thread.sleep(
                    Math.max(0, (numEmployees - Math.min(numEmployees, dataStorageHelper.getLastLeadershipIndex()) - 1))
                            * 1000);
            // Thread.sleep(15000);
            Stat readyForLunchZxidAfter = zk.exists(lunchZnodePath + ZooLunchConstants.READY_FOR_LUNCH, true);
            if (readyForLunchZxidBefore != null && readyForLunchZxidAfter != null
                    && readyForLunchZxidBefore.getCzxid() != readyForLunchZxidAfter.getCzxid()) {
                System.out.println("Are u returning here?");
                return;
            }
            zk.create(lunchZnodePath + ZooLunchConstants.LEADER,
                    (zookeeperClientName).getBytes(),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL);
            // zk.create(lunchZnodePath + ZooLunchConstants.LEADER,
            //         (zookeeperClientName.substring(3)).getBytes(),
            //         ZooDefs.Ids.OPEN_ACL_UNSAFE,
            //         CreateMode.EPHEMERAL);
            System.out.println("Re-registering Leader");
            zk.exists(lunchZnodePath + ZooLunchConstants.LEADER, true);
        } catch (KeeperException e) {
            if (e.code() == KeeperException.Code.NODEEXISTS) {
                try {
                    // zk.exists(lunchZnodePath + "/leader", new LeaderWatcher(zk, lunchZnodePath,
                    // zookeeperClientName));
                    System.out.println("Re-registering Leader");
                    zk.exists(lunchZnodePath + ZooLunchConstants.LEADER, true);
                } catch (KeeperException | InterruptedException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
