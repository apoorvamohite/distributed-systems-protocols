package edu.sjsu.cs249.zooleader;

import java.io.IOException;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

public class ZooKeeperHelper {
    public static ZooKeeper connectToZooKeeperInstance(String host, String lunchZnodePath, String name,
            LunchDataStorageHelper dataHelper) throws IOException, InterruptedException {
        ZooKeeper zoo = new ZooKeeper(host, 8000, (watchEvent) -> {
            System.out.println("The old default watcher ---- " + watchEvent);
        });
        zoo.register(new DefaultWatcher(zoo, lunchZnodePath, name, dataHelper));
        watchReadyForLunch(zoo, lunchZnodePath, name, dataHelper);
        return zoo;
    }

    public static void createEmployeeNode(ZooKeeper zk, String lunchZnodePath, String name, String addr) {
        try {
            zk.create(lunchZnodePath + ZooLunchConstants.EMPLOYEE + ZooLunchConstants.ZK_PREFIX + name, addr.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        } catch (KeeperException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void watchReadyForLunch(ZooKeeper zk, String lunchZnodePath, String name,
            LunchDataStorageHelper dataHelper) {
        try {
            // zk.exists(lunchZnodePath + ZooLunchConstants.READY_FOR_LUNCH,
            //         new ReadyForLunchWatcher(zk, lunchZnodePath, name, dataHelper));
            zk.exists(lunchZnodePath + ZooLunchConstants.READY_FOR_LUNCH, true);
        } catch (KeeperException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void tryLeader(ZooKeeper zk, String lunchZnodePath, String zookeeperClientName) {
        try {
            zk.create(lunchZnodePath + ZooLunchConstants.LEADER, zookeeperClientName.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL);
        } catch (KeeperException e) {
            if (e.code() == KeeperException.Code.NODEEXISTS) {
                try {
                    // zk.exists(lunchZnodePath + "/leader", new LeaderWatcher(zk, lunchZnodePath, zookeeperClientName));
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
