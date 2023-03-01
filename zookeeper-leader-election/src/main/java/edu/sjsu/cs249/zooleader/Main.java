package edu.sjsu.cs249.zooleader;

import io.grpc.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

import edu.sjsu.cs249.zooleader.Grpc.GetLunchRequest;
import edu.sjsu.cs249.zooleader.Grpc.GoingToLunchRequest;
import edu.sjsu.cs249.zooleader.Grpc.LunchesAttendedRequest;
import edu.sjsu.cs249.zooleader.Grpc.SkipRequest;

public class Main {
    // TODO:
    // localhost hardcoded. What is grpcHostPort? only a number or host:number
    // When node starts up when readyforlunch already created, does it signup for
    // lunch?
    // What is SkipRequest?

    public static void main(String[] args) {
        System.exit(new CommandLine(new Cli()).execute(args));
    }

    @Command(subcommands = { ClientCli.class })
    static class Cli implements Callable<Integer> {
        @Parameters(index = "0", arity = "0", description = "Zookeeper client name")
        String zookeeperClientName;
        @Parameters(index = "1", arity = "0", description = "Zookeeper client port number")
        int serverPort;
        @Parameters(index = "2", arity = "0", description = "Zookeeper server address")
        String zookeeperServerAddr;
        @Parameters(index = "3", arity = "0", description = "lunch znode path")
        String lunchPath;

        @Override
        public Integer call() throws Exception {
            Server server = ServerBuilder
                    .forPort(serverPort)
                    .addService(new ZooLunchServiceImpl(zookeeperServerAddr, zookeeperClientName,
                            "localhost:" + serverPort, lunchPath))
                    .build();

            System.out.printf("Zookeeper listening on %s %d %s\n", zookeeperClientName, serverPort,
                    zookeeperServerAddr);
            server.start();
            server.awaitTermination();
            return 0;
        }
    }

    @Command(name = "client", mixinStandardHelpOptions = true, description = "start zoo client.")
    static class ClientCli {
        @Parameters(index = "0", description = "comma separated list of servers to use.")
        String serverPorts;

        @Command
        public void checkLunch() {
            var stub = getStub(serverPorts);
            System.out.println("response" + stub.goingToLunch(GoingToLunchRequest.newBuilder().build()));
        }

        @Command
        public void skipLunch() {
            var stub = getStub(serverPorts);
            System.out.println("response" + stub.skipLunch(SkipRequest.newBuilder().build()));
        }

        @Command
        public void lunchesAttended() {
            var stub = getStub(serverPorts);
            var resp = stub.lunchesAttended(LunchesAttendedRequest.newBuilder().build());
            System.out.println("response1" + resp);
            System.out.println("response2" + stub.getLunch(GetLunchRequest.newBuilder().setZxid(resp.getZxids(0)).build()));
        }

        private static ZooLunchGrpc.ZooLunchBlockingStub getStub(String serverAddr) {
            var lastColon = serverAddr.lastIndexOf(':');
            var host = serverAddr.substring(0, lastColon);
            var port = Integer.parseInt(serverAddr.substring(lastColon + 1));
            var channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
            return ZooLunchGrpc.newBlockingStub(channel);
        }
    }
}