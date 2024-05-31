package camos.mode_execution;

import camos.GeneralManager;
import camos.mode_execution.carmodels.Vehicle;

public class Agent {

    long id;
    boolean willingToUseAlternatives;
    Coordinate homePosition;
    Request request;
    Vehicle car;
    long willingToWalkInMeters;
    long willingToRideInMinutes;
    long timeIntervalInMinutes;


    public Agent(long id, Coordinate homePosition, Vehicle car, Request request) {
        this.id = id;
        this.homePosition = homePosition;
        this.car = car;
        this.request = request;
        this.timeIntervalInMinutes = GeneralManager.timeInterval;
        this.willingToWalkInMeters = GeneralManager.acceptedWalkingDistance;
        this.willingToUseAlternatives = true;
    }


    public Agent(){

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
}
