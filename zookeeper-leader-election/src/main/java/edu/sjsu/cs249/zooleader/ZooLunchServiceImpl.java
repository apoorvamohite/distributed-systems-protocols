package edu.sjsu.cs249.zooleader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.zookeeper.AddWatchMode;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.checkerframework.common.returnsreceiver.qual.This;

import edu.sjsu.cs249.zooleader.Grpc.ExitRequest;
import edu.sjsu.cs249.zooleader.Grpc.ExitResponse;
import edu.sjsu.cs249.zooleader.Grpc.GetLunchRequest;
import edu.sjsu.cs249.zooleader.Grpc.GetLunchResponse;
import edu.sjsu.cs249.zooleader.Grpc.GoingToLunchRequest;
import edu.sjsu.cs249.zooleader.Grpc.GoingToLunchResponse;
import edu.sjsu.cs249.zooleader.Grpc.LunchesAttendedRequest;
import edu.sjsu.cs249.zooleader.Grpc.LunchesAttendedResponse;
import edu.sjsu.cs249.zooleader.Grpc.SkipRequest;
import edu.sjsu.cs249.zooleader.Grpc.SkipResponse;
import edu.sjsu.cs249.zooleader.ZooLunchGrpc.*;
import io.grpc.stub.StreamObserver;

public class ZooLunchServiceImpl extends ZooLunchImplBase {

    // TODO
    // Watch for changes in number of employees?
    // Watch for changes in leader
    // Update Thread.sleep value
    public String lunchPath;
    public long counter = 0;

    public ZooLunchServiceImpl(String zookeeperServerAddr, String name, String zookeeperClientAddr,
            String lunchZnodePath) {
        super();
        try {
            lunchPath = lunchZnodePath;
            name = name.replace(" ", "_");
            LunchDataStorageHelper dataHelper = new LunchDataStorageHelper(zookeeperClientAddr);
            ZooKeeper zk = ZooKeeperHelper.connectToZooKeeperInstance(zookeeperServerAddr, lunchZnodePath, name, dataHelper);
            ZooKeeperHelper.createEmployeeNode(zk, name, zookeeperClientAddr);
            // ZooKeeperHelper.tryLeader(zk, lunchPath, zookeeperClientAddr);
            // Stat stat = zk.exists(lunchPath + "/employee/zk-apoorva", false);
            // System.out.println("ZNode exists: " + stat);
            // System.out.println(
            // "ZNode data: " + new String(zk.getData(lunchPath + "/employee/zk-apoorva", false, null), "UTF-8"));
            // if (zk.exists(lunchPath + "/readyforlunch", false)!=null) {

            // try {
            // zk.addWatch("/lunch", AddWatchMode.PERSISTENT);
            // } catch (KeeperException e) {
            // // TODO Auto-generated catch block
            // System.out.println(e.getMessage()+e.getCause());
            // e.printStackTrace();
            // }
            // }
            // zk.exists(lunchPath + "/readyforlunch", (watchedEvent) -> {
            // if(watchedEvent.getType() == Watcher.Event.EventType.NodeCreated){
            // // try {
            // // zk.create(lunchPath+"/zk-"+name, name.getBytes(),
            // ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
            // // Thread.sleep(counter);
            // // // zk.exists(lunchPath+"/leader", (watchedLeaderEvent) -> {
            // // // if(watchedLeaderEvent.getType() == Watcher.Event.EventType.NodeDeleted
            // || watchedLeaderEvent.getType() == Watcher.Event.EventType.None)
            // // // });
            // // // zk.exists(lunchPath+"/leader", name.getBytes(),
            // ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);

            // // } catch (KeeperException | InterruptedException e) {
            // // // TODO Auto-generated catch block
            // // e.printStackTrace();
            // // }
            // System.out.println("Ok, readyforlunch was created");
            // }
            // });
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void exitZoo(ExitRequest request, StreamObserver<ExitResponse> responseObserver) {
        ExitResponse response = ExitResponse.newBuilder()
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
        System.out.println("Exiting zoo");
        System.exit(0);
    }

    @Override
    public void getLunch(GetLunchRequest request, StreamObserver<GetLunchResponse> responseObserver) {
        // TODO Auto-generated method stub
        super.getLunch(request, responseObserver);
    }

    @Override
    public void goingToLunch(GoingToLunchRequest request, StreamObserver<GoingToLunchResponse> responseObserver) {
        // TODO Auto-generated method stub
        super.goingToLunch(request, responseObserver);
    }

    @Override
    public void lunchesAttended(LunchesAttendedRequest request,
            StreamObserver<LunchesAttendedResponse> responseObserver) {
        // TODO Auto-generated method stub
        super.lunchesAttended(request, responseObserver);
    }

    @Override
    public void skipLunch(SkipRequest request, StreamObserver<SkipResponse> responseObserver) {
        // TODO Auto-generated method stub
        super.skipLunch(request, responseObserver);
    }

}
