package edu.sjsu.cs249.zooleader;

import java.io.Serializable;
import java.util.List;

public class Lunch implements Serializable {
    public boolean leader;
    public String leaderName;
    public String restaurant;
    public List<String> attendees;

    public Lunch(boolean leader, String leaderName, String restaurant, List<String> attendees) {
        this.leader = leader;
        this.leaderName = leaderName;
        this.restaurant = restaurant;
        this.attendees = attendees;
    }

    @Override
    public String toString() {
        return "Lunch [leader=" + leader + ", leaderName=" + leaderName + ", restaurant=" + restaurant + ", attendees="
                + attendees + "]";
    }
}
