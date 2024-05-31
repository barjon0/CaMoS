package camos.mode_execution.groupings;

import camos.mode_execution.Agent;
import camos.mode_execution.Coordinate;
import camos.mode_execution.Requesttype;
import camos.mode_execution.carmodels.Vehicle;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Ride extends Grouping {

    long id;

    static long idcounter;

    List<Stop> stops;

    LocalDateTime startTime;

    LocalDateTime endTime;


    public Ride(){

    }

    public Ride(Coordinate startPosition, Coordinate endPosition, LocalDateTime startTime, LocalDateTime endTime, Vehicle vehicle, Agent driver, Requesttype typeOfRide, List<Agent> agents) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.startTime = startTime;
        this.endTime = endTime;
        this.vehicle = vehicle;
        this.driver = driver;
        this.typeOfGrouping = typeOfRide;
        this.agents = agents;
        this.stops = new ArrayList<>();
        this.id = ++idcounter;
    }


    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public List<Stop> getStops() {
        return stops;
    }

    public void setStops(List<Stop> stops) {
        this.stops = stops;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public static long getIdcounter() {
        return idcounter;
    }

    public static void setIdcounter(long idcounter) {
        Ride.idcounter = idcounter;
    }

}
