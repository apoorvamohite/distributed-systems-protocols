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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

public class LunchDataStorageHelper {
    public Map<Long, Lunch> lunchMap;
    private String filename;

    public LunchDataStorageHelper(String zookeeperClientAddr) {
        try {
            if (!Files.exists(Paths.get("server-data"))) {
                Files.createDirectory(Paths.get("server-data"));
            }
            filename = zookeeperClientAddr.substring(zookeeperClientAddr.indexOf(":") + 1) + ".txt";
            if (!Files.exists(Paths.get(
                    "server-data/" + filename))) {
                Files.createFile(Paths.get(
                        "server-data/" + filename));
                lunchMap = Collections.synchronizedMap(new LinkedHashMap<Long, Lunch>());
            } else {
                // Read from file
                try {
                    ObjectInputStream ois = new ObjectInputStream(
                            new FileInputStream(new File("server-data/" + filename)));

                    try {
                        lunchMap = (Map<Long, Lunch>)ois.readObject();
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
        System.out.println("Ok, I'm here");
        int ans = 0;
        int i = 0;
        for (Object l : lunchMap.values()) {
            Lunch lunch = (Lunch) l;
            if (lunch.leader) {
                ans = (i + 1);
            }
            i++;
        }
        System.out.println("Ok, I'm here, ans is" + ans);
        return ans == 0 ? Integer.MAX_VALUE : ans;
    }

    public void sendToFile() {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File("server-data/" + filename)));
            oos.writeObject(lunchMap);
            oos.flush();
            oos.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
