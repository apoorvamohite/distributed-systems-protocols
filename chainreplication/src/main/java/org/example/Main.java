package org.example;

import edu.sjsu.cs249.chain.*;
import io.grpc.*;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

public class Main {

    // TODO
    // Use sync()
    // ChainDebugRequest
    // Handle newSuccessorResponse RC correctly

    public static void main(String[] args) {
        System.exit(new CommandLine(new Cli()).execute(args));
    }

    @Command(subcommands = { ClientCli.class })
    static class Cli implements Callable<Integer> {
        @Parameters(index = "0", arity = "0", description = "My Name")
        String myName;
        @Parameters(index = "1", arity = "0", description = "gRPC host:port")
        String grpcHostPort;
        @Parameters(index = "2", arity = "0", description = "Zookeeper host:port")
        String zookeeperHostPort;
        @Parameters(index = "3", arity = "0", description = "Control path")
        String controlPath;

        @Override
        public Integer call() throws Exception {
            ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();
            zookeeperHelper.init(zookeeperHostPort, controlPath, grpcHostPort, myName);
            Server server = ServerBuilder
                    .forPort(Integer.parseInt(grpcHostPort.split(":")[1]))
                    .addService(new HeadChainReplicaImpl())
                    .addService(new ReplicaImpl())
                    .addService(new TailChainReplicaImpl())
                    .addService(new ChainDebugImpl())
                    .build();

            System.out.printf("Replica listening on %s, Control path: %s\n", grpcHostPort, controlPath);
            server.start();
            server.awaitTermination();
            return 0;
        }
    }

    @Command(name = "client", mixinStandardHelpOptions = true, description = "start zoo client.")
    static class ClientCli {
        @Parameters(index = "0", description = "zookeeper address")
        String serverPorts;

        @Parameters(index = "1", description = "control path")
        String controlPath;

        @Command
        public void write(@Parameters(paramLabel = "key") String key,
                          @Parameters(paramLabel = "inc value") int incValue) {
            String headAddr = null;
            String tailAddr = null;
            try {
                ZooKeeper zooKeeper = new ZooKeeper(serverPorts, 8000, (watchEvent) -> {
                    System.out.println("Init default watcher");
                });
                List<String> children = zooKeeper.getChildren(controlPath, false);
                Collections.sort(children);
                headAddr = (new String(zooKeeper.getData(controlPath + "/" + children.get(0), false, null))).split("\n")[0];
//                tailAddr = (new String(zooKeeper.getData(controlPath + "/" + children.get(children.size() - 1), false, null))).split("\n")[0];
            } catch (KeeperException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println("head: " + headAddr + " tail: " + tailAddr);
            var channel = ManagedChannelBuilder.forTarget(headAddr).usePlaintext().build();
            var stub = HeadChainReplicaGrpc.newBlockingStub(channel);
            IncRequest incRequest = IncRequest.newBuilder()
                    .setKey(key)
                    .setIncValue(incValue)
                    .build();
            var incResponse = stub.increment(incRequest);
            System.out.println("Got HeadResponse from head, rc: " + incResponse.getRc());
        }

        @Command
        public void read(@Parameters(paramLabel = "key") String key) {
            String headAddr = null;
            String tailAddr = null;
            try {
                ZooKeeper zooKeeper = new ZooKeeper(serverPorts, 8000, (watchEvent) -> {
                    System.out.println("Init default watcher");
                });
                List<String> children = zooKeeper.getChildren(controlPath, false);
                Collections.sort(children);
//                headAddr = (new String(zooKeeper.getData(controlPath + "/" + children.get(0), false, null))).split("\n")[0];
                tailAddr = (new String(zooKeeper.getData(controlPath + "/" + children.get(children.size() - 1), false, null))).split("\n")[0];
            } catch (KeeperException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            var channel = ManagedChannelBuilder.forTarget(tailAddr).usePlaintext().build();
            var stub = TailChainReplicaGrpc.newBlockingStub(channel);
            GetRequest getRequest = GetRequest.newBuilder()
                    .setKey(key)
                    .build();
            var getResponse = stub.get(getRequest);
            System.out.println("Got GetResponse from head " + getResponse.toString());
        }

        @Command
        public void debug(@Parameters(paramLabel = "key") String address) {

            var channel = ManagedChannelBuilder.forTarget(address).usePlaintext().build();
            var stub = ChainDebugGrpc.newBlockingStub(channel);
            ChainDebugRequest cdRequest = ChainDebugRequest.newBuilder()
                    .build();
            var cdResponse = stub.debug(cdRequest);
            System.out.println("Got ChainDebugResponse from successor " + cdResponse.toString());
        }
    }
}