package camos.mode_execution.mobilitymodels;

import camos.mode_execution.groupings.Grouping;
import com.graphhopper.ResponsePath;
import camos.mode_execution.*;
import camos.mode_execution.groupings.Ride;
import camos.mode_execution.mobilitymodels.modehelpers.CommonFunctionHelper;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.*;

/**
 * EverybodyDrives is a mobility mode in which every agent rides in their own car.
 */
public class EverybodyDrives extends MobilityMode {

    Map<LocalDateTime,List<Ride>> timeToRideStart;
    List<LocalDateTime> sortedTimes;

    public EverybodyDrives(){
        super();
        timeToRideStart = new HashMap<>();
        sortedTimes = new ArrayList<>();
    }

    public String getName(){
        return "EverybodyDrives";
    }


    public void prepareMode(List<Agent> agents){

        this.agents = new ArrayList<>(agents);
        for(Agent agent : agents){
            Request agentRequest = agent.getRequest();
            LocalDateTime driveStartTime = CommonFunctionHelper.calculateToUniDriveStartTime(agent.getRequest());
            Ride toUniRide = new Ride(agentRequest.getHomePosition(),agentRequest.getDropOffPosition(),driveStartTime,agentRequest.getFavoredArrivalTime(),agent.getCar(),agent, Requesttype.DRIVETOUNI,List.of(agent));
            Ride fromUniRide = new Ride(agentRequest.getDropOffPosition(),agentRequest.getHomePosition(),agentRequest.getFavoredDepartureTime(),null,agent.getCar(),agent,Requesttype.DRIVEHOME,List.of(agent));
            CommonFunctionHelper.addToRideStartList(driveStartTime,toUniRide,this.timeToRideStart);
            CommonFunctionHelper.addToRideStartList(agentRequest.getFavoredDepartureTime(),fromUniRide,this.timeToRideStart);
        }
        this.sortedTimes = new ArrayList<>(timeToRideStart.keySet());
        Collections.sort(this.sortedTimes);
    }


    public void startMode(){
        if(this.sortedTimes.isEmpty()){
            throw new RuntimeException("Run 'prepareSimulation' first.");
        }
        for(LocalDateTime time : this.sortedTimes){
            if(timeToRideStart.containsKey(time)){
                for(Ride ride : timeToRideStart.get(time)){
                    ride.setEndTime(calculateMetrics(ride));
                }
            }
        }
    }


    public boolean checkIfConstraintsAreBroken(List<Agent> agents){
        for(Agent agent : agents){
            if(agentToRides==null || !agentToRides.containsKey(agent) || agentToRides.get(agent).size()<2){
                return true;
            }
            Ride toRide = agentToRides.get(agent).get(0);
            Ride backRide = agentToRides.get(agent).get(1);
            if(toRide==null || backRide==null){
                return true;
            }
            if(!CommonFunctionHelper.isOverlapping(toRide.getEndTime(),toRide.getEndTime(),agent.getRequest().getArrivalIntervalStart(),agent.getRequest().getArrivalIntervalEnd())){
                return true;
            }
            if(!CommonFunctionHelper.isOverlapping(backRide.getStartTime(),backRide.getStartTime(),agent.getRequest().getDepartureIntervalStart(),agent.getRequest().getDepartureIntervalEnd())){
                return true;
            }
        }
        return false;
    }


    @Override
    public void writeResultsToFile() {
        Map<Agent,Double> kmTravelled = this.getKmTravelled();
        Map<Agent,Double> minutesTravelled = this.getMinutesTravelled();
        Map<Agent,Double> emissions = this.getEmissions();
        Map<Agent,Double> costs = this.getCosts();
        Map<Set<Object>,Double> oneWayEmissions = this.getOneWayEmissions();
        Map<Set<Object>,Double> oneWayCosts = this.getOneWayCosts();
        Map<Set<Object>,Double> oneWayKmTravelled = this.getOneWayKmTravelled();
        Map<Set<Object>,Double> oneWayMinutesTravelled = this.getOneWayMinutesTravelled();
        Map<Agent, List<Ride>> agentToRides = this.getAgentToRides();

        List<String> dataLines = new ArrayList<>();
        dataLines.add("Agent Id,GesamtKilometer,GesamtMinuten,GesamtCO2,GesamtKosten,HinKilometer,HinMinuten,HinCO2,HinKosten,RueckKilometer,RueckMinuten,RueckCO2,RueckKosten");
        for(Agent a : kmTravelled.keySet()){
            Ride toUniRide = agentToRides.get(a).get(0);
            Ride homeRide = agentToRides.get(a).get(1);
            dataLines.add(a.getId() + "," + kmTravelled.get(a) + "," + minutesTravelled.get(a) + "," + emissions.get(a) + "," + costs.get(a) + "," + oneWayKmTravelled.get(Set.of(toUniRide)) + "," + oneWayMinutesTravelled.get(Set.of(toUniRide)) + "," + oneWayEmissions.get(Set.of(toUniRide)) + "," + oneWayCosts.get(Set.of(toUniRide)) + "," + oneWayKmTravelled.get(Set.of(homeRide)) + "," + oneWayMinutesTravelled.get(Set.of(homeRide)) + "," + oneWayEmissions.get(Set.of(homeRide)) + "," + oneWayCosts.get(Set.of(homeRide)));
        }

        File csvOutputFile = new File("edResults.csv");
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            for(String data : dataLines){
                pw.println(data);
            }
        }catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public List<Ride> returnFinishedRides() {
        return this.rides;
    }

    /**
     * Calculate and save the output metrics of one ride and return the ride end time.
     *
     * @param  ride the ride for which the metrics are to be calculated
     * @return      the ride end time
     */
    public LocalDateTime calculateMetrics(Ride ride){
        Agent agent = ride.getAgents().get(0);
        ResponsePath path = CommonFunctionHelper.getSimpleBestGraphhopperPath(ride.getStartPosition(),ride.getEndPosition());
        double distance = path.getDistance()/1000.0;
        long timeInMinutes = path.getTime()/60000L;
        double time = (double) timeInMinutes;
        double pathCosts = distance*agent.getCar().getConsumptionPerKm()*agent.getCar().getPricePerLiter();
        double pathEmissions = distance*agent.getCar().getConsumptionPerKm()*agent.getCar().getCo2EmissionPerLiter();

        List<Ride> rideList = new ArrayList<>();
        if(agentToRides.containsKey(agent)){
            rideList = agentToRides.get(agent);
        }
        rideList.add(ride);
        agentToRides.put(agent,rideList);

        oneWayCosts.put(Set.of(ride),pathCosts);
        oneWayEmissions.put(Set.of(ride),pathEmissions);
        oneWayKmTravelled.put(Set.of(ride),distance);
        oneWayMinutesTravelled.put(Set.of(ride),time);

        if(minutesTravelled.containsKey(agent)){
            time = minutesTravelled.get(agent)+time;
            distance = kmTravelled.get(agent)+distance;
            pathEmissions = emissions.get(agent)+pathEmissions;
            pathCosts = costs.get(agent)+pathCosts;
        }
        minutesTravelled.put(agent, time);
        kmTravelled.put(agent, distance);
        emissions.put(agent,pathEmissions);
        costs.put(agent,pathCosts);

        return ride.getTypeOfGrouping()==Requesttype.DRIVETOUNI ? ride.getEndTime() : ride.getStartTime().plusMinutes(timeInMinutes);
    }




}
