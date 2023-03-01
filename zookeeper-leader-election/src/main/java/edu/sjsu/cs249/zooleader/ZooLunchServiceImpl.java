package edu.sjsu.cs249.zooleader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.UnsupportedEncodingException;
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
import org.apache.zookeeper.Watcher.Event.EventType;
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
    public String zookeeperClientName;

    public ZooLunchServiceImpl(String zookeeperServerAddr, String name, String zookeeperClientAddr,
            String lunchZnodePath) {
        super();
        try {
            lunchPath = lunchZnodePath;
            zookeeperClientName = name.replace(" ", "_");
            dataStorageHelper = new LunchDataStorageHelper(zookeeperClientAddr);
            zk = ZooKeeperHelper.connectToZooKeeperInstance(zookeeperServerAddr, lunchZnodePath, zookeeperClientName,
                    dataStorageHelper);
            ZooKeeperHelper.createEmployeeNode(zk, lunchZnodePath, zookeeperClientName, zookeeperClientAddr);
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
            rb.addAllAttendees(lunch.attendees);
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
        GoingToLunchResponse.Builder rb = GoingToLunchResponse.newBuilder();
        try {
            // Lunch getting ready
            if (zk.exists(lunchPath + ZooLunchConstants.READY_FOR_LUNCH, true) != null
                    && zk.exists(lunchPath + ZooLunchConstants.LUNCH_TIME, true) == null) {
                String leaderName = "";
                try {
                    leaderName = new String(zk.getData(lunchPath + ZooLunchConstants.LEADER, true, null), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                if (leaderName.equals(zookeeperClientName)) {
                    rb.setRc(0).setLeader(leaderName);
                } else {
                    rb.setRc(1);
                }
            } else {
                Lunch lastLunch = dataStorageHelper.getLastLunch();
                if (lastLunch != null) {
                    if (lastLunch.leader) {
                        rb.setRc(0).setLeader(lastLunch.leaderName).setRestaurant(lastLunch.restaurant);
                        int i = 0;
                        rb.addAllAttendees(lastLunch.attendees);
                    } else {
                        rb.setRc(1);
                    }
                } else {
                    rb.setRc(1);
                }
            }
        } catch (KeeperException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.printf("goingToLunch() response: \nRC: %d\nLeader: %s\nRestaurant: %s", rb.getRc(), rb.getLeader(),
                rb.getRestaurant());
        for (int i = 0; i < rb.getAttendeesCount(); i++) {
            System.out.println("Attendee 1: " + rb.getAttendees(i));
        }
        responseObserver.onNext(rb.build());
        responseObserver.onCompleted();
    }

    @Override
    public void lunchesAttended(LunchesAttendedRequest request,
            StreamObserver<LunchesAttendedResponse> responseObserver) {
        // TODO Auto-generated method stub
        List<Long> zxids = new ArrayList<Long>(dataStorageHelper.lunchMap.keySet());
        LunchesAttendedResponse.Builder rb;
        rb = LunchesAttendedResponse.newBuilder();
        rb.addAllZxids(zxids);
        responseObserver.onNext(rb.build());
        responseObserver.onCompleted();
        System.out.println("lunchesAttended() returning: " + zxids);
    }

    @Override
    public void skipLunch(SkipRequest request, StreamObserver<SkipResponse> responseObserver) {
        // TODO Auto-generated method stub
        try {
            if (zk.exists(lunchPath + ZooLunchConstants.READY_FOR_LUNCH, false) != null
                    && zk.exists(lunchPath + ZooLunchConstants.LUNCH_TIME, false) == null) {
                if (zk.exists(lunchPath + ZooLunchConstants.ZK_PREFIX + zookeeperClientName, false) != null) {
                    zk.delete(lunchPath + ZooLunchConstants.ZK_PREFIX + zookeeperClientName, -1);
                }
                if (zk.exists(lunchPath + ZooLunchConstants.LEADER, false) != null) {
                    String leaderName = new String(
                            zk.getData(lunchPath + ZooLunchConstants.LEADER, false, null),
                            "UTF-8");
                    if (leaderName.equals(zookeeperClientName)) {
                        zk.delete(lunchPath + ZooLunchConstants.LEADER, -1);
                    }
                }
                zk.exists(lunchPath + ZooLunchConstants.LUNCH_TIME, false);
                zk.exists(lunchPath + ZooLunchConstants.READY_FOR_LUNCH, true);
            } else {
                zk.removeAllWatches(lunchPath + ZooLunchConstants.READY_FOR_LUNCH, Watcher.WatcherType.Any, true);
                // zk.exists(lunchPath + ZooLunchConstants.READY_FOR_LUNCH, false);
                zk.exists(lunchPath + ZooLunchConstants.READY_FOR_LUNCH, new Watcher() {

                    @Override
                    public void process(WatchedEvent event) {
                        System.out.println("Skip Watcher");

                        try {
                            if (event.getType() == EventType.NodeCreated) {
                                System.out.println("In created----------");
                                zk.exists(lunchPath + ZooLunchConstants.READY_FOR_LUNCH, this);
                            } else if (event.getType() == EventType.NodeDeleted) {
                                System.out.println("In deleted -----------");
                                zk.exists(lunchPath + ZooLunchConstants.READY_FOR_LUNCH, true);
                            }
                        } catch (KeeperException | InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (KeeperException | InterruptedException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        responseObserver.onNext(SkipResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

}
