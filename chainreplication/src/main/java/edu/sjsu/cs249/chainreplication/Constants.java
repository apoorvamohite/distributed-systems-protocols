package edu.sjsu.cs249.chainreplication;

public class Constants {
    public static final String REPLICA_PREFIX = "/replica-";
    public static final String MY_NAME = "apoorva";

    public static enum REPLICA_STATE {
        HEAD, REPLICA, TAIL, ALL;
    }
}
