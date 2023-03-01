package edu.sjsu.cs249.zooleader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
    // Update Thread.sleep value after restart
    // What to respond for goingToLunch()
    public String lunchPath;
    public long counter = 0;
    public LunchDataStorageHelper dataStorageHelper;
    public ZooKeeper zk;

    public ZooLunchServiceImpl(String zookeeperServerAddr, String name, String zookeeperClientAddr,
            String lunchZnodePath) {
        super();
        try {
            lunchPath = lunchZnodePath;
            name = name.replace(" ", "_");
            dataStorageHelper = new LunchDataStorageHelper(zookeeperClientAddr);
            zk = ZooKeeperHelper.connectToZooKeeperInstance(zookeeperServerAddr, lunchZnodePath, name,
                    dataStorageHelper);
            ZooKeeperHelper.createEmployeeNode(zk, name, zookeeperClientAddr);
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
        long zxid = request.getZxid();
        Lunch lunch = dataStorageHelper.lunchMap.get(zxid);
        GetLunchResponse.Builder rb;
        if (lunch.leader) {
            rb = GetLunchResponse.newBuilder().setRc(0).setLeader(lunch.leaderName).setRestaurant(lunch.restaurant);
            int i = 0;
            for (String attendee : lunch.attendees) {
                rb.setAttendees(i++, attendee);
            }
        } else {
            rb = GetLunchResponse.newBuilder().setRc(1);
        }
        responseObserver.onNext(rb.build());
        responseObserver.onCompleted();
        System.out.println("getLunch() returning: " + lunch);
    }

    @Override
    public void goingToLunch(GoingToLunchRequest request, StreamObserver<GoingToLunchResponse> responseObserver) {
        // TODO Auto-generated method stub
        try {
            System.out.println("getData call"+zk.exists("/lunch/readyforlunch", true));
        } catch (KeeperException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        responseObserver.onNext(GoingToLunchResponse.newBuilder().setRc(1).build());
        responseObserver.onCompleted();
        System.out.println("goingToLunch request complete");
    }

    @Override
    public void lunchesAttended(LunchesAttendedRequest request,
            StreamObserver<LunchesAttendedResponse> responseObserver) {
        // TODO Auto-generated method stub
        List<Long> zxids = new ArrayList<Long>(dataStorageHelper.lunchMap.keySet());
        LunchesAttendedResponse.Builder rb;
        rb = LunchesAttendedResponse.newBuilder();
        int i = 0;
        for (Long zxid : zxids) {
            rb.setZxids(i++, zxid);
        }
        responseObserver.onNext(rb.build());
        responseObserver.onCompleted();
        System.out.println("lunchesAttended() returning: " + zxids);
    }

    @Override
    public void skipLunch(SkipRequest request, StreamObserver<SkipResponse> responseObserver) {
        // TODO Auto-generated method stub
        super.skipLunch(request, responseObserver);
    }

}
