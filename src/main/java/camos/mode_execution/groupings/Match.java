package camos.mode_execution.groupings;

import camos.GeneralManager;
import camos.mode_execution.*;
import camos.mode_execution.carmodels.Vehicle;
import camos.mode_execution.mobilitymodels.MobilityType;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class Match extends Grouping implements Comparable{

    long id;
    static long idCounter = 0;
    Map<Coordinate,String> differentStops;
    MobilityType mobilityType;
    LocalDateTime timeIntervalStart;
    LocalDateTime timeIntervalEnd;

    boolean isWayToWork;
    Agent selectedDriver;

    Match partner;
    int maxSeats;
    Coordinate centroid;

    List<Agent> possDrivers;

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

    public Match(List<Agent> agents, boolean isWayToWork) {
        this.agents = agents;
        this.isWayToWork = isWayToWork;
        if (isWayToWork) {
            agents.forEach(a -> a.setTeamOfAgentTo(this));
        } else {
            agents.forEach(a -> a.setTeamOfAgentFrom(this));
        }
        Optional<Integer> seats = agents.stream().map(a -> a.getCar().getSeatCount()).max(Integer::compareTo);
        seats.ifPresent(integer -> {
            this.maxSeats = Math.max(maxSeats, integer);
        });
        this.id = ++idCounter;
        computeCenter();
        this.partner = null;
        this.possDrivers = new ArrayList<>();
        if(agents.size() == 1) {
            possDrivers.add(agents.get(0));
        }
    }

    public void setPossDrivers(List<Agent> possDrivers) {
        this.possDrivers = possDrivers;
    }
    public void addPossDriver(Agent d) {
        possDrivers.add(d);
    }

    public int teamCount() {
        return agents.size();
    }

    public int maxSeats() {
        return maxSeats;
    }

    public void removeFromTeam(List<Agent> agentList) {
        this.agents.removeAll(agentList);
        computeCenter();
        Optional<Integer> seats = this.agents.stream().map(a -> a.getCar().getSeatCount()).max(Integer::compareTo);
        seats.ifPresent(Integer -> this.maxSeats = Integer);
    }

    public void addToTeam(List<Agent> agentList){
        this.agents.addAll(agentList);
        computeCenter();
        if (isWayToWork) {
            agentList.forEach(a -> a.setTeamOfAgentTo(this));
        }  else {
            agentList.forEach(a -> a.setTeamOfAgentFrom(this));
        }

        Optional<Integer> seats = agentList.stream().map(a -> a.getCar().getSeatCount()).max(Integer::compareTo);
        seats.ifPresent(integer -> this.maxSeats = Math.max(maxSeats, integer));
    }

    public List<Agent> getPossDrivers() {
        return possDrivers;
    }

    public Match getPartner() {
        return partner;
    }

    public void setPartner(Match partner) {
        this.partner = partner;
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

    public void computeCenter() {
        centroid = Coordinate.computeCenter(agents.stream().map(Agent::getHomePosition).toList());
    }

    public Agent getSelectedDriver() {
        return selectedDriver;
    }

    public void setSelectedDriver(Agent selectedDriver) {
        this.selectedDriver = selectedDriver;
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

    public Coordinate getCentroid() {
        return centroid;
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
