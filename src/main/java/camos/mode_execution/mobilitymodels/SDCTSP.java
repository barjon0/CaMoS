package camos.mode_execution.mobilitymodels;

import camos.GeneralManager;
import camos.mode_execution.Agent;
import camos.mode_execution.Coordinate;
import camos.mode_execution.Requesttype;
import camos.mode_execution.groupings.Ride;
import camos.mode_execution.groupings.Stop;
import camos.mode_execution.groupings.Stopreason;
import camos.mode_execution.mobilitymodels.modehelpers.CommonFunctionHelper;
import com.graphhopper.ResponsePath;

import java.io.File;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public abstract class SDCTSP extends MobilityMode {

    Map<Agent,List<Double>> emissionsBoth;
    Map<Agent,List<Double>> costsBoth;
    Map<Agent,List<Double>> kmTravelledBoth;
    Map<Agent,List<Double>> minutesTravelledBoth;

    Map<Ride,Double> emissionsRide;
    Map<Ride,Double> costsRide;
    Map<Ride,Double> kmTravelledRide;
    Map<Ride,Double> minutesRide;

    public SDCTSP() {
        costsBoth = new HashMap<>();
        emissionsBoth = new HashMap<>();
        kmTravelledBoth = new HashMap<>();
        minutesTravelledBoth = new HashMap<>();
        emissionsRide = new HashMap<>();
        costsRide = new HashMap<>();
        kmTravelledRide = new HashMap<>();
        minutesRide = new HashMap<>();
    }

    @Override
    public void prepareMode(List<Agent> agents) throws Exception {

    }

    @Override
    public void startMode() {

    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public boolean checkIfConstraintsAreBroken(List<Agent> agents) {
        //checks if everyone has exactly one ride to and from, intervals fit, capacities fit, drivers match,
        //does not check if maximum drive time is held up
        int[] checkingTo = new int[agents.size()];
        int[] checkingFrom = new int[agents.size()];
        int[] checkingToDriver = new int[agents.size()];
        int[] checkingFromDriver = new int[agents.size()];
        for (Ride ride : rides) {
            List<LocalDateTime> interval = CommonFunctionHelper.calculateInterval(
                    ride.getAgents(), ride.getTypeOfGrouping());
            if (interval == null) {
                return true;
            }
            if (ride.getDriver().getCar().getSeatCount() < ride.getAgents().size()) {
                return true;
            }
            if (ride.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                for (Agent a : ride.getAgents()) {
                    checkingTo[agents.indexOf(a)] += 1;
                }
                Agent driver = ride.getDriver();
                checkingToDriver[agents.indexOf(driver)] += 1;
            } else {
                for (Agent a : ride.getAgents()) {
                    checkingFrom[agents.indexOf(a)] += 1;
                }
                Agent driver = ride.getDriver();
                checkingFromDriver[agents.indexOf(driver)] += 1;
            }
        }
        for (int i = 0; i < agents.size(); i++) {
            if (checkingTo[i] != 1 || checkingFrom[i] != 1 ||
                    (checkingToDriver[i] != checkingFromDriver[i])) {
                return true;
            }
        }

        return false;
    }

    public List<String> createAccumData() {
        List<String> dataLines = new ArrayList<>();
        dataLines.add("Ride Id, TypeOfRide, Kilometer, Minuten, CO2, Kosten, Fahrer, Agenten");
        rides = rides.stream().sorted(Comparator.comparing(Ride::getTypeOfGrouping)).toList();
        for(Ride r : rides){
            dataLines.add(r.getId() + "," + r.getTypeOfGrouping().toString() + "," + String.format(Locale.US,"%.2f", kmTravelledRide.get(r)) + "," + String.format(Locale.US,"%.2f", minutesRide.get(r)) + "," + String.format(Locale.US,"%.2f", emissionsRide.get(r)) + "," + String.format(Locale.US,"%.2f", costsRide.get(r)) + "," + r.getDriver().getId() + "," + r.getAgents().stream().map(Agent::getId).toList());
        }
        return dataLines;
    }

    public List<String> createSingleData() {
        List<String> dataLines2 = new ArrayList<>();
        dataLines2.add("Agent Id, Hin-Kilometer,Hin-Minuten,Hin-CO2,Hin-Kosten,Rück-Kilometer,Rück-Minuten,Rück-CO2,Rück-Kosten");
        for(Agent a : agents) {
            dataLines2.add(a.getId() + "," + String.format(Locale.US, "%.2f", kmTravelledBoth.get(a).get(0)) + "," + String.format(Locale.US, "%.2f", minutesTravelledBoth.get(a).get(0)) + "," + String.format(Locale.US,"%.2f", emissionsBoth.get(a).get(0)) + ","
                    + String.format(Locale.US,"%.2f", costsBoth.get(a).get(0)) + "," + String.format(Locale.US,"%.2f", kmTravelledBoth.get(a).get(1)) + "," + String.format(Locale.US,"%.2f", minutesTravelledBoth.get(a).get(1)) + "," + String.format(Locale.US,"%.2f", emissionsBoth.get(a).get(1)) + ","
                    + String.format(Locale.US,"%.2f", costsBoth.get(a).get(1)));
        }
        return dataLines2;
    }

    @Override
    public List<Ride> returnFinishedRides() {
        return rides;
    }

    public void calculateMetrics() {
        for(Ride r : rides) {
            emissionsRide.put(r, r.getDistanceCovered()*r.getDriver().getCar().getConsumptionPerKm()
                    *r.getDriver().getCar().getCo2EmissionPerLiter());
            costsRide.put(r, r.getDistanceCovered()*r.getDriver().getCar().getConsumptionPerKm()
                    *r.getDriver().getCar().getPricePerLiter());
            kmTravelledRide.put(r, r.getDistanceCovered());
            minutesRide.put(r, (double) Duration.between(
                    r.getStartTime(), r.getEndTime()).toMinutes());
            /*
            for(Agent a : r.getAgents()) {
                if(agentToRides.containsKey(a)) {
                    if(r.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                        agentToRides.get(a).add(0, r);
                    } else {
                        agentToRides.get(a).add(1, r);
                    }
                } else {
                    List<Ride> rideList = new ArrayList<>();
                    rideList.add(r);
                    agentToRides.put(a, rideList);
                }

            }
             */
        }
    }
    public void computeStops(Ride ride) {
        double[] distPerAgent = new double[ride.getAgents().size()];
        double[] timePerAgent = new double[ride.getAgents().size()];
        List<Stop> stopList = new ArrayList<>();
        LocalDateTime lastStopTime = ride.getStartTime();
        Coordinate lastPosition = ride.getStartPosition();
        Stop lastStop = null;

        if(ride.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
            for (int i = 1; i < ride.getAgents().size(); i++) {
                if (lastStop != null && lastStop.getStopCoordinate() == ride.getAgents().get(i).getHomePosition()) {
                    lastStop.getPersonsInQuestion().add(ride.getAgents().get(i));
                }
                ResponsePath pw = CommonFunctionHelper
                        .getSimpleBestGraphhopperPath(lastPosition, ride.getAgents().get(i).getHomePosition());
                for (int j = 0; j < i; j++) {
                    distPerAgent[j] += (pw.getDistance() / 1000.0);
                    timePerAgent[j] += (pw.getTime() / 60000.0);
                }
                LocalDateTime arrivTime = lastStopTime.plusMinutes(pw.getTime() / 60000L);
                lastStopTime= arrivTime.plusMinutes(GeneralManager.stopTime);
                lastPosition = ride.getAgents().get(i).getHomePosition();
                List<Agent> agentList = new ArrayList<>();
                agentList.add(ride.getAgents().get(i));
                lastStop = new Stop(arrivTime, lastStopTime, lastPosition, Stopreason.getReason(ride.getTypeOfGrouping()), agentList);
                stopList.add(lastStop);
            }
            ResponsePath lastPart = CommonFunctionHelper
                    .getSimpleBestGraphhopperPath(ride.getAgents()
                            .get(ride.getAgents().size() - 1).getHomePosition(), ride.getEndPosition());

            for (int j = 0; j < distPerAgent.length; j++) {
                distPerAgent[j] += (lastPart.getDistance() / 1000.0);
                timePerAgent[j] += (lastPart.getTime() / 60000.0);
            }
            ride.setDistanceCovered(distPerAgent[0]);
        } else {
            for (int i = 0; i < ride.getAgents().size() - 1; i++) {
                if (lastStop != null && lastStop.getStopCoordinate() == ride.getAgents().get(i).getHomePosition()) {
                    lastStop.getPersonsInQuestion().add(ride.getAgents().get(i));
                }
                ResponsePath pw = CommonFunctionHelper
                        .getSimpleBestGraphhopperPath(lastPosition, ride.getAgents().get(i).getHomePosition());
                for (int j = distPerAgent.length - 1; j > i - 1 ; j--) {
                    distPerAgent[j] += (pw.getDistance() / 1000.0);
                    timePerAgent[j] += (pw.getTime() / 60000.0);
                }
                LocalDateTime arrivTime = lastStopTime.plusMinutes(pw.getTime() / 60000L);
                lastStopTime= arrivTime.plusMinutes(GeneralManager.stopTime);
                lastPosition = ride.getAgents().get(i).getHomePosition();
                List<Agent> agentList = new ArrayList<>();
                agentList.add(ride.getAgents().get(i));
                lastStop = new Stop(arrivTime, lastStopTime, lastPosition, Stopreason.getReason(ride.getTypeOfGrouping()), agentList);
                stopList.add(lastStop);
            }
            Coordinate secondLast;
            if (ride.getAgents().size() == 1) {
                secondLast = ride.getStartPosition();
            } else {
                secondLast = ride.getAgents().get(ride.getAgents().size() - 2).getHomePosition();
            }
            ResponsePath lastPart = CommonFunctionHelper
                    .getSimpleBestGraphhopperPath(secondLast, ride.getEndPosition());

            distPerAgent[distPerAgent.length - 1] += (lastPart.getDistance() / 1000.0);
            timePerAgent[timePerAgent.length - 1] += (lastPart.getTime() / 60000.0);

            ride.setDistanceCovered(distPerAgent[distPerAgent.length - 1]);
        }

        for (int i = 0; i < ride.getAgents().size(); i++) {
            Agent agent = ride.getAgents().get(i);
            double emmison = distPerAgent[i]*agent.getCar().getConsumptionPerKm()*agent.getCar().getCo2EmissionPerLiter();
            double kmTravelled = distPerAgent[i];
            double minutesTravelled = timePerAgent[i];
            double cost = distPerAgent[i]*agent.getCar().getConsumptionPerKm()*agent.getCar().getPricePerLiter();
            if(emissionsBoth.containsKey(agent)) {
               if(ride.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                   emissionsBoth.get(agent).add(0, emmison);
                   kmTravelledBoth.get(agent).add(0, kmTravelled);
                   minutesTravelledBoth.get(agent).add(0, minutesTravelled);
                   costsBoth.get(agent).add(0, cost);
               } else {
                   emissionsBoth.get(agent).add(emmison);
                   kmTravelledBoth.get(agent).add(kmTravelled);
                   minutesTravelledBoth.get(agent).add(minutesTravelled);
                   costsBoth.get(agent).add(cost);
               }
            } else {
                List<Double> emList = new ArrayList<>();
                emList.add(emmison);
                List<Double> kmList = new ArrayList<>();
                kmList.add(kmTravelled);
                List<Double> mintravList = new ArrayList<>();
                mintravList.add(minutesTravelled);
                List<Double> cosList = new ArrayList<>();
                cosList.add(cost);
                emissionsBoth.put(agent, emList);
                kmTravelledBoth.put(agent, kmList);
                minutesTravelledBoth.put(agent, mintravList);
                costsBoth.put(agent, cosList);
            }

        }
        ride.setStops(stopList);
    }
}
