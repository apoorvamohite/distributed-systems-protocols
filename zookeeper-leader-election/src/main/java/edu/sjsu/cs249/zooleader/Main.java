package edu.sjsu.cs249.zooleader;

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
import java.util.Map;
import java.util.Map.Entry;
import java.util.List;
import java.util.Objects;

import static java.util.concurrent.TimeUnit.SECONDS;

public class Main {
    // TODO:
    // localhost hardcoded. What is grpcHostPort? only a number or host:number
    // What is lunch_path in command

    public static final int TIMEOUT = 3;

    public static void main(String[] args) {
        System.exit(new CommandLine(new Cli()).execute(args));
    }

    @Command(subcommands = { ClientCli.class })
    static class Cli implements Callable<Integer> {
        @Parameters(index = "0", arity="0", description = "Zookeeper client name")
        String zookeeperClientName;
        @Parameters(index = "1", arity="0", description = "Zookeeper client port number")
        int serverPort;
        @Parameters(index = "2", arity="0", description = "Zookeeper server address")
        String zookeeperServerAddr;
        @Parameters(index = "3", arity="0", description = "lunch znode path")
        String lunchPath;

        @Override
        public Integer call() throws Exception {
            Server server = ServerBuilder
                    .forPort(serverPort)
                    .addService(new ZooLunchServiceImpl(zookeeperServerAddr, zookeeperClientName, "localhost:"+serverPort, lunchPath)).build();

            server.start();
            server.awaitTermination();
            System.out.printf("Zookeeper listening on %s %d %s\n", zookeeperClientName, serverPort, zookeeperServerAddr);
            return 0;
        }
    }

    @Command(name = "client", mixinStandardHelpOptions = true, description = "start zoo client.")
    static class ClientCli {
        @Parameters(index = "0", description = "comma separated list of servers to use.")
        String serverPorts;

        @Command
        public void read(@Parameters(paramLabel = "register") String register) {
            System.out.println("Hello from client read cli");
        }

        @Command
        public void write(@Parameters(paramLabel = "register") String register,
                @Parameters(paramLabel = "value") String value) {
                    System.out.println("Hello from client write cli");
        }

        // private <R> Function<ABDServiceGrpc.ABDServiceBlockingStub, R> noe(Function<ABDServiceGrpc.ABDServiceBlockingStub, R> func) {
        //     return o -> {
        //         try {
        //             return func.apply(o);
        //         } catch (Exception e) {
        //             // debug("Skipping {0}", o.getChannel().authority());
        //         }
        //         return null;
        //     };
        // }

        // @Command
        // public void exit() {
        //     var stub = getStub(serverPorts);
        //     System.out.println("Shutting down server "
        //             + stub.withDeadlineAfter(TIMEOUT, TimeUnit.SECONDS).exit(Grpc.ExitRequest.newBuilder().build()));
        // }

        // private static ABDServiceGrpc.ABDServiceBlockingStub getStub(String serverAddr) {
        //     var lastColon = serverAddr.lastIndexOf(':');
        //     var host = serverAddr.substring(0, lastColon);
        //     var port = Integer.parseInt(serverAddr.substring(lastColon + 1));
        //     var channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        //     return ABDServiceGrpc.newBlockingStub(channel);
        // }

        // private static ArrayList<String[]> getServerList(String serverPorts) {
        //     ArrayList<String[]> res = new ArrayList<>();
        //     String[] servers = serverPorts.split(",");
        //     for (String server : servers) {
        //         res.add(server.split(":"));
        //     }
        //     return res;
        // }
    }
}