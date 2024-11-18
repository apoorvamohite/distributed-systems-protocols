package edu.sjsu.cs249.kafkaTable;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public class Util {
    public static String getXidKey(ClientXid xid){
        return xid.getClientid() + xid.getCounter();
    }

    public static ConsumerRecord<?,?> getLastItem(ConsumerRecords<?,?> records){
        var iter = records.iterator();
        var lastRecord = iter.next();
        while(iter.hasNext()){
            lastRecord = iter.next();
        }
        return lastRecord;
    }
}
