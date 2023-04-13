package edu.sjsu.cs249.chainreplication;

public class Util {
    public static String logState(String optional) {
        ZookeeperHelper zookeeperHelper = ZookeeperHelper.getInstance();
        String log = "\n"+optional+"**********************" +
                "\nZnode SEQUENCE NUMBER: " + zookeeperHelper.getZnodeSequence() +
                "\nCurrent REPLICA_STATE: " + zookeeperHelper.getCurrentState() +
                "\nHISTORY: \n" + zookeeperHelper.getHistory() +
                "\nSENT: \n" + zookeeperHelper.getSentList() +
                "\nHASHTABLE: \n" + zookeeperHelper.getHashtable() +
                "\nLAST XID: " + zookeeperHelper.getLastXid() +
                "\nLAST ACK: " + zookeeperHelper.getLastAck() +
                "\nPREDECESSOR: " + zookeeperHelper.getPredecessor() +
                "\nSUCCESSOR: " + zookeeperHelper.getSuccessor() +
                "\n**********************";
        return log;
    }
}
