package edu.sjsu.cs249.abd;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.grpc.stub.StreamObservers;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import edu.sjsu.cs249.abd.Grpc.WriteResponse;

import java.util.ArrayList;
import java.util.HashMap;

public class Main {

    // TODO:
    // Synchronized
    // Understand Concurrent write
    // n+1/2 acks
    // Multi threaded broadcast?
    // What if I don't get (n+1)/2 acks for a write? How to revert the written
    // values on the minority processes? -> (n+1)/2 failure assumption
    // Why single writer?
    public static final int TIMEOUT = 10;
    static Integer acks = 0;
    static Long maxLabel = 0L;
    static Long maxValue = 0L;

    public static void main(String[] args) {
        System.exit(new CommandLine(new Cli()).execute(args));
    }

    @Command(subcommands = { ServerCli.class, ClientCli.class })
    static class Cli {
    }

    @Command(name = "server", mixinStandardHelpOptions = true, description = "start an ABD register server.")
    static class ServerCli implements Callable<Integer> {
        @Parameters(index = "0", description = "host:port listen on.")
        int serverPort;

        @Override
        public Integer call() throws Exception {
            System.out.printf("Listening on %s\n", serverPort);
            Server server = ServerBuilder
                    .forPort(serverPort)
                    .addService(new ABDServiceImpl()).build();

            server.start();
            server.awaitTermination();
            return 0;
        }
    }

    @Command(name = "client", mixinStandardHelpOptions = true, description = "start and ADB client.")
    static class ClientCli {
        @Parameters(index = "0", description = "comma separated list of servers to use.")
        String serverPorts;

        @Command
        public void read(@Parameters(paramLabel = "register") String register) throws InterruptedException {
            ArrayList<String[]> servers = getServerList(serverPorts);
            maxLabel = 0L;
            maxValue = 0L;
            int minAcks = (int) Math.ceil((servers.size() + 1) / 2);
            acks = 0;
            CountDownLatch latch = new CountDownLatch(servers.size());
            HashMap<String, ABDServiceGrpc.ABDServiceStub> stubMap = new HashMap<String, ABDServiceGrpc.ABDServiceStub>();
            for (String[] server : servers) {
                System.out.printf("Going to read %s from %s at %s\n", register, server[0], server[1]);
                var stub = getAsyncStub(server[0] + ":" + server[1]);
                stubMap.put(server[0]+":"+server[1], stub);

                stub.withDeadlineAfter(TIMEOUT, TimeUnit.SECONDS)
                        .read1(Grpc.Read1Request.newBuilder().setAddr(Long.valueOf(register)).build(),
                                new StreamObserver<Grpc.Read1Response>() {

                                    @Override
                                    public void onNext(Grpc.Read1Response resp) {
                                        System.out.printf(
                                                "Read1Response from server %s:%s :\nRC: %d\nLabel: %d\nValue: %d\n",
                                                server[0],
                                                server[1], resp.getRc(), resp.getLabel(), resp.getValue());
                                        if (resp.getLabel() > maxLabel) {
                                            maxLabel = resp.getLabel();
                                            maxValue = resp.getValue();
                                        }
                                        acks++;
                                    }

                                    @Override
                                    public void onError(Throwable t) {
                                        // TODO Auto-generated method stub
                                        System.out.printf("XXXFailed to get a Read1Response from %s:%s, %s\n",
                                                server[0],
                                                server[1],
                                                t.getMessage());
                                        latch.countDown();
                                    }

                                    @Override
                                    public void onCompleted() {
                                        // TODO Auto-generated method stub
                                        System.out.println("COMPLETE");
                                        latch.countDown();
                                    }

                                });
            }
            latch.await();
            if (acks < minAcks || maxLabel == 0L) {
                System.out.printf("Read1 FAILED!\n");
                return;
            }
            minAcks = (int) Math.ceil((servers.size() + 1) / 2);
            acks = 0;
            CountDownLatch latch2 = new CountDownLatch(servers.size());
            for (String[] server : servers) {
                System.out.printf("\nGoing to read2 %s from %s at %s\n", register, server[0], server[1]);
                //var stub = getAsyncStub(server[0] + ":" + server[1]);
                var stub = stubMap.get(server[0]+":"+server[1]);
                stub.withDeadlineAfter(TIMEOUT, TimeUnit.SECONDS).read2(Grpc.Read2Request.newBuilder()
                        .setAddr(Long.valueOf(register))
                        .setLabel(maxLabel)
                        .setValue(maxValue)
                        .build(), new StreamObserver<Grpc.Read2Response>() {

                            @Override
                            public void onNext(Grpc.Read2Response value) {
                                System.out.printf("\nRead2Response from server %s:%s received\n", server[0],
                                        server[1]);
                                acks++;
                            }

                            @Override
                            public void onError(Throwable t) {
                                System.out.printf("Failed to get a Read2Response from %s:%s, %s\n", server[0],
                                        server[1],
                                        t.getMessage());
                                latch2.countDown();

                            }

                            @Override
                            public void onCompleted() {
                                // TODO Auto-generated method stub
                                latch2.countDown();
                            }

                        });
            }
            latch2.await();
            if (acks >= minAcks) {
                System.out.printf("Read2 SUCCESS!\n");
                System.out.printf("%d(%d)\n", maxValue, maxLabel);
            } else {
                System.out.println("Read FAILED!\n");
            }

        }

        @Command
        public void write(@Parameters(paramLabel = "register") String register,
                @Parameters(paramLabel = "value") String value) throws InterruptedException {
            ArrayList<String[]> servers = getServerList(serverPorts);
            int minAcks = (int) Math.ceil((servers.size() + 1) / 2);
            acks = 0;
            CountDownLatch latch = new CountDownLatch(servers.size());
            long now = System.currentTimeMillis();
            for (String[] server : servers) {
                System.out.printf("Going to write %s to %s on %s\n", value, register, serverPorts);
                var stub = getAsyncStub(server[0] + ":" + server[1]);

                stub.withDeadlineAfter(TIMEOUT, TimeUnit.SECONDS)
                        .write(Grpc.WriteRequest.newBuilder().setAddr(Long.valueOf(register))
                                .setLabel(now).setValue(Long.valueOf(value)).build(),
                                new StreamObserver<Grpc.WriteResponse>() {
                                    @Override
                                    public void onCompleted() {
                                        latch.countDown();
                                    }

                                    @Override
                                    public void onNext(Grpc.WriteResponse value) {
                                        System.out.printf("WriteResponse from %s:%s received\n", server[0],
                                                server[1]);
                                        acks += 1;
                                    };

                                    @Override
                                    public void onError(Throwable t) {
                                        System.out.printf("Failed to get a WriteResponse from %s:%s, %s\n",
                                                server[0],
                                                server[1],
                                                t.getMessage());
                                        latch.countDown();
                                    }
                                });

            }
            latch.await();
            if (acks >= minAcks) {
                System.out.printf("Write SUCCESS!");
            }
        }

        @Command
        public void name() {
            var stub = getStub(serverPorts);
            System.out.println("This server belongs to " + stub.withDeadlineAfter(TIMEOUT, TimeUnit.SECONDS)
                    .name(Grpc.NameRequest.newBuilder().build()).getName());
        }

        @Command
        public void exit() {
            var stub = getStub(serverPorts);
            System.out.println("Shutting down server "
                    + stub.withDeadlineAfter(TIMEOUT, TimeUnit.SECONDS).exit(Grpc.ExitRequest.newBuilder().build()));
        }

        @Command
        public void enableRequest(@Parameters(paramLabel = "read1") Boolean read1,
                @Parameters(paramLabel = "read2") Boolean read2, @Parameters(paramLabel = "write") Boolean write) {
            var stub = getStub(serverPorts);
            System.out.println("Enabling requests " + stub.withDeadlineAfter(TIMEOUT, TimeUnit.SECONDS)
                    .enableRequests(Grpc.EnableRequest.newBuilder()
                            .setRead1(read1)
                            .setRead2(read2)
                            .setWrite(write)
                            .build()));
        }

        private static ABDServiceGrpc.ABDServiceBlockingStub getStub(String serverAddr) {
            var lastColon = serverAddr.lastIndexOf(':');
            var host = serverAddr.substring(0, lastColon);
            var port = Integer.parseInt(serverAddr.substring(lastColon + 1));
            var channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
            return ABDServiceGrpc.newBlockingStub(channel);
        }

        private static ABDServiceGrpc.ABDServiceStub getAsyncStub(String serverAddr) {
            var lastColon = serverAddr.lastIndexOf(':');
            var host = serverAddr.substring(0, lastColon);
            var port = Integer.parseInt(serverAddr.substring(lastColon + 1));
            var channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
            return ABDServiceGrpc.newStub(channel);
        }

        private static ArrayList<String[]> getServerList(String serverPorts) {
            ArrayList<String[]> res = new ArrayList<>();
            String[] servers = serverPorts.split(",");
            for (String server : servers) {
                res.add(server.split(":"));
            }
            return res;
        }
    }
}