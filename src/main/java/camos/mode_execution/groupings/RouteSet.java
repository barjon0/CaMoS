package camos.mode_execution.groupings;

import camos.mode_execution.Agent;
import camos.mode_execution.Requesttype;

import java.util.*;

public class RouteSet extends Grouping{
    long timeInMinutes;
    List<Agent> order;


    public RouteSet(List<Agent> members, Agent driver, long timeInMinutes, Requesttype isToWork) {
        this.agents = members;
        this.driver = driver;
        this.typeOfGrouping = isToWork;
        this.order = members;
        this.timeInMinutes = timeInMinutes;

    }

    public List<Agent> getMembers() {
        return agents;
    }

    public long getTimeInMinutes() {
        return timeInMinutes;
    }

    public void setTimeInMinutes(long timeInMinutes) {
        this.timeInMinutes = timeInMinutes;
    }

    public List<Agent> getOrder() {
        return order;
    }

    public void setOrder(List<Agent> order) {
        if (new HashSet<>(agents).containsAll(order)) {
            this.order = order;
        } else {
            throw new IllegalArgumentException("Tried to change order to members not in Set");
        }

    }
}
