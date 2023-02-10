package edu.sjsu.cs249.abd;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.grpc.stub.StreamObservers;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import java.util.ArrayList;

public class Main {

    // TODO:
    // Synchronized
    // Understand Concurrent write
    // n+1/2 acks
    // Multi threaded broadcast?
    // What if I don't get (n+1)/2 acks for a write? How to revert the written values on the minority processes? -> (n+1)/2 failure assumption
    // Why single writer?
    public static final int TIMEOUT = 10;

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
        public void read(@Parameters(paramLabel = "register") String register) {
            ArrayList<String[]> servers = getServerList(serverPorts);
            Long maxLabel = 0L;
            Long maxValue = 0L;
            int minAcks = (int) Math.ceil((servers.size()+1)/2);
            int acks = 0;
            for (String[] server : servers) {
                System.out.printf("Going to read %s from %s at %s\n", register, server[0], server[1]);
                var stub = getStub(server[0] + ":" + server[1]);

                try {
                    var resp = stub.withDeadlineAfter(TIMEOUT, TimeUnit.SECONDS)
                            .read1(Grpc.Read1Request.newBuilder().setAddr(Long.valueOf(register)).build());
                    System.out.printf("Read1Response from server %s:%s :\nRC: %d\nLabel: %d\nValue: %d\n", server[0],
                            server[1], resp.getRc(), resp.getLabel(), resp.getValue());
                    if (resp.getLabel() > maxLabel) {
                        maxLabel = resp.getLabel();
                        maxValue = resp.getValue();
                    }
                    acks++;
                } catch (StatusRuntimeException sre) {
                    System.out.printf("Failed to get a Read1Response from %s:%s, %s", server[0], server[1],
                            sre.getMessage());
                }
            }
            if(acks < minAcks || maxLabel == 0L){
                System.out.printf("Read1 FAILED!");
                return;
            }
            minAcks = (int) Math.ceil((servers.size()+1)/2);
            acks = 0;
            for (String[] server : servers) {
                System.out.printf("\nGoing to read2 %s from %s at %s\n", register, server[0], server[1]);
                var stub = getStub(server[0] + ":" + server[1]);
                try {
                    var resp = stub.withDeadlineAfter(TIMEOUT, TimeUnit.SECONDS).read2(Grpc.Read2Request.newBuilder()
                            .setAddr(Long.valueOf(register))
                            .setLabel(maxLabel)
                            .setValue(maxValue)
                            .build());
                    System.out.printf("\nRead2Response from server %s:%s received", server[0], server[1]);
                    acks++;
                } catch (StatusRuntimeException sre) {
                    System.out.printf("Failed to get a Read2Response from %s:%s, %s", server[0], server[1],
                            sre.getMessage());
                }
            }
            if(acks >= minAcks){
                System.out.printf("Read2 SUCCESS!");
                System.out.printf("%d(%d)", maxValue, maxLabel);
            } else {
                System.out.println("Read FAILED!");
            }

        }

        @Command
        public void write(@Parameters(paramLabel = "register") String register,
                @Parameters(paramLabel = "value") String value) {
            ArrayList<String[]> servers = getServerList(serverPorts);
            int minAcks = (int) Math.ceil((servers.size()+1)/2);
            int acks = 0;
            for (String[] server : servers) {
                System.out.printf("Going to write %s to %s on %s\n", value, register, serverPorts);
                var stub = getStub(server[0] + ":" + server[1]);
                try {
                    var resp = stub.withDeadlineAfter(TIMEOUT, TimeUnit.SECONDS)
                            .write(Grpc.WriteRequest.newBuilder().setAddr(Long.valueOf(register))
                                    .setLabel(System.currentTimeMillis()).setValue(Long.valueOf(value)).build());
                    System.out.printf("WriteResponse from %s:%s received", server[0], server[1]);
                    acks++;
                } catch (StatusRuntimeException sre) {
                    System.out.printf("Failed to get a WriteResponse from %s:%s, %s", server[0], server[1],
                            sre.getMessage());
                }
            }
            if(acks >= minAcks){
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