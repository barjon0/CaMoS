package camos.mode_execution;

import camos.GeneralManager;
import camos.mode_execution.carmodels.Vehicle;
import camos.mode_execution.groupings.Match;

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
    Match teamOfAgentTo;
    Match teamOfAgentFrom;
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
        this.maxTravelTimeInMinutes = 60;
    }

    public double getMaxTravelTimeInMinutes() {
        return maxTravelTimeInMinutes;
    }

    public Match getTeamOfAgentTo() {
        return teamOfAgentTo;
    }

    public Match getTeamOfAgentFrom() {return teamOfAgentFrom;}

    public void setTeamOfAgentTo(Match teamOfAgent) {
        this.teamOfAgentTo = teamOfAgent;
    }

    public void setTeamOfAgentFrom(Match teamOfAgent) {
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
    }

    public Vehicle getCar() {
        return car;
    }

    public void setCar(Vehicle car) {
        this.car = car;
    }

    public double clusterFunc() {
        return Coordinate.distFunc(distanceToTarget);
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
}
