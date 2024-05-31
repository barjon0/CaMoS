package camos.mode_execution;

import camos.GeneralManager;
import camos.mode_execution.groupings.Match;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;

public class Request implements Comparable{

    long id;
    Agent agent;
    Requesttype requesttype;
    LocalDateTime requestTime;
    LocalDateTime favoredArrivalTime;
    LocalDateTime arrivalIntervalStart;
    LocalDateTime arrivalIntervalEnd;
    LocalDateTime favoredDepartureTime;
    LocalDateTime departureIntervalStart;
    LocalDateTime departureIntervalEnd;
    String homePLZ;
    String uniPLZ;
    Coordinate homePosition;
    Coordinate dropOffPosition;


    public Request(Agent agent, Requesttype requesttype, String homePLZ, String uniPLZ, Coordinate homePosition, Coordinate dropOffPosition) {
        this.agent = agent;
        this.requesttype = requesttype;
        this.homePLZ = homePLZ;
        this.uniPLZ = uniPLZ;
        this.homePosition = homePosition;
        this.dropOffPosition = dropOffPosition;
    }


    public Request(Request request) {
        this.agent = request.agent;
        this.requesttype = request.requesttype;
        this.requestTime = request.requestTime;
        this.favoredArrivalTime = request.favoredArrivalTime;
        this.arrivalIntervalStart = request.getArrivalIntervalStart();
        this.arrivalIntervalEnd = request.getArrivalIntervalEnd();
        this.favoredDepartureTime = request.favoredDepartureTime;
        this.departureIntervalStart = request.getDepartureIntervalStart();
        this.departureIntervalEnd = request.getDepartureIntervalEnd();
        this.homePLZ = request.homePLZ;
        this.uniPLZ = request.uniPLZ;
        this.homePosition = request.homePosition;
        this.dropOffPosition = request.dropOffPosition;
    }

    public Request() {}

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public Requesttype getRequesttype() {
        return requesttype;
    }

    public void setRequesttype(Requesttype requesttype) {
        this.requesttype = requesttype;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
    }

    public LocalDateTime getFavoredArrivalTime() {
        return favoredArrivalTime;
    }

    public void setFavoredArrivalTime(LocalDateTime favoredArrivalTime) {
        this.favoredArrivalTime = favoredArrivalTime;
    }

    public LocalDateTime getFavoredDepartureTime() {
        return favoredDepartureTime;
    }

    public void setFavoredDepartureTime(LocalDateTime favoredDepartureTime) {
        this.favoredDepartureTime = favoredDepartureTime;
    }

    public String getHomePLZ() {
        return homePLZ;
    }

    public void setHomePLZ(String homePLZ) {
        this.homePLZ = homePLZ;
    }

    public String getUniPLZ() {
        return uniPLZ;
    }

    public void setUniPLZ(String uniPLZ) {
        this.uniPLZ = uniPLZ;
    }

    public Coordinate getHomePosition() {
        return homePosition;
    }

    public void setHomePosition(Coordinate homePosition) {
        this.homePosition = homePosition;
    }

    public Coordinate getDropOffPosition() {
        return dropOffPosition;
    }

    public void setDropOffPosition(Coordinate dropOffPosition) {
        this.dropOffPosition = dropOffPosition;
    }

    public LocalDateTime getArrivalIntervalStart() {
        return arrivalIntervalStart;
    }

    public void setArrivalIntervalStart(LocalDateTime arrivalIntervalStart) {
        this.arrivalIntervalStart = arrivalIntervalStart;
    }

    public LocalDateTime getArrivalIntervalEnd() {
        return arrivalIntervalEnd;
    }

    public void setArrivalIntervalEnd(LocalDateTime arrivalIntervalEnd) {
        this.arrivalIntervalEnd = arrivalIntervalEnd;
    }

    public LocalDateTime getDepartureIntervalStart() {
        return departureIntervalStart;
    }

    public void setDepartureIntervalStart(LocalDateTime departureIntervalStart) {
        this.departureIntervalStart = departureIntervalStart;
    }

    public LocalDateTime getDepartureIntervalEnd() {
        return departureIntervalEnd;
    }

    public void setDepartureIntervalEnd(LocalDateTime departureIntervalEnd) {
        this.departureIntervalEnd = departureIntervalEnd;
    }


    //TODO
    @Override
    public int compareTo(@NotNull Object o) {
        if(GeneralManager.compareMatch!=null){
            Request request = (Request) o;
            if(Match.similarToRequest(request, GeneralManager.compareMatch)<Match.similarToRequest(this, GeneralManager.compareMatch)){
                return -1;
            }else if(Match.similarToRequest(request, GeneralManager.compareMatch)>Match.similarToRequest(this, GeneralManager.compareMatch)){
                return 1;
            }
            return 0;
        }
        return 0;
    }
}
