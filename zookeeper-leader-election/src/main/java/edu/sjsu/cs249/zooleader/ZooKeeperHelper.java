package edu.sjsu.cs249.zooleader;

import java.io.IOException;
import java.util.Map;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

public class ZooKeeperHelper {
    public static ZooKeeper connectToZooKeeperInstance(String host, String lunchZnodePath, String name,
            LunchDataStorageHelper dataHelper) throws IOException, InterruptedException {
        ZooKeeper zoo = new ZooKeeper(host, 8000, (watchEvent) -> {
            System.out.println("The default watcher ---- " + watchEvent);
        });
        watchReadyForLunch(zoo, lunchZnodePath, name, dataHelper);
        return zoo;
    }

    public static void createEmployeeNode(ZooKeeper zk, String name, String addr) {
        try {
            zk.create("/lunch/employee/zk-" + name, addr.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        } catch (KeeperException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void watchReadyForLunch(ZooKeeper zk, String lunchZnodePath, String name,
            LunchDataStorageHelper dataHelper) {
        try {
            zk.exists(lunchZnodePath + ZooLunchConstants.READY_FOR_LUNCH,
                    new ReadyForLunchWatcher(zk, lunchZnodePath, name, dataHelper));
        } catch (KeeperException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void tryLeader(ZooKeeper zk, String lunchZnodePath, String zookeeperClientName) {
        try {
            zk.create(lunchZnodePath + "/leader", zookeeperClientName.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL);
        } catch (KeeperException e) {
            if (e.code() == KeeperException.Code.NODEEXISTS) {
                try {
                    zk.exists(lunchZnodePath + "/leader", new LeaderWatcher(zk, lunchZnodePath, zookeeperClientName));
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
