package edu.sjsu.cs249.chainreplication;

import edu.sjsu.cs249.chain.IncRequest;
import edu.sjsu.cs249.chain.UpdateRequest;

import java.util.List;

public class HistoryItem {
    public void setXid(int xid) {
        this.xid = xid;
    }

    private int xid;

    @Override
    public String toString() {
        return "HistoryItem{" +
                "xid=" + xid +
                ", incRequest=" + incRequest +
                ", updateRequest=" + updateRequest +
                '}';
    }

    private IncRequest incRequest;
    private UpdateRequest updateRequest;

    public HistoryItem(IncRequest request) {
        this.incRequest = request;
    }

    public HistoryItem(UpdateRequest request) {
        this.updateRequest = request;
        this.xid = request.getXid();
    }

    public int getXid() {
        return xid;
    }

    public IncRequest getIncRequest() {
        return incRequest;
    }

    public UpdateRequest getUpdateRequest() {
        return updateRequest;
    }
}
