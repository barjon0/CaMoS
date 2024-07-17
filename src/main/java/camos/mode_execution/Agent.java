package camos.mode_execution;

import camos.GeneralManager;
import camos.mode_execution.carmodels.Vehicle;
import camos.mode_execution.groupings.Grouping;
import camos.mode_execution.groupings.Match;
import camos.mode_execution.mobilitymodels.modehelpers.CommonFunctionHelper;
import com.graphhopper.GHRequest;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Agent {
    long id;
    boolean willingToUseAlternatives;
    Coordinate homePosition;
    Request request;
    Vehicle car;
    long willingToWalkInMeters;
    long willingToRideInMinutes;
    long timeIntervalInMinutes;
    double distanceToTarget;
    Grouping teamOfAgentTo;
    Grouping teamOfAgentFrom;
    double maxTravelTimeInMinutes;


    public Agent(long id, Coordinate homePosition, Vehicle car) {
        this.id = id;
        this.homePosition = homePosition;
        this.car = car;
        this.timeIntervalInMinutes = GeneralManager.timeInterval;
        this.willingToWalkInMeters = GeneralManager.acceptedWalkingDistance;
        this.willingToUseAlternatives = true;
        this.teamOfAgentTo = null;
        this.teamOfAgentFrom = null;
    }



    public double getMaxTravelTimeInMinutes() {
        return maxTravelTimeInMinutes;
    }

    public Grouping getTeamOfAgentTo() {
        return teamOfAgentTo;
    }

    public Grouping getTeamOfAgentFrom() {return teamOfAgentFrom;}

    public void setTeamOfAgentTo(Grouping teamOfAgent) {
        this.teamOfAgentTo = teamOfAgent;
    }

    public void setTeamOfAgentFrom(Grouping teamOfAgent) {
        this.teamOfAgentFrom = teamOfAgent;
    }

    public double getDistanceToTarget() {
        return distanceToTarget;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Coordinate getHomePosition() {
        return homePosition;
    }

    public void setHomePosition(Coordinate homePosition) {
        this.homePosition = homePosition;
    }

    public Request getRequest() {
        return request;
    }

    public void setRequest(Request request) {

        this.request = request;
        distanceToTarget = request.dropOffPosition.computeDistance(this.homePosition);
        double mintraveltimeMinutes = CommonFunctionHelper.computeTimeBetweenPoints(homePosition, this.request.dropOffPosition);
        this.maxTravelTimeInMinutes = (Math.log(mintraveltimeMinutes + 1) / Math.log(1.2)) + mintraveltimeMinutes;
    }

    public Vehicle getCar() {
        return car;
    }

    public void setCar(Vehicle car) {
        this.car = car;
    }

    public long getWillingToWalkInMeters() {
        return willingToWalkInMeters;
    }

    public void setWillingToWalkInMeters(long willingToWalkInMeters) {
        this.willingToWalkInMeters = willingToWalkInMeters;
    }

    public long getWillingToRideInMinutes() {
        return willingToRideInMinutes;
    }

    public void setWillingToRideInMinutes(long willingToRideInMinutes) {
        this.willingToRideInMinutes = willingToRideInMinutes;
    }

    public long getTimeIntervalInMinutes() {
        return timeIntervalInMinutes;
    }

    public void setTimeIntervalInMinutes(long timeIntervalInMinutes) {
        this.timeIntervalInMinutes = timeIntervalInMinutes;
    }

    public boolean isWillingToUseAlternatives() {
        return willingToUseAlternatives;
    }

    public void setWillingToUseAlternatives(boolean willingToUseAlternatives) {
        this.willingToUseAlternatives = willingToUseAlternatives;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Agent agent = (Agent) o;
        return id == agent.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
