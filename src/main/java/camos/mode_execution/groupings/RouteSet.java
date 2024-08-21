package camos.mode_execution.groupings;

import camos.mode_execution.Agent;
import camos.mode_execution.Requesttype;

import java.util.*;

public class RouteSet extends Grouping{
    double timeInMinutes;
    List<Agent> order;


    public RouteSet(List<Agent> members, Agent driver, double timeInMinutes, Requesttype isToWork) {
        this.agents = members;
        this.driver = driver;
        this.typeOfGrouping = isToWork;
        this.order = members;
        this.timeInMinutes = timeInMinutes;
    }

    public List<Agent> getMembers() {
        return agents;
    }

    public double getTimeInMinutes() {
        return timeInMinutes;
    }

    public void setTimeInMinutes(Double timeInMinutes) {
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

    @Override
    public int hashCode() {
        Set<Agent> uniqueSet = new HashSet<>(agents);
        uniqueSet.remove(driver);
        return Objects.hash(driver, typeOfGrouping, uniqueSet);
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Set<Agent> uniqueSet = new HashSet<>(this.agents);
        uniqueSet.remove(this.driver);
        Set<Agent> otherUniqueSet = new HashSet<>(((RouteSet) obj).agents);
        otherUniqueSet.remove(((RouteSet) obj).driver);
        return (this.typeOfGrouping.equals(((RouteSet) obj).typeOfGrouping) && this.driver.equals(((RouteSet) obj).driver) && uniqueSet.equals(otherUniqueSet));
    }
}
