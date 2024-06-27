package camos.mode_execution.mobilitymodels;

import camos.GeneralManager;
import camos.mode_execution.Agent;
import camos.mode_execution.Coordinate;
import camos.mode_execution.Requesttype;
import camos.mode_execution.carmodels.Vehicle;
import camos.mode_execution.groupings.Match;
import camos.mode_execution.groupings.Ride;
import camos.mode_execution.mobilitymodels.modehelpers.CommonFunctionHelper;
import camos.mode_execution.mobilitymodels.modehelpers.IntervalGraph;
import camos.mode_execution.mobilitymodels.modehelpers.MaximumMatching;
import com.graphhopper.ResponsePath;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;

import java.io.File;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static java.util.Comparator.comparingDouble;

public class SwitchDriver extends SDCTSP {

    List<Match> resultMatch;

    public SwitchDriver() {
        super();
        this.resultMatch = new ArrayList<>();
    }

    @Override           //receives list of agents with requests of same day
    public void prepareMode(List<Agent> agents) {
        this.agents = agents;
        //make greedy time bubbles
        List<DefaultUndirectedGraph<Agent, DefaultEdge>> graphs = IntervalGraph.buildIntervalGraph(agents);
        List<List<Agent>> bubblesTo = IntervalGraph.cliqueCover(graphs.get(0));
        List<List<Agent>> bubblesFrom = IntervalGraph.cliqueCover(graphs.get(1));

        //make Cluster call
        List<Match> clustersTo = new ArrayList<>();
        List<Match> clustersFrom = new ArrayList<>();
        bubblesTo.forEach(bubble -> clustersTo.addAll(makeCluster(bubble, Requesttype.DRIVETOUNI)));
        bubblesFrom.forEach(bubble -> clustersFrom.addAll(makeCluster(bubble, Requesttype.DRIVEHOME)));

        List<List<Match>> matching = MaximumMatching.getMatching(clustersTo, clustersFrom);

        matching.addAll(repairRest(clustersTo, Requesttype.DRIVETOUNI));
        matching.addAll(repairRest(clustersFrom, Requesttype.DRIVEHOME));

        //find driver
        matching.forEach(tuple -> {
            List<Agent> intersect = tuple.get(0).getPossDrivers().stream().filter(tuple.get(1).getPossDrivers()::contains).toList();
            Optional<Agent> driver = intersect.stream().max(comparingDouble(Agent::getDistanceToTarget));
            tuple.get(0).setDriver(driver.get());
            tuple.get(1).setDriver(driver.get());
        });
        resultMatch = new ArrayList<>(matching.stream()
                .flatMap(List::stream)
                .toList());
    }

    private List<List<Match>> repairRest(List<Match> clusters, Requesttype isToWork) {
        List<List<Match>> result = new ArrayList<>();

        clusters.stream().filter(m -> m.getPartner() == null).forEach(m1 -> {
            int maxSize = 0;
            Match opposingMatchMax = null;
            List<Agent> possList = new ArrayList<>();
            for (Agent a : m1.getPossDrivers()) {
                Match oppMatch;
                if (isToWork == Requesttype.DRIVETOUNI) {
                    oppMatch = (Match) a.getTeamOfAgentFrom();
                } else {
                    oppMatch = (Match) a.getTeamOfAgentTo();
                }
                List<Agent> intersect = m1.getAgents().stream().filter(oppMatch.getAgents()::contains).toList();
                if (intersect.size() > maxSize) {
                    maxSize = intersect.size();
                    opposingMatchMax = oppMatch;
                    possList = intersect;
                }
            }
            opposingMatchMax.removeFromTeam(possList);
            Match nextMatch = new Match(possList, isToWork.getOpposite());
            possList.forEach(a -> {
                if (m1.getPossDrivers().contains(a)) {
                    nextMatch.addPossDriver(a);
                }
            });
            nextMatch.setPartner(m1);
            m1.setPartner(nextMatch);
            List<Match> pair = new ArrayList<>();
            pair.add(m1);
            pair.add(nextMatch);
            result.add(pair);
        });
        return result;
    }

    private List<Match> makeCluster(List<Agent> agents, Requesttype isToWork) {

        List<Match> clusters = new ArrayList<>(agents.stream().map(a -> {
            List<Agent> aList = new ArrayList<>();
            aList.add(a);
            return new Match(aList, isToWork);
        }).toList());

        List<List<Object>> potential = new ArrayList<>();
        //compute distances between each cluster, add trigram (cluster1, cluster2, distance)
        for (int i = 0; i < clusters.size(); i++) {
            for (int j = i + 1; j < clusters.size(); j++) {
                Match m1 = clusters.get(i);
                Match m2 = clusters.get(j);
                if (checkIfSuitable(m1, m2)) {
                    List<Object> trigram = new ArrayList<>();
                    trigram.add(m1);
                    trigram.add(m2);
                    trigram.add(m1.getCentroid().computeDistance(m2.getCentroid()));
                    potential.add(trigram);
                }
            }
        }

        //keep merging clusters (smallest dist) first until no options left
        int count = 0;
        potential = potential.stream().sorted(comparingDouble(obj -> (double) obj.get(2))).toList();
        while (count < potential.size()) {
            Match m1 = (Match) potential.get(count).get(0);
            Match m2 = (Match) potential.get(count).get(1);
            count = count + 1;
            List<Agent> feasibleDrivers = getFeasibleDrivers(m1, m2);
            if (!feasibleDrivers.isEmpty()) {
                m1.addToTeam(m2.getAgents());
                clusters.remove(m2);
                m1.setPossDrivers(feasibleDrivers);
                List<List<Object>> changedPot = new ArrayList<>();
                while (count < potential.size()) {
                    Match checkM1 = (Match) potential.get(count).get(0);
                    Match checkM2 = (Match) potential.get(count).get(1);
                    if (checkM1 == m1 || checkM1 == m2 || checkM2 == m1 || checkM2 == m2) {
                        if (checkM1 == m2) {
                            potential.get(count).set(0, m1);
                            checkM1 = m1;
                        }
                        if (checkM2 == m2) {
                            potential.get(count).set(1, m1);
                            checkM2 = m1;
                        }
                        if (checkIfSuitable(checkM1, checkM2)) {
                            potential.get(count).set(2, checkM1.getCentroid().computeDistance(checkM2.getCentroid()));
                            changedPot.add(potential.get(count));
                        }
                    } else {
                        changedPot.add(potential.get(count));
                    }
                    count = count + 1;
                }
                potential = changedPot.stream().sorted(comparingDouble(obj -> (double) obj.get(2))).toList();
                //update others
            }
        }

        return clusters;
    }


    private List<Agent> getFeasibleDrivers(Match match1, Match match2) {
        //checks if there is a permutation for which no constraints are broken, add poss. Driver to result
        List<Agent> result = new ArrayList<>();
        List<Agent> possList = new ArrayList<>(match1.getAgents());
        possList.addAll(match2.getAgents());
        List<Agent> possDriverList = new ArrayList<>(match1.getPossDrivers());
        possDriverList.addAll(match2.getPossDrivers());
        possDriverList = possDriverList.stream().filter(a -> a.getCar().getSeatCount() >= possList.size()).toList();

        for (Agent driver : possDriverList) {
            List<List<Agent>> permutations = CommonFunctionHelper.getPermut(possList.stream().filter(a -> a != driver).toList());
            for (List<Agent> permut : permutations) {
                List<Agent> permuList = new ArrayList<>(permut);
                permuList.add(0, driver);
                Long time = CommonFunctionHelper.checkFeasTime(permuList);
                if (time != null) {
                    result.add(driver);
                    break;
                }
            }
        }
        return result;
    }

    public boolean checkIfSuitable(Match m1, Match m2) {
        return (m1.teamCount() + m2.teamCount()) <= Math.max(m1.maxSeats(), m2.maxSeats());
    }

    @Override
    public void startMode() {
        //computes concrete Routes for found Matches/Teams
        for (Match m : resultMatch) {
            List<Agent> residual = m.getAgents().stream().filter(d -> d != m.getDriver()).toList();
            long shortestDriveTime = 999999999;
            List<Agent> bestOrder = null;
            List<List<Agent>> permutations;
            if (m.getAgents().size() != 1) {
                permutations = CommonFunctionHelper.getPermut(residual);
            } else {
                permutations = new ArrayList<>();
                permutations.add(new ArrayList<>());
            }
                for (List<Agent> permut : permutations) {
                    permut.add(0, m.getDriver());
                    Long driveTimeInMinutes = CommonFunctionHelper.checkFeasTime(permut);
                    if (driveTimeInMinutes != null && driveTimeInMinutes < shortestDriveTime) {
                        shortestDriveTime = driveTimeInMinutes;
                        bestOrder = permut;
                    }
                }
                if (bestOrder == null) {
                    throw new IllegalStateException("No order has been found at later stage");
                }

            Coordinate startPosition;
            Coordinate destination;
            LocalDateTime startTime;
            LocalDateTime endTime;
            Vehicle vehicle;
            Requesttype req;
            if (m.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                startPosition = m.getDriver().getHomePosition();
                destination = m.getDriver().getRequest().getDropOffPosition();
                endTime = CommonFunctionHelper
                        .calculateInterval(m.getAgents(), m.getTypeOfGrouping()).get(0);


                startTime = endTime.minusMinutes(shortestDriveTime);
                vehicle = m.getDriver().getCar();
                req = m.getTypeOfGrouping();

            } else {
                startPosition = m.getDriver().getRequest().getDropOffPosition();
                destination = m.getDriver().getHomePosition();
                startTime = CommonFunctionHelper
                        .calculateInterval(m.getAgents(), m.getTypeOfGrouping()).get(0);
                endTime = startTime.plusMinutes(shortestDriveTime);
                vehicle = m.getDriver().getCar();
                req = m.getTypeOfGrouping();
                Collections.reverse(bestOrder);
            }
            Ride r = new Ride(startPosition, destination, startTime, endTime, vehicle, m.getDriver(), req, bestOrder);
            computeStops(r);
            rides.add(r);

        }
    }

    @Override
    public void writeResultsToFile() {
        calculateMetrics();
        List<String> dataSingle = createSingleData();
        List<String> dataAccum = createAccumData();

        File csvOutputFile = new File("accumResultsSwitch.csv");
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            for(String data : dataAccum){
                pw.println(data);
            }
        }catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
        File csvOutputFile2 = new File("singleResultsSwitch.csv");
        try (PrintWriter pw = new PrintWriter(csvOutputFile2)) {
            for(String data : dataSingle){
                pw.println(data);
            }
        }catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "SwitchDriver";
    }
}