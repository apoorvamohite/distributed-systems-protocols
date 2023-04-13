package edu.sjsu.cs249.chainreplication;

public class TableEntry {
    private Integer value = 0;
    private int xid = -1;

    public Integer getValue() {
        return value;
    }

    // May need to be synchronized
    public void setXid(int xid) {
        this.xid = Math.max(xid, this.xid);
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public int getXid() {
        return xid;
    }

    public TableEntry(Integer value) {
        this.value = value;
    }
}
