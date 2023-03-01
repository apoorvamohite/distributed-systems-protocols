package edu.sjsu.cs249.zooleader;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class LunchDataStorageHelper {
    public Map<Long, Lunch> lunchMap;
    private String filename;

    public LunchDataStorageHelper(String zookeeperClientAddr) {
        try {
            if (!Files.exists(Paths.get(ZooLunchConstants.DATA_DIR))) {
                Files.createDirectory(Paths.get(ZooLunchConstants.DATA_DIR));
            }
            filename = zookeeperClientAddr.substring(zookeeperClientAddr.indexOf(":") + 1) + ".txt";
            if (!Files.exists(Paths.get(
                    ZooLunchConstants.DATA_DIR + "/" + filename))) {
                Files.createFile(Paths.get(
                        ZooLunchConstants.DATA_DIR + "/" + filename));
                lunchMap = Collections.synchronizedMap(new LinkedHashMap<Long, Lunch>());
            } else {
                // Read from file
                try {
                    ObjectInputStream ois = new ObjectInputStream(
                            new FileInputStream(new File(ZooLunchConstants.DATA_DIR + "/" + filename)));

                    try {
                        lunchMap = (Map<Long, Lunch>) ois.readObject();
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        lunchMap = Collections.synchronizedMap(new LinkedHashMap<Long, Lunch>());
                        e.printStackTrace();
                    }
                    ois.close();
                } catch (EOFException e) {
                    lunchMap = Collections.synchronizedMap(new LinkedHashMap<Long, Lunch>());
                }
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public int getLastLeadershipIndex() {
        int ans = 0;
        int i = 0;
        for (Object l : lunchMap.values()) {
            Lunch lunch = (Lunch) l;
            if (lunch.leader) {
                ans = (i + 1);
            }
            i++;
        }
        return ans == 0 ? Integer.MAX_VALUE : ans;
    }

    public void sendToFile() {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(new File(ZooLunchConstants.DATA_DIR + "/" + filename)));
            oos.writeObject(lunchMap);
            oos.flush();
            oos.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public Lunch getLastLunch() {
        Lunch lastLunch = null;
        for (Object l : lunchMap.values()) {
            lastLunch = (Lunch) l;
        }
        return lastLunch;
    }
}
