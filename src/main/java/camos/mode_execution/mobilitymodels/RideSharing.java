package camos.mode_execution.mobilitymodels;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager;
import com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.util.shapes.GHPoint;
import camos.GeneralManager;
import camos.mode_execution.*;
import camos.mode_execution.carmodels.StandInVehicle;
import camos.mode_execution.groupings.Match;
import camos.mode_execution.groupings.Ride;
import camos.mode_execution.groupings.Stop;
import camos.mode_execution.groupings.Stopreason;
import camos.mode_execution.mobilitymodels.modehelpers.CommonFunctionHelper;
import camos.mode_execution.mobilitymodels.tsphelpers.ActivityOrderConstraint;
import camos.mode_execution.mobilitymodels.tsphelpers.ActivityWaitConstraintOneAllowed;
import camos.mode_execution.mobilitymodels.tsphelpers.TransportCosts;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;


public class RideSharing extends MobilityMode {

    public static final String name = "Ridesharing";
    List<LocalDateTime> sortedTimes;

    MobilityMode compareMode;
    List<Agent> unwillingAgents;
    int requestCounter;
    int requestSum;
    Map<LocalDateTime, List<Request>> timeToRequest;
    Map<Agent, LocalDateTime> agentToLatestDriveStartTime;
    List<Match> activeMatches;
    List<Match> finishedMatches;
    List<Agent> drivers;
    Map<Agent, Request> lost;

    Map<String, Coordinate> postcodeToCoordinate;
    List<Request> pendingHomeRequests;
    Map<String, Long> secondsBetweenDropOffs;
    Map<Match, LocalDateTime> matchToStartTime;
    Map<LocalDateTime, List<Match>> timeToMatch;
    Map<Match, VehicleRoutingProblemSolution> matchToSolution;
    Map<Match, VehicleRoutingProblemSolution> temporaryMatchToSolution;


    public RideSharing(){
        super();
        comparing = true;
        unwillingAgents = new ArrayList<>();
        lost = new HashMap<>();
        matchToStartTime = new HashMap<>();
        matchToSolution = new HashMap<>();
        temporaryMatchToSolution = new HashMap<>();
        timeToMatch = new HashMap<>();
        activeMatches = new ArrayList<>();
        finishedMatches = new ArrayList<>();
        drivers = new ArrayList<>();
        pendingHomeRequests = new ArrayList<>();
        agentToLatestDriveStartTime = new HashMap<>();
        secondsBetweenDropOffs = new HashMap<>();
        sortedTimes = new ArrayList<>();
        this.compareMode = ModeExecutionManager.compareMode;
        this.postcodeToCoordinate = ModeExecutionManager.postcodeToCoordinate;
    }


    public String getName(){
        return "Ridesharing";
    }


    public void prepareMode(List<Agent> agents) {

        this.agents = new ArrayList<>(agents);
        this.timeToRequest = new HashMap<>();
        List<Agent> willingAgents = new ArrayList<>(agents);
        CommonFunctionHelper.filterWilling(GeneralManager.percentOfWillingStudents,willingAgents,unwillingAgents);
        for(Agent agent : this.unwillingAgents){
            this.letAgentDriveNormally(agent);
        }

        for (Request request : splitRequestsInTwo(willingAgents)) {
            List<Request> requestList = new ArrayList<>();
            if (timeToRequest.containsKey(request.getRequestTime())) {
                requestList = timeToRequest.get(request.getRequestTime());
            }
            requestList.add(request);
            timeToRequest.put(request.getRequestTime(), requestList);
        }
        this.sortedTimes = new ArrayList<>(timeToRequest.keySet());
        Collections.sort(this.sortedTimes);

        CommonFunctionHelper.calculateSecondsBetweenDropOffs(this.secondsBetweenDropOffs,this.postcodeToCoordinate);
        CommonFunctionHelper.calculateAcceptedDrivingTimes(agents, this.compareMode, ""); //TODO
    }


    public List<Request> splitRequestsInTwo(List<Agent> agents) {
        List<Request> splitRequests = new ArrayList<>();
        for (Agent agent : agents) {
            Request sourceRequest = agent.getRequest();
            Request driveToUniRequest = new Request(sourceRequest);
            driveToUniRequest.setRequesttype(Requesttype.DRIVETOUNI);

            Request driveHomeRequest = new Request(sourceRequest);
            driveHomeRequest.setRequesttype(Requesttype.DRIVEHOME);
            int low = 30;
            int high = 121;
            int result = GeneralManager.random.nextInt(high - low) + low;
            driveHomeRequest.setRequestTime(driveHomeRequest.getFavoredDepartureTime().minusMinutes(result));

            splitRequests.add(driveToUniRequest);
            splitRequests.add(driveHomeRequest);
        }
        requestSum = splitRequests.size();
        return splitRequests;
    }


    public void startMode() {
        List<Request> doneRequests = new ArrayList<>();

        if(this.sortedTimes.isEmpty()){
            throw new RuntimeException("Run 'prepareSimulation' first.");
        }

        requestCounter = 0;
        int lastPercent = -1;
        LocalDateTime lastTime = null;

        while(!this.sortedTimes.isEmpty()){
            LocalDateTime time = this.sortedTimes.get(0);
            if(lastTime != null && lastTime.isAfter(time)){
                int f = 2;
            }
            if (timeToMatch.containsKey(time)) {
                for (Match match : timeToMatch.get(time)) {
                    if(finishedMatches.contains(match)){
                        int f = 2;
                    }
                    calculateMetrics(matchToRide(match));
                }
            }
            if (timeToRequest.containsKey(time) && (lastTime==null || lastTime.isBefore(time))) {
                for (Request request : timeToRequest.get(time)) {
                    if(doneRequests.contains(request)){
                        List<Request> rr = timeToRequest.get(time);
                        int f = 2;
                    }
                    doneRequests.add(request);
                    requestCounter++;
                    int countOf5percentSteps = (int) Math.floor(((double) requestCounter / requestSum) * 20);
                    String progressBar = "|" + "=".repeat(countOf5percentSteps) + " ".repeat(20 - countOf5percentSteps) + "|" + "\r";
                    findMatch(request);
                    if (countOf5percentSteps > lastPercent) {
                        System.out.print(progressBar);
                    }
                    lastPercent = countOf5percentSteps;
                }
            }
            sortedTimes.remove(time);
            lastTime = time;
        }

        for (Request pendingRequest : this.pendingHomeRequests) {
            lost.put(pendingRequest.getAgent(), pendingRequest);
            handleLostPerson(pendingRequest);
        }
        this.pendingHomeRequests = new ArrayList<>();
    }


    public boolean checkIfConstraintsAreBroken(List<Agent> agents){
        for(Agent agent : agents){
            if(agentToRides==null || !agentToRides.containsKey(agent) || agentToRides.get(agent).isEmpty()){
                return true;
            }
            Ride toRide = agentToRides.get(agent).get(0);
            Ride backRide = agentToRides.get(agent).size()>1 ? agentToRides.get(agent).get(1) : null;
            if(toRide==null){
                return true;
            }
            Set<Object> set = new HashSet<>();
            set.add(toRide);
            set.add(agent);
            if(oneWayMinutesTravelled.get(set)>agent.getWillingToRideInMinutes()){
                return true;
            }
            Stop stop = CommonFunctionHelper.getAgentStops(toRide,agent).get(0);
            if(!CommonFunctionHelper.isOverlapping(stop.getStartTime(),stop.getStartTime(),agent.getRequest().getArrivalIntervalStart(),agent.getRequest().getArrivalIntervalEnd())){
                return true;
            }
            if(backRide!=null){
                set.remove(toRide);
                set.add(backRide);
                if(oneWayMinutesTravelled.get(set)>agent.getWillingToRideInMinutes()){
                    return true;
                }
                stop = CommonFunctionHelper.getAgentStops(backRide,agent).get(0);
                if(!CommonFunctionHelper.isOverlapping(stop.getStartTime(),stop.getStartTime(),agent.getRequest().getDepartureIntervalStart(),agent.getRequest().getDepartureIntervalEnd())){
                    return true;
                }
            }else{
                if(!lost.containsKey(agent)){
                    return true;
                }
            }
        }
        return false;
    }


    @Override
    public void writeResultsToFile() { //TODO säubern
        double averageSeatCount = 0;
        double averageSeatCountToUni = 0;
        int countOfToUni = 0;
        int countOfHome = 0;
        double averageSeatCountHome = 0;
        double averageSeatCountForWilling = 0;
        int countOfWilling=0;
        double averageSeatCountToUniForWilling = 0;
        int countOfWillingToUni=0;
        double averageSeatCountHomeForWilling = 0;
        int countOfWillingHome=0;
        double averageSeatCountForGroups = 0;
        int countOfGroups=0;
        double averageSeatCountToUniForGroups = 0;
        int countOfGroupsToUni=0;
        double averageSeatCountHomeForGroups = 0;
        int countOfGroupsHome=0;

        List<String> dataLines = new ArrayList<>();

        List<String> dataLines3 = new ArrayList<>();
        dataLines.add("Agent Id,ToRide Id,BackRide Id,willing,wasDriver,droveAloneToUni,droveAloneBack,lost,GesamtKilometer,GesamtMinuten,GesamtCO2,GesamtKosten,HinKilometer,HinMinuten,HinCO2,HinKosten,RueckKilometer,RueckMinuten,RueckCO2,RueckKosten");
        dataLines3.add("Ride Id,AgentCount,unwillingRide,StopCount,StartTime,EndTime,StartPosition-Latitude,StartPosition-Longitude,EndPosition-Latitude,EndPosition-Longitude");
        for(Ride ride : rides){
            dataLines3.add(ride.getId() + "," + ride.getAgents().size() + "," + (ride.getDriver() != null && !ride.getDriver().isWillingToUseAlternatives()) + "," + ride.getStops().size() + "," + ride.getStartTime() + "," + ride.getEndTime() + "," + ride.getStartPosition().getLatitude() + "," + ride.getStartPosition().getLongitude() + "," + ride.getEndPosition().getLatitude() + "," + ride.getEndPosition().getLongitude());
            averageSeatCount += ride.getAgents().size();
            if((ride.getDriver() != null && !ride.getDriver().isWillingToUseAlternatives())){
                averageSeatCountForWilling += ride.getAgents().size();
                countOfWilling++;
            }
            if(ride.getAgents().size()>1){
                averageSeatCountForGroups += ride.getAgents().size();
                countOfGroups++;
            }
            if(ride.getTypeOfGrouping()==Requesttype.DRIVETOUNI){
                if((ride.getDriver() != null && !ride.getDriver().isWillingToUseAlternatives())){
                    averageSeatCountToUniForWilling += ride.getAgents().size();
                    countOfWillingToUni++;
                }
                if(ride.getAgents().size()>1){
                    averageSeatCountToUniForGroups += ride.getAgents().size();
                    countOfGroupsToUni++;
                }
                averageSeatCountToUni += ride.getAgents().size();
                countOfToUni++;
            }else{
                if((ride.getDriver() != null && !ride.getDriver().isWillingToUseAlternatives())){
                    averageSeatCountHomeForWilling += ride.getAgents().size();
                    countOfWillingHome++;
                }
                if(ride.getAgents().size()>1){
                    averageSeatCountHomeForGroups += ride.getAgents().size();
                    countOfGroupsHome++;
                }
                averageSeatCountHome += ride.getAgents().size();
                countOfHome++;
            }
        }
        averageSeatCount = averageSeatCount/rides.size();
        averageSeatCountToUni = averageSeatCountToUni/countOfToUni;
        averageSeatCountHome = averageSeatCountHome/countOfHome;
        averageSeatCountForWilling = averageSeatCountForWilling/countOfWilling;
        averageSeatCountToUniForWilling = averageSeatCountToUniForWilling/countOfWillingToUni;
        averageSeatCountHomeForWilling = averageSeatCountHomeForWilling/countOfWillingHome;
        averageSeatCountForGroups = averageSeatCountForGroups/countOfGroups;
        averageSeatCountToUniForGroups = averageSeatCountToUniForGroups/countOfGroupsToUni;
        averageSeatCountHomeForGroups = averageSeatCountHomeForGroups/countOfGroupsHome;

        List<String> dataLines2 = new ArrayList<>();
        dataLines2.add("averageSeatCount,averageSeatCountToUni,averageSeatCountHome,count of lost students,count of driving students,count of agents,count of rides,count of rides to uni,count of rides home,averageSeatCountForWilling,countOfWilling,averageSeatCountToUniForWilling,countOfWillingToUni,averageSeatCountHomeForWilling,countOfWillingHome,averageSeatCountForGroups,countOfGroups,averageSeatCountToUniForGroups,countOfGroupsToUni,averageSeatCountHomeForGroups,countOfGroupsHome");
        dataLines2.add(averageSeatCount + "," + averageSeatCountToUni + "," + averageSeatCountHome + "," + lost.keySet().size() + "," + drivers.size() + "," + agents.size() + "," + finishedMatches.size() + "," + countOfToUni + "," + countOfHome + "," + averageSeatCountForWilling + "," + countOfWilling + "," + averageSeatCountToUniForWilling + "," + countOfWillingToUni + "," + averageSeatCountHomeForWilling + "," + countOfWillingHome + "," + averageSeatCountForGroups + "," + countOfGroups + "," + averageSeatCountToUniForGroups + "," + countOfGroupsToUni + "," + averageSeatCountHomeForGroups + "," + countOfGroupsHome);

        for(Agent a : agents) {
            Ride toUni = agentToRides.get(a).get(0);
            Set<Object> set = Set.of(a,toUni);
            double km;
            double min;
            double co2;
            double cost;
            Ride home = null;
            if (agentToRides.get(a).size() > 1) {
                home = agentToRides.get(a).get(1);
                Set<Object> set2 = Set.of(a,home);
                km = oneWayKmTravelled.get(set2);
                min = oneWayMinutesTravelled.get(set2);
                co2 = oneWayEmissions.get(set2);
                cost = oneWayCosts.get(set2);
            } else {
                km = 0.0;
                min = 0.0;
                co2 = 0.0;
                cost = 0.0;
            }
            dataLines.add(a.getId() + "," + agentToRides.get(a).get(0).getId() + "," + (agentToRides.get(a).size() > 1 ? agentToRides.get(a).get(1).getId() : -1) + "," + a.isWillingToUseAlternatives() + "," + toUni.getDriver().equals(a) + "," + (toUni.getAgents().size() == 1) + "," + (home != null && home.getAgents().size() == 1) + "," + lost.containsKey(a) + "," + kmTravelled.get(a) + "," + minutesTravelled.get(a) + "," + emissions.get(a) + "," + costs.get(a) + "," + oneWayKmTravelled.get(set) + "," + oneWayMinutesTravelled.get(set) + "," + oneWayEmissions.get(set) + "," + oneWayCosts.get(set) + "," + km + "," + min + "," + co2 + "," + cost);
        }

        File csvOutputFile = new File("rsResults.csv");
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            for(String data : dataLines){
                pw.println(data);
            }
        }catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
        String[] results = "rsResults.csv".split("\\.");
        String newResultFileName = results[0] + "AdditionalData.csv";
        File csvOutputFile2 = new File(newResultFileName);
        try (PrintWriter pw = new PrintWriter(csvOutputFile2)) {
            for(String data : dataLines2){
                pw.println(data);
            }
        }catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }

        newResultFileName = results[0] + "ForRides.csv";
        File csvOutputFile3 = new File(newResultFileName);
        try (PrintWriter pw = new PrintWriter(csvOutputFile3)) {
            for(String data : dataLines3){
                pw.println(data);
            }
        }catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public List<Ride> returnFinishedRides() {
        return rides;
    }


    public void findMatch(Request request) {
        boolean matchFound = false;
        List<Match> eligibleMatches = new ArrayList<>();
        LocalDateTime requestIntervalStart;
        LocalDateTime requestIntervalEnd;

        if (!(request.getRequesttype() == Requesttype.DRIVEHOME && drivers.contains(request.getAgent()))) {
            if (request.getRequesttype() == Requesttype.DRIVETOUNI) {
                requestIntervalStart = request.getArrivalIntervalStart();
                requestIntervalEnd = request.getArrivalIntervalEnd();
            } else {
                requestIntervalStart = request.getDepartureIntervalStart();
                requestIntervalEnd = request.getDepartureIntervalEnd();
            }

            GeneralManager.compareRequest = request;
            List<Match> matchList;
            matchList = this.activeMatches.stream()
                    .filter(m -> m.getTypeOfGrouping() == request.getRequesttype() && m.getAgents().size() < m.getDriver().getCar().getSeatCount())
                    .collect(Collectors.toList());
            matchList.sort(null);

            List<Match> truncatedList = matchList.subList(0, Math.min(matchList.size(), 20));
            for (Match match : truncatedList) {
                if (checkIfRequestFitsMatch(request, match, requestIntervalStart, requestIntervalEnd)) {
                    eligibleMatches.add(match);
                }
            }

            if (!eligibleMatches.isEmpty()) {
                Match bestMatch = getBestMatch(eligibleMatches, request);
                matchFound = true;
                if (temporaryMatchToSolution.containsKey(bestMatch)) {
                    matchToSolution.put(bestMatch, temporaryMatchToSolution.get(bestMatch));
                    temporaryMatchToSolution.remove(bestMatch);
                }
                setNewMatchPartner(bestMatch, request, requestIntervalStart, requestIntervalEnd, false);
            }

        }

        if (!matchFound) {
            if (request.getRequesttype() == Requesttype.DRIVETOUNI) {
                Match match = new Match(new ArrayList<>(), new HashMap<>(), request.getAgent(), request.getAgent().getCar(), request.getHomePosition(), request.getDropOffPosition(), request.getRequesttype(), request.getArrivalIntervalStart(), request.getArrivalIntervalEnd(), MobilityType.RIDESHARING);
                setNewMatchPartner(match, request, request.getArrivalIntervalStart(), request.getArrivalIntervalEnd(), true);
            } else {
                if (drivers.contains(request.getAgent())) {
                    Match match = new Match(new ArrayList<>(), new HashMap<>(), request.getAgent(), request.getAgent().getCar(), request.getDropOffPosition(), request.getHomePosition(), request.getRequesttype(), request.getDepartureIntervalStart(), request.getDepartureIntervalEnd(), MobilityType.RIDESHARING);
                    setNewMatchPartner(match, request, request.getDepartureIntervalStart(), request.getDepartureIntervalEnd(), false);
                    GeneralManager.compareMatch = match;
                    List<Request> pendingList = new ArrayList<>(this.pendingHomeRequests);
                    pendingList.sort(null);
                    List<Request> trunList;
                    trunList = pendingList.subList(0, Math.min(pendingList.size(), 20));
                    GeneralManager.compareMatch = null;
                    for (Request pendingRequest : trunList) {
                        if (match.getAgents().size() < match.getDriver().getCar().getSeatCount()) {
                            requestIntervalStart = pendingRequest.getDepartureIntervalStart();
                            requestIntervalEnd = pendingRequest.getDepartureIntervalEnd();
                            if (request.getRequestTime().isAfter(requestIntervalEnd)) {
                                lost.put(pendingRequest.getAgent(), pendingRequest);
                                handleLostPerson(pendingRequest);
                                this.pendingHomeRequests.remove(pendingRequest);
                                continue;
                            }

                            if (checkIfRequestFitsMatch(pendingRequest, match, requestIntervalStart, requestIntervalEnd)) {
                                if (temporaryMatchToSolution.containsKey(match)) {
                                    matchToSolution.put(match, temporaryMatchToSolution.get(match));
                                    temporaryMatchToSolution.remove(match);
                                }
                                setNewMatchPartner(match, pendingRequest, requestIntervalStart, requestIntervalEnd, false);
                                this.pendingHomeRequests.remove(pendingRequest);
                            }
                        }
                    }
                } else {
                    pendingHomeRequests.add(request);
                }
            }
        }
        GeneralManager.compareRequest = null;
    }


    public void calculateMetrics(Ride ride) {
        Coordinate firstCoordinate = ride.getStartPosition();
        Coordinate secondCoordinate;
        Map<Agent, Double> agentToDistance = new HashMap<>();
        Map<Agent, Double> agentToTime = new HashMap<>();
        for (Agent agent : ride.getAgents()) {
            agentToDistance.put(agent, 0.0);
            agentToTime.put(agent, 0.0);
        }
        for (Stop stop : ride.getStops()) {
            secondCoordinate = stop.getStopCoordinate();
            List<Location> list = new ArrayList<>();
            list.add(Coordinate.coordinateToLocation(firstCoordinate));
            list.add(Coordinate.coordinateToLocation(secondCoordinate));
            double distance = ModeExecutionManager.distanceMap.get(list);
            double time = ModeExecutionManager.timeMap.get(list) + GeneralManager.stopTime;
            for (Agent agent : ride.getAgents()) {
                if (agentToDistance.containsKey(agent)) {
                    agentToDistance.put(agent, agentToDistance.get(agent) + distance);
                    agentToTime.put(agent, agentToTime.get(agent) + time);
                }
                if (stop.getPersonsInQuestion().contains(agent)) {
                    if (ride.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                        agentToTime.put(agent, agentToTime.get(agent) - GeneralManager.stopTime);
                        setMetrics(agent, ride, agentToDistance, agentToTime);
                        agentToDistance.remove(agent);
                        agentToTime.remove(agent);
                    } else {
                        agentToDistance.put(agent, 0.0);
                        agentToTime.put(agent, 0.0);
                    }
                }
            }
            firstCoordinate = secondCoordinate;
        }
        if (!ride.getStops().isEmpty()) {
            List<Location> list = new ArrayList<>();
            list.add(Coordinate.coordinateToLocation(firstCoordinate));
            list.add(Coordinate.coordinateToLocation(ride.getEndPosition()));
            double time = ModeExecutionManager.timeMap.get(list);
            double distance = ModeExecutionManager.distanceMap.get(list);
            for (Agent agent : agentToDistance.keySet()) {
                agentToDistance.put(agent, agentToDistance.get(agent) + distance);
                agentToTime.put(agent, agentToTime.get(agent) + time);
                setMetrics(agent, ride, agentToDistance, agentToTime);
            }
        } else {
            for (Agent a : ride.getAgents()) {
                setMetrics(a, ride, agentToDistance, agentToTime);
            }
        }
    }


    public void setMetrics(Agent agent, Ride ride, Map<Agent, Double> agentToDistance, Map<Agent, Double> agentToTime) {

        Set<Object> set;
        if (agent.equals(ride.getDriver())) {
            double co2 = (agentToDistance.get(agent) / 1000) * agent.getCar().getConsumptionPerKm() * agent.getCar().getCo2EmissionPerLiter();
            double costPerKm = GeneralManager.handleLost && this.lost.containsKey(agent) ? 0.3 : agent.getCar().getConsumptionPerKm() * agent.getCar().getPricePerLiter();
            double cost = (agentToDistance.get(agent) / 1000) * costPerKm;
            for (Agent a : ride.getAgents()) {
                set = new HashSet<>();
                set.add(a);
                set.add(ride);
                oneWayCosts.put(set, cost / ride.getAgents().size());
                oneWayEmissions.put(set, co2 / ride.getAgents().size());

                if (emissions.containsKey(a)) {
                    emissions.put(a, emissions.get(a) + co2 / ride.getAgents().size());
                    costs.put(a, costs.get(a) + cost / ride.getAgents().size());
                } else {
                    emissions.put(a, co2 / ride.getAgents().size());
                    costs.put(a, cost / ride.getAgents().size());
                }
            }
        }

        set = new HashSet<>();
        set.add(agent);
        set.add(ride);

        oneWayKmTravelled.put(set, agentToDistance.get(agent) / 1000);
        oneWayMinutesTravelled.put(set, agentToTime.get(agent));
        List<Ride> rideList = new ArrayList<>();
        if (agentToRides.containsKey(agent)) {
            rideList = agentToRides.get(agent);
        }
        rideList.add(ride);
        agentToRides.put(agent, rideList);

        if (minutesTravelled.containsKey(agent)) {
            minutesTravelled.put(agent, minutesTravelled.get(agent) + agentToTime.get(agent));
            kmTravelled.put(agent, kmTravelled.get(agent) + agentToDistance.get(agent) / 1000);
        } else {
            minutesTravelled.put(agent, agentToTime.get(agent));
            kmTravelled.put(agent, agentToDistance.get(agent) / 1000);
        }
    }


    public Ride matchToRide(Match match) {
        VehicleRoutingProblemSolution solution = matchToSolution.get(match);
        Ride ride;

        if (solution == null || match.getDifferentStops().isEmpty()) {
            LocalDateTime rideEndTime ;
            if (match.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                rideEndTime = match.getDriver().getRequest().getFavoredArrivalTime();
                long minutesBetweenAgentPreferredArrivalAndMatchInterval = ChronoUnit.MINUTES.between(match.getDriver().getRequest().getFavoredArrivalTime(), getStartTimeForNoStops(match, match.getDriver().getRequest().getFavoredArrivalTime()));
                rideEndTime = minutesBetweenAgentPreferredArrivalAndMatchInterval < 0 ? rideEndTime.minusMinutes(Math.abs(minutesBetweenAgentPreferredArrivalAndMatchInterval)) : rideEndTime.plusMinutes(minutesBetweenAgentPreferredArrivalAndMatchInterval);
            } else {
                ResponsePath path = CommonFunctionHelper.getSimpleBestGraphhopperPath(this.graphHopper,match.getStartPosition(),match.getEndPosition());
                long timeInMinutes = path.getTime()/60000L;

                rideEndTime = match.getDriver().getRequest().getFavoredDepartureTime().plusMinutes(timeInMinutes);
                long minutesBetweenAgentPreferredArrivalAndMatchInterval = ChronoUnit.MINUTES.between(match.getDriver().getRequest().getFavoredDepartureTime(), getStartTimeForNoStops(match, match.getDriver().getRequest().getFavoredDepartureTime()));
                rideEndTime = minutesBetweenAgentPreferredArrivalAndMatchInterval < 0 ? rideEndTime.minusMinutes(Math.abs(minutesBetweenAgentPreferredArrivalAndMatchInterval)) : rideEndTime.plusMinutes(minutesBetweenAgentPreferredArrivalAndMatchInterval);
            }
            ride = new Ride(match.getStartPosition(), match.getEndPosition(), matchToStartTime.get(match), rideEndTime, match.getDriver().getCar(), match.getDriver(), match.getTypeOfGrouping(), match.getAgents());
            if (ride.getEndTime() == null) {
                ride.setEndTime(CommonFunctionHelper.getRideEndTime(graphHopper,ride));
            }
        } else {
            List<VehicleRoute> routes = (List<VehicleRoute>) solution.getRoutes();
            VehicleRoute route = routes.get(0);
            Stopreason reasonForStopping;
            LocalDateTime rideEndTime = matchToStartTime.get(match).plusMinutes((long) (solution.getCost()));
            ride = new Ride(match.getStartPosition(), match.getEndPosition(), matchToStartTime.get(match), rideEndTime, match.getDriver().getCar(), match.getDriver(), match.getTypeOfGrouping(), match.getAgents());
            if (match.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                reasonForStopping = Stopreason.DROPOFF;
            } else {
                reasonForStopping = Stopreason.PICKUP;
            }
            List<Agent> stopAgents;
            List<Stop> stops = new ArrayList<>();
            double lastArrivalTime = 1000;
            double departureTime;
            LocalDateTime lastArrivalLocalTime = ride.getEndTime();
            TourActivity service;
            if (match.getTypeOfGrouping() == Requesttype.DRIVEHOME) {
                lastArrivalTime = route.getEnd().getArrTime();
            }
            for (int j = route.getActivities().size() - 1; j > 0; j--) {
                service = route.getActivities().get(j);
                if (j != route.getActivities().size() - 1 || match.getTypeOfGrouping() != Requesttype.DRIVETOUNI) {
                    departureTime = service.getEndTime();
                    stopAgents = new ArrayList<>();
                    for (Agent agent : match.getAgents()) {
                        if (Coordinate.coordinateTheSameAsLocation(agent.getRequest().getDropOffPosition(), service.getLocation())) {
                            stopAgents.add(agent);
                        }
                    }
                    stops.add(new Stop(lastArrivalLocalTime.minusMinutes((long) (lastArrivalTime - departureTime)).minusMinutes(GeneralManager.stopTime), lastArrivalLocalTime.minusMinutes((long) (lastArrivalTime - departureTime)), Coordinate.locationToCoordinate(service.getLocation()), reasonForStopping, stopAgents));
                    lastArrivalLocalTime = lastArrivalLocalTime.minusMinutes((long) (lastArrivalTime - departureTime)).minusMinutes(GeneralManager.stopTime);
                }
                lastArrivalTime = service.getArrTime();
            }
            if (match.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                service = route.getActivities().get(0);
                departureTime = service.getEndTime();
                stopAgents = new ArrayList<>();
                for (Agent agent : match.getAgents()) {
                    if (Coordinate.coordinateTheSameAsLocation(agent.getRequest().getDropOffPosition(), service.getLocation())) {
                        stopAgents.add(agent);
                    }
                }
                stops.add(new Stop(lastArrivalLocalTime.minusMinutes((long) (lastArrivalTime - departureTime)).minusMinutes(GeneralManager.stopTime), lastArrivalLocalTime.minusMinutes((long) (lastArrivalTime - departureTime)), Coordinate.locationToCoordinate(service.getLocation()), reasonForStopping, stopAgents));
            }
            stops.sort(Comparator.comparing(Stop::getStartTime));
            ride.setStops(stops);
        }

        activeMatches.remove(match);
        finishedMatches.add(match);
        rides.add(ride);
        return ride;
    }


    public boolean checkIfRequestFitsMatch(Request request, Match match, LocalDateTime requestIntervalStart, LocalDateTime requestIntervalEnd) {
        if (match.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
            if (request.getDropOffPosition().equals(match.getEndPosition())) {
                return CommonFunctionHelper.isOverlapping(match.getTimeIntervalStart(), match.getTimeIntervalEnd(), requestIntervalStart, requestIntervalEnd) && getDistanceToDriverInMeters(request, match) <= request.getAgent().getWillingToWalkInMeters() && wouldDriveBy(request.getDropOffPosition(), match, requestIntervalStart, requestIntervalEnd, request.getAgent());
            } else {
                return CommonFunctionHelper.isOverlapping(match.getTimeIntervalStart(), match.getTimeIntervalEnd(), requestIntervalStart.plusSeconds(this.secondsBetweenDropOffs.get(request.getUniPLZ() + "-" + match.getDriver().getRequest().getUniPLZ())), requestIntervalEnd.plusSeconds(this.secondsBetweenDropOffs.get(request.getUniPLZ() + "-" + match.getDriver().getRequest().getUniPLZ()))) && getDistanceToDriverInMeters(request, match) <= request.getAgent().getWillingToWalkInMeters() && wouldDriveBy(request.getDropOffPosition(), match, requestIntervalStart, requestIntervalEnd, request.getAgent());
            }
        } else {
            if (request.getDropOffPosition().equals(match.getStartPosition())) {
                return CommonFunctionHelper.isOverlapping(match.getTimeIntervalStart(), match.getTimeIntervalEnd(), requestIntervalStart, requestIntervalEnd) && getDistanceToDriverInMeters(request, match) <= request.getAgent().getWillingToWalkInMeters() && wouldDriveBy(request.getDropOffPosition(), match, requestIntervalStart, requestIntervalEnd, request.getAgent());
            } else {
                return CommonFunctionHelper.isOverlapping(match.getTimeIntervalStart().plusSeconds(this.secondsBetweenDropOffs.get(match.getDriver().getRequest().getUniPLZ() + "-" + request.getUniPLZ())), match.getTimeIntervalEnd(), requestIntervalStart.minusSeconds(this.secondsBetweenDropOffs.get(match.getDriver().getRequest().getUniPLZ() + "-" + request.getUniPLZ())), requestIntervalEnd) && getDistanceToDriverInMeters(request, match) <= request.getAgent().getWillingToWalkInMeters() && wouldDriveBy(request.getDropOffPosition(), match, requestIntervalStart, requestIntervalEnd, request.getAgent());
            }
        }
    }


    public void setNewMatchPartner(Match match, Request request, LocalDateTime requestIntervalStart, LocalDateTime requestIntervalEnd, boolean newDriver) {
        Coordinate position;

        boolean sameAsBefore = true;
        boolean sameDropOff = false;
        VehicleRoutingProblemSolution solution = this.matchToSolution.remove(match);
        LocalDateTime startTime = this.matchToStartTime.remove(match);

        if (startTime != null && this.timeToMatch.containsKey(startTime)) {
            List<Match> matchList = this.timeToMatch.get(startTime);
            matchList.remove(match);
            if (matchList.isEmpty()) {
                this.timeToMatch.remove(startTime);
                if(!timeToRequest.containsKey(startTime)){
                    CommonFunctionHelper.updateSortedTimes(this.sortedTimes,startTime,true);
                }
            } else {
                this.timeToMatch.put(startTime, matchList);
            }
        }

        if (match.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
            position = match.getEndPosition();
        } else {
            position = match.getStartPosition();
        }
        int index = this.activeMatches.indexOf(match);
        List<Agent> matchPeople = match.getAgents();
        matchPeople.add(request.getAgent());
        match.setAgents(matchPeople);
        String oldInterval = CommonFunctionHelper.getIntervalString(match.getTimeIntervalStart(), match.getTimeIntervalEnd());
        setNewInterval(match, requestIntervalStart, requestIntervalEnd, request.getDropOffPosition());
        if (!request.getDropOffPosition().equals(position)) {
            Map<Coordinate, String> map = match.getDifferentStops();
            if (match.getDifferentStops().containsKey(request.getDropOffPosition())) {
                if (!match.getDifferentStops().get(request.getDropOffPosition()).equals(getNewStopInterval(match.getDifferentStops().get(request.getDropOffPosition()), requestIntervalStart, requestIntervalEnd))) {
                    map.put(request.getDropOffPosition(), getNewStopInterval(match.getDifferentStops().get(request.getDropOffPosition()), requestIntervalStart, requestIntervalEnd));
                    sameAsBefore = false;
                }
            } else {
                map.put(request.getDropOffPosition(), CommonFunctionHelper.getIntervalString(requestIntervalStart, requestIntervalEnd));
                sameAsBefore = false;
            }
            match.setDifferentStops(map);
        } else {
            sameDropOff = true;
            if (!oldInterval.equals(CommonFunctionHelper.getIntervalString(match.getTimeIntervalStart(), match.getTimeIntervalEnd()))) {
                sameAsBefore = false;
            }
        }
        if (newDriver) {
            drivers.add(request.getAgent());
        }
        if (index == -1) {
            activeMatches.add(match);
        } else {
            activeMatches.set(index, match);
        }
        this.setDriveStartTime(match, solution, startTime, sameAsBefore, sameDropOff);
    }


    public void setNewInterval(Match match, LocalDateTime start, LocalDateTime end, Coordinate dropOffPosition) {
        Coordinate referencePosition = match.getTypeOfGrouping() == Requesttype.DRIVETOUNI ? match.getEndPosition() : match.getStartPosition();
        if (referencePosition.equals(dropOffPosition)) {
            if (match.getTimeIntervalStart().isBefore(start)) {
                match.setTimeIntervalStart(start);
            }
            if (match.getTimeIntervalEnd().isAfter(end)) {
                match.setTimeIntervalEnd(end);
            }
        }
    }


    public String getNewStopInterval(String oldInterval, LocalDateTime newStart, LocalDateTime newEnd) {
        LocalDateTime oldStart = LocalDateTime.parse(oldInterval.split("-")[0], GeneralManager.dateTimeFormatter);
        LocalDateTime oldEnd = LocalDateTime.parse(oldInterval.split("-")[1], GeneralManager.dateTimeFormatter);
        String newInterval = "";
        if (oldStart.isBefore(newStart)) {
            newInterval = newInterval + newStart.format(GeneralManager.dateTimeFormatter);
        } else {
            newInterval = newInterval + oldStart.format(GeneralManager.dateTimeFormatter);
        }
        newInterval = newInterval + "-";
        if (oldEnd.isAfter(newEnd)) {
            newInterval = newInterval + newEnd.format(GeneralManager.dateTimeFormatter);
        } else {
            newInterval = newInterval + oldEnd.format(GeneralManager.dateTimeFormatter);
        }
        return newInterval;
    }


    public Match getBestMatch(List<Match> eligibleMatches, Request request) {
        Match bestMatch = null;
        double bestDistanceToDriver = Double.MAX_VALUE;
        for (Match match : eligibleMatches) {
            double distance = getDistanceToDriverInMeters(request, match);
            if (distance < bestDistanceToDriver) {
                bestMatch = match;
                bestDistanceToDriver = distance;
            }
        }
        return bestMatch;
    }

    public boolean wouldDriveBy(Coordinate dropOffCoordinate, Match match, LocalDateTime start, LocalDateTime end, Agent newAgent) {
        temporaryMatchToSolution.remove(match);
        boolean sameStopAsDriver = false;

        LocalDateTime oldTimeIntervalStart = match.getTimeIntervalStart();
        LocalDateTime oldTimeIntervalEnd = match.getTimeIntervalEnd();
        if ((match.getTypeOfGrouping() == Requesttype.DRIVEHOME && match.getStartPosition().equals(dropOffCoordinate)) || (match.getTypeOfGrouping() == Requesttype.DRIVETOUNI && match.getEndPosition().equals(dropOffCoordinate))) {
            if (match.getDifferentStops().isEmpty() && !match.getTimeIntervalStart().isAfter(end) && !match.getTimeIntervalEnd().isBefore(start)) {
                return CommonFunctionHelper.getSimpleBestGraphhopperPath(this.graphHopper,match.getStartPosition(),match.getEndPosition()).getTime() <= newAgent.getWillingToRideInMinutes();
            }
            sameStopAsDriver = true;
            setNewInterval(match, start, end, dropOffCoordinate);
        }

        final int WEIGHT_INDEX = 0;
        VehicleTypeImpl.Builder vehicleTypeBuilder = VehicleTypeImpl.Builder.newInstance("vehicleType").addCapacityDimension(WEIGHT_INDEX, 5).setCostPerServiceTime(1);
        VehicleType vehicleType = vehicleTypeBuilder.build();

        VehicleImpl.Builder vehicleBuilder = VehicleImpl.Builder.newInstance("vehicle");
        vehicleBuilder.setStartLocation(Location.newInstance(match.getStartPosition().getLongitude(), match.getStartPosition().getLatitude()));
        vehicleBuilder.setEndLocation(Location.newInstance(match.getEndPosition().getLongitude(), match.getEndPosition().getLatitude()));
        vehicleBuilder.setType(vehicleType);
        vehicleBuilder.setEarliestStart(0).setLatestArrival(1000);

        VehicleImpl vehicle = vehicleBuilder.build();

        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();

        VehicleRoutingTransportCosts transportCosts = new TransportCosts();
        vrpBuilder.setRoutingCost(transportCosts);
        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);
        vrpBuilder.addVehicle(vehicle);

        int i = 0;
        double intervalStart;
        double intervalEnd;
        for (Coordinate coordinate : match.getDifferentStops().keySet()) {
            String stopInterval = match.getDifferentStops().get(coordinate);
            if (coordinate.equals(dropOffCoordinate)) {
                if (!CommonFunctionHelper.isOverlapping(LocalDateTime.parse(stopInterval.split("-")[0], GeneralManager.dateTimeFormatter), LocalDateTime.parse(stopInterval.split("-")[1], GeneralManager.dateTimeFormatter), start, end)) {
                    return false;
                }
                stopInterval = getNewStopInterval(stopInterval, start, end);
            }
            if (match.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                intervalStart = 1000 - (Math.max(ChronoUnit.MINUTES.between(LocalDateTime.parse(stopInterval.split("-")[0], GeneralManager.dateTimeFormatter), match.getTimeIntervalEnd()), 0));
                intervalEnd = 1000 - (Math.max(0, ChronoUnit.MINUTES.between(LocalDateTime.parse(stopInterval.split("-")[1], GeneralManager.dateTimeFormatter), match.getTimeIntervalEnd())));
            } else {
                intervalStart = Math.max(ChronoUnit.MINUTES.between(match.getTimeIntervalStart(), LocalDateTime.parse(stopInterval.split("-")[0], GeneralManager.dateTimeFormatter)), 0);
                intervalEnd = Math.max(ChronoUnit.MINUTES.between(match.getTimeIntervalStart(), LocalDateTime.parse(stopInterval.split("-")[1], GeneralManager.dateTimeFormatter)), 0);
            }
            Service service = Service.Builder.newInstance(String.valueOf(++i)).addSizeDimension(WEIGHT_INDEX, 1).setLocation(Location.newInstance(coordinate.getLongitude(), coordinate.getLatitude())).setServiceTime(GeneralManager.stopTime).addTimeWindow(intervalStart, intervalEnd).build();
            vrpBuilder.addJob(service);
        }
        if (!sameStopAsDriver && !match.getDifferentStops().containsKey(dropOffCoordinate)) {
            if (match.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                intervalStart = 1000 - (ChronoUnit.MINUTES.between(start, match.getTimeIntervalEnd()));
                intervalEnd = 1000 - (Math.max(0, ChronoUnit.MINUTES.between(end, match.getTimeIntervalEnd())));
            } else {
                intervalStart = Math.max(ChronoUnit.MINUTES.between(match.getTimeIntervalStart(), start), 0);
                intervalEnd = ChronoUnit.MINUTES.between(match.getTimeIntervalStart(), end);
            }
            Service service = Service.Builder.newInstance(String.valueOf(++i)).addSizeDimension(WEIGHT_INDEX, 1).setLocation(Location.newInstance(dropOffCoordinate.getLongitude(), dropOffCoordinate.getLatitude())).setServiceTime(GeneralManager.stopTime).addTimeWindow(intervalStart, intervalEnd).build();
            vrpBuilder.addJob(service);
        }
        Coordinate position;
        int priority;
        if (match.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
            intervalStart = 1000 - (ChronoUnit.MINUTES.between(match.getTimeIntervalStart(), match.getTimeIntervalEnd()));
            intervalEnd = 1000;
            priority = 10;
            position = match.getEndPosition();
        } else {
            intervalStart = 0;
            intervalEnd = ChronoUnit.MINUTES.between(match.getTimeIntervalStart(), match.getTimeIntervalEnd());
            priority = 1;
            position = match.getStartPosition();
        }
        Service service = Service.Builder.newInstance(String.valueOf(++i)).addSizeDimension(WEIGHT_INDEX, 1).setLocation(Location.newInstance(position.getLongitude(), position.getLatitude())).addTimeWindow(intervalStart, intervalEnd).setPriority(priority).build();
        vrpBuilder.addJob(service);

        VehicleRoutingProblem problem = vrpBuilder.build();

        StateManager stateManager = new StateManager(problem);
        ConstraintManager constraintManager = new ConstraintManager(problem, stateManager);
        constraintManager.addConstraint(new ActivityOrderConstraint(), ConstraintManager.Priority.CRITICAL);
        HardActivityConstraint constraint = new ActivityWaitConstraintOneAllowed();
        constraintManager.addConstraint(constraint, ConstraintManager.Priority.CRITICAL);
        VehicleRoutingAlgorithm algorithm = Jsprit.Builder.newInstance(problem).setStateAndConstraintManager(stateManager, constraintManager).buildAlgorithm();
        Jsprit.createAlgorithm(problem);
        algorithm.setMaxIterations(100);
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

        int counter = 0;
        List<TourActivity> activities = ((List<VehicleRoute>) bestSolution.getRoutes()).get(0).getActivities();
        int activityCounter = 0;
        if (bestSolution.getUnassignedJobs().isEmpty()) {
            for (TourActivity a : activities) {
                long timeForStop = match.getTypeOfGrouping() == Requesttype.DRIVETOUNI && activityCounter == activities.size() - 1 || match.getTypeOfGrouping() == Requesttype.DRIVEHOME && activityCounter == 0 ? 0 : GeneralManager.stopTime;
                if (activityCounter > 0 && a.getEndTime() - a.getArrTime() > timeForStop) {
                    counter++;
                }
                activityCounter++;
            }
        }
        if (counter > 0) {
            bestSolution = CommonFunctionHelper.correctSolution(MobilityType.RIDESHARING, match.getTypeOfGrouping(), bestSolution, vrpBuilder);
        }

        match.setTimeIntervalStart(oldTimeIntervalStart);
        match.setTimeIntervalEnd(oldTimeIntervalEnd);
        List<Agent> agents = new ArrayList<>(match.getAgents());
        agents.add(newAgent);

        if (bestSolution != null && bestSolution.getUnassignedJobs().isEmpty() && rideTimeIsAcceptedByAllAgents(bestSolution, agents, match.getTypeOfGrouping())) {
            this.temporaryMatchToSolution.put(match, bestSolution);
            return true;
        }

        return false;
    }


    public void setDriveStartTime(Match match, VehicleRoutingProblemSolution solution, LocalDateTime oldStartTime, boolean sameAsBefore, boolean sameDropOff) {
        LocalDateTime startTime;

        if (oldStartTime == null) {
            if (match.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                startTime = CommonFunctionHelper.calculateToUniDriveStartTime(match.getDriver().getRequest());
            } else {
                startTime = match.getTimeIntervalStart();
            }
        } else {
            this.matchToSolution.put(match, solution);
            if (!sameAsBefore) {
                if (sameDropOff && match.getDifferentStops().isEmpty()) {
                    LocalDateTime referenceTime = match.getTypeOfGrouping() == Requesttype.DRIVETOUNI ? match.getDriver().getRequest().getFavoredArrivalTime() : match.getDriver().getRequest().getFavoredDepartureTime();
                    long minutesBetweenAgentPreferredTimeAndMatchInterval = ChronoUnit.MINUTES.between(referenceTime, getStartTimeForNoStops(match, referenceTime));
                    if (match.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                        referenceTime = CommonFunctionHelper.calculateToUniDriveStartTime(match.getDriver().getRequest());
                    }
                    startTime = minutesBetweenAgentPreferredTimeAndMatchInterval < 0 ? referenceTime.minusMinutes(Math.abs(minutesBetweenAgentPreferredTimeAndMatchInterval)) : referenceTime.plusMinutes(minutesBetweenAgentPreferredTimeAndMatchInterval);
                } else {
                    VehicleRoute route = ((List<VehicleRoute>) solution.getRoutes()).get(0);
                    if (match.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                        startTime = match.getTimeIntervalEnd().minusMinutes((long) (1000 - route.getEnd().getArrTime())).minusMinutes((long) solution.getCost());
                    } else {
                        startTime = match.getTimeIntervalStart().plusMinutes(((long) route.getActivities().get(0).getEndTime()));
                    }
                }
            } else {
                startTime = oldStartTime;
            }
        }

        if(!startTime.isAfter(this.sortedTimes.get(0))){
            int f = 2;
        }

        this.matchToStartTime.put(match, startTime);
        List<Match> matchList = new ArrayList<>();
        if (this.timeToMatch.containsKey(startTime)) {
            matchList = this.timeToMatch.get(startTime);
        }
        matchList.add(match);
        this.timeToMatch.put(startTime, matchList);
        if(!this.sortedTimes.contains(startTime)){
            CommonFunctionHelper.updateSortedTimes(this.sortedTimes,startTime,false);
        }
    }


    public double getDistanceToDriverInMeters(Request request, Match match) {
        GHPoint ghPointStart = new GHPoint(request.getHomePosition().getLatitude(), request.getHomePosition().getLongitude());
        GHPoint ghPointEnd;
        if (request.getRequesttype() == Requesttype.DRIVETOUNI) {
            ghPointEnd = new GHPoint(match.getStartPosition().getLatitude(), match.getStartPosition().getLongitude());
        } else {
            ghPointEnd = new GHPoint(match.getEndPosition().getLatitude(), match.getEndPosition().getLongitude());
        }
        GHRequest ghRequest = new GHRequest(ghPointStart, ghPointEnd).setProfile("foot").setLocale(Locale.GERMANY);

        GHResponse rsp = ModeExecutionManager.graphHopper.route(ghRequest);
        ResponsePath path = rsp.getBest();
        return path.getDistance();
    }

    public LocalDateTime getStartTimeForNoStops(Match match, LocalDateTime referenceTime) {
        LocalDateTime fittingTime;
        if (CommonFunctionHelper.isOverlapping(referenceTime, referenceTime, match.getTimeIntervalStart(), match.getTimeIntervalEnd())) {
            fittingTime = referenceTime;
        } else {
            fittingTime = Math.abs(ChronoUnit.MINUTES.between(match.getTimeIntervalStart(), referenceTime)) < Math.abs(ChronoUnit.MINUTES.between(referenceTime, match.getTimeIntervalEnd())) ? match.getTimeIntervalStart() : match.getTimeIntervalEnd();
        }
        return fittingTime;
    }


    public boolean rideTimeIsAcceptedByAllAgents(VehicleRoutingProblemSolution solution, List<Agent> agents, Requesttype requesttype) {
        Map<Agent, Double> agentToTime = new HashMap<>();
        for (Agent agent : agents) {
            agentToTime.put(agent, 0.0);
        }

        VehicleRoute route = ((List<VehicleRoute>) solution.getRoutes()).get(0);
        List<Location> locations = new ArrayList<>();
        Map<Location, Double> locationToStopTime = new HashMap<>();
        for (TourActivity activity : route.getActivities()) {
            locations.add(activity.getLocation());
            locationToStopTime.put(activity.getLocation(), activity.getOperationTime());
        }
        locations.add(route.getEnd().getLocation());
        Location firstLocation = route.getStart().getLocation();
        Location secondLocation;
        for (Location location : locations) {
            secondLocation = location;
            List<Location> list = new ArrayList<>();
            list.add(firstLocation);
            list.add(secondLocation);
            double time = ModeExecutionManager.timeMap.get(list);
            if (locationToStopTime.containsKey(secondLocation)) {
                time += locationToStopTime.get(secondLocation);
            }
            for (Agent agent : agents) {
                if (agentToTime.containsKey(agent)) {
                    agentToTime.put(agent, agentToTime.get(agent) + time);
                    if (Coordinate.coordinateTheSameAsLocation(agent.getRequest().getDropOffPosition(), secondLocation)) {
                        if (requesttype == Requesttype.DRIVETOUNI) {
                            long stopTime = 0;
                            if (!secondLocation.equals(route.getEnd().getLocation())) {
                                stopTime = GeneralManager.stopTime;
                            }
                            if (agentToTime.get(agent) - stopTime > agent.getWillingToRideInMinutes()) {
                                return false;
                            }
                            agentToTime.remove(agent);
                        } else {
                            agentToTime.put(agent, 0.0);
                        }
                    }
                }
            }
            firstLocation = secondLocation;
        }
        for (Agent agent : agentToTime.keySet()) {
            if (agentToTime.get(agent) > agent.getWillingToRideInMinutes()) {
                return false;
            }
        }
        return true;
    }


    public void handleLostPerson(Request request) {
        if(GeneralManager.handleLost){
            ResponsePath path = CommonFunctionHelper.getSimpleBestGraphhopperPath(this.graphHopper,request.getDropOffPosition(),request.getHomePosition());
            long timeInMinutes = path.getTime()/60000L;

            Ride ride = new Ride(request.getDropOffPosition(),request.getHomePosition(),request.getDepartureIntervalEnd(),request.getDepartureIntervalEnd().plusMinutes(timeInMinutes),new StandInVehicle(),null,Requesttype.DRIVEHOME,List.of(request.getAgent()));
            calculateMetrics(ride);
        }
    }


    public void letAgentDriveNormally(Agent agent){
        List<Agent> matchAgents = new ArrayList<>();
        matchAgents.add(agent);
        Match toMatch = new Match(matchAgents, new HashMap<>(), agent, agent.getCar(), agent.getHomePosition(), agent.getRequest().getDropOffPosition(), Requesttype.DRIVETOUNI, agent.getRequest().getArrivalIntervalStart(), agent.getRequest().getArrivalIntervalEnd(), MobilityType.RIDESHARING);

        LocalDateTime toStartTime = CommonFunctionHelper.calculateToUniDriveStartTime(agent.getRequest());
        LocalDateTime backStartTime = agent.getRequest().getFavoredDepartureTime();
        Match backMatch = new Match(matchAgents, new HashMap<>(), agent, agent.getCar(), agent.getRequest().getDropOffPosition(), agent.getHomePosition(), Requesttype.DRIVEHOME, agent.getRequest().getDepartureIntervalStart(), agent.getRequest().getDepartureIntervalEnd(), MobilityType.RIDESHARING);
        List<Match> matches = new ArrayList<>();
        if (timeToMatch.containsKey(toStartTime)) {
            matches = timeToMatch.get(toStartTime);
        }
        matches.add(toMatch);
        timeToMatch.put(toStartTime, matches);
        matchToStartTime.put(toMatch, toStartTime);
        CommonFunctionHelper.updateSortedTimes(this.sortedTimes,toStartTime,false);
        matches = new ArrayList<>();
        if (timeToMatch.containsKey(backStartTime)) {
            matches = timeToMatch.get(backStartTime);
        }
        matches.add(backMatch);
        timeToMatch.put(backStartTime, matches);
        matchToStartTime.put(backMatch, backStartTime);
        CommonFunctionHelper.updateSortedTimes(this.sortedTimes,backStartTime,false);
    }


    public List<Match> getFinishedMatches() {
        return finishedMatches;
    }

    public List<Agent> getDrivers() {
        return drivers;
    }

    public Map<Agent, Request> getLost() {
        return lost;
    }


}