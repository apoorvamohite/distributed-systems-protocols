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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import edu.sjsu.cs249.abd.Grpc.*;
import java.util.Map;
import java.util.Map.Entry;
import java.util.List;
import java.util.Objects;

import static java.util.concurrent.TimeUnit.SECONDS;

public class Main {

    // TODO:
    // Synchronized
    // Understand Concurrent write
    // n+1/2 acks
    // Multi threaded broadcast?
    // What if I don't get (n+1)/2 acks for a write? How to revert the written values on the minority processes? -> (n+1)/2 failure assumption
    // Why single writer?
    public static final int TIMEOUT = 3;

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
            int minAcks = (int) Math.ceil((servers.size()+1.0)/2.0);
            List<ABDServiceGrpc.ABDServiceBlockingStub> stubs = new ArrayList<>();
            for (String[] server : servers) {
                stubs.add(getStub(server[0] + ":" + server[1]));
            }
            var read1 = Read1Request.newBuilder().setAddr(Long.valueOf(register)).build();
            var results = stubs.stream().parallel().map(noe(s -> Map.entry(s, s.withDeadlineAfter(TIMEOUT, SECONDS).read1(read1)))).filter(Objects::nonNull).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
            if(results.size() < minAcks){
                System.out.println("failed");
                return;
            }
            long maxLabel = 0L;
            long maxValue = 0L;
            for(Read1Response resp : results.values()){
                if (resp.getLabel() > maxLabel) {
                    maxLabel = resp.getLabel();
                    maxValue = resp.getValue();
                }
            }
            if(maxLabel == 0L){
                System.out.println("failed");
                return;
            }

            var read2 = Read2Request.newBuilder().setAddr(Long.valueOf(register)).setLabel(maxLabel).setValue(maxValue).build();
            var results2 = stubs.stream().parallel().map(noe(s -> Map.entry(s, s.withDeadlineAfter(TIMEOUT, SECONDS).read2(read2)))).filter(Objects::nonNull).collect(Collectors.toMap(Entry::getKey, Entry::getValue));

            if(results2.size() >= minAcks){
                // System.out.printf("Read2 SUCCESS!");
                System.out.printf("%d(%d)", maxValue, maxLabel);
            } else {
                System.out.println("failed");
            }

        }

        @Command
        public void write(@Parameters(paramLabel = "register") String register,
                @Parameters(paramLabel = "value") String value) {
            ArrayList<String[]> servers = getServerList(serverPorts);
            int minAcks = (int) Math.ceil((servers.size()+1.0)/2.0);
            List<ABDServiceGrpc.ABDServiceBlockingStub> stubs = new ArrayList<>();
            for (String[] server : servers) {
                stubs.add(getStub(server[0] + ":" + server[1]));
            }
            var wreq = WriteRequest.newBuilder().setAddr(Long.valueOf(register)).setLabel(System.currentTimeMillis()).setValue(Long.valueOf(value)).build();
            var results = stubs.stream().parallel().map(noe(s -> Map.entry(s, s.withDeadlineAfter(TIMEOUT, SECONDS).write(wreq)))).filter(Objects::nonNull).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
            if(results.size() >= minAcks){
                System.out.printf("success");
            } else {
                System.out.printf("failure");
            }
        }

        private <R> Function<ABDServiceGrpc.ABDServiceBlockingStub, R> noe(Function<ABDServiceGrpc.ABDServiceBlockingStub, R> func) {
            return o -> {
                try {
                    return func.apply(o);
                } catch (Exception e) {
                    // debug("Skipping {0}", o.getChannel().authority());
                }
                return null;
            };
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