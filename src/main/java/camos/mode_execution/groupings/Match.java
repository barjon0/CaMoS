package camos.mode_execution.groupings;

import camos.GeneralManager;
import camos.mode_execution.*;
import camos.mode_execution.carmodels.Vehicle;
import camos.mode_execution.mobilitymodels.MobilityType;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

public class Match extends Grouping implements Comparable{

    long id;
    static long idCounter = 0;
    Map<Coordinate,String> differentStops;
    MobilityType mobilityType;
    LocalDateTime timeIntervalStart;
    LocalDateTime timeIntervalEnd;


    public Match(List<Agent> agents, Map<Coordinate,String> differentStops, Agent driver, Vehicle vehicle, Coordinate startPosition, Coordinate endPosition, Requesttype typeOfGrouping, LocalDateTime timeIntervalStart, LocalDateTime timeIntervalEnd, MobilityType mobilityType) {
        this.agents = agents;
        this.differentStops = differentStops;
        this.driver = driver;
        this.vehicle = vehicle;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.typeOfGrouping = typeOfGrouping;
        this.timeIntervalStart = timeIntervalStart;
        this.timeIntervalEnd = timeIntervalEnd;
        this.mobilityType = mobilityType;
        this.id = ++idCounter;
    }

    public Match(){

    }

    public LocalDateTime getTimeIntervalStart() {
        return timeIntervalStart;
    }

    public void setTimeIntervalStart(LocalDateTime timeIntervalStart) {
        this.timeIntervalStart = timeIntervalStart;
    }

    public LocalDateTime getTimeIntervalEnd() {
        return timeIntervalEnd;
    }

    public void setTimeIntervalEnd(LocalDateTime timeIntervalEnd) {
        this.timeIntervalEnd = timeIntervalEnd;
    }

    public Map<Coordinate,String> getDifferentStops() {
        return differentStops;
    }

    public void setDifferentStops(Map<Coordinate,String> differentStops) {
        this.differentStops = differentStops;
    }

    public MobilityType getMobilityType() {
        return mobilityType;
    }

    public void setMobilityType(MobilityType mobilityType) {
        this.mobilityType = mobilityType;
    }


    //TODO
    @Override
    public int compareTo(@NotNull Object o) {
        if(GeneralManager.compareRequest!=null){
            Request request = GeneralManager.compareRequest;
            Match match = (Match) o;
            if(Match.similarToRequest(request,match)<Match.similarToRequest(request,this)){
                return -1;
            }else if(Match.similarToRequest(request,match)>Match.similarToRequest(request,this)){
                return 1;
            }
            return 0;
        }
        return 0;
    }

    //TODO
    public static double similarToRequest(Request request, Match match){
        double value = 0;
        Coordinate position;
        LocalDateTime requestIntervalStart;
        LocalDateTime requestIntervalEnd;
        if(match.getTypeOfGrouping()==Requesttype.DRIVETOUNI){
            position = match.getStartPosition();
            requestIntervalStart = request.getArrivalIntervalStart();
            requestIntervalEnd = request.getArrivalIntervalEnd();
        }else{
            position = match.getEndPosition();
            requestIntervalStart = request.getDepartureIntervalStart();
            requestIntervalEnd = request.getDepartureIntervalEnd();
        }
        value = value - Math.abs(position.getLongitude()-request.getHomePosition().getLongitude());
        value = value - Math.abs(position.getLatitude()-request.getHomePosition().getLatitude());
        value = value - (double) Math.abs(ChronoUnit.HOURS.between(match.getTimeIntervalStart(), requestIntervalStart)) /100;
        value = value - (double) Math.abs(ChronoUnit.HOURS.between(match.getTimeIntervalEnd(), requestIntervalEnd)) /100;
        return value;
    }


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public static long getIdCounter() {
        return idCounter;
    }

    public static void setIdCounter(long idCounter) {
        Match.idCounter = idCounter;
    }
}
