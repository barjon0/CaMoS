package camos.mode_execution.mobilitymodels;

import camos.mode_execution.Agent;
import camos.mode_execution.groupings.Match;
import camos.mode_execution.groupings.Ride;
import camos.mode_execution.mobilitymodels.modehelpers.MaximumMatching;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.util.shapes.GHPoint;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingDouble;

public class SwitchDriver extends MobilityMode{
    @Override           //receives list of agents with requests of same day
    public void prepareMode(List<Agent> agents) {
        List<List<Agent>> bubblesTo = new ArrayList<>();
        List<List<Agent>> bubblesFrom = new ArrayList<>();

        agents = agents.stream().sorted(Comparator.comparing(a -> a.getRequest().getFavoredArrivalTime())).collect(Collectors.toList());
        LocalDateTime latestTime = LocalDateTime.MIN;
        int i = -1;
        for (Agent agent : agents) {
            LocalDateTime arrivTime = roundDownTo30(agent.getRequest().getFavoredArrivalTime());
            if (arrivTime.isAfter(latestTime)) {
                latestTime = arrivTime;
                bubblesTo.add(new ArrayList<>());
                i++;
            }
            bubblesTo.get(i).add(agent);
        }

        agents = agents.stream().sorted(Comparator.comparing(a -> a.getRequest().getFavoredDepartureTime())).collect(Collectors.toList());
        latestTime = LocalDateTime.MIN;
        i = -1;
        for (Agent agent : agents) {
            LocalDateTime leavTime = roundUpTo30(agent.getRequest().getFavoredDepartureTime());
            if (leavTime.isAfter(latestTime)) {
                latestTime = leavTime;
                bubblesFrom.add(new ArrayList<>());
                i++;
            }
            bubblesFrom.get(i).add(agent);
        }

        //make Cluster call
        List<Match> clustersTo = new ArrayList<>();
        List<Match> clustersFrom = new ArrayList<>();
        bubblesTo.forEach(bubble -> clustersTo.addAll(makeCluster(bubble, true)));
        bubblesFrom.forEach(bubble -> clustersFrom.addAll(makeCluster(bubble, false)));

        List<List<Match>> matching = MaximumMatching.getMatching(clustersTo, clustersFrom);
        List<List<Match>> finMatching = new ArrayList<>(matching);

        matching.addAll(repairRest(clustersTo, true));
        matching.addAll(repairRest(clustersFrom, false));

        //find driver
        finMatching.forEach(tuple -> {
            List<Agent> intersect = tuple.get(0).getPossDrivers().stream().filter(tuple.get(1).getPossDrivers()::contains).toList();
            Optional<Agent> driver = intersect.stream().max(comparingDouble(Agent::getDistanceToTarget));
            tuple.get(0).setSelectedDriver(driver.get());
            tuple.get(1).setSelectedDriver(driver.get());
        });
    }

    private List<List<Match>> repairRest(List<Match> clusters, boolean isToWork) {
        List<List<Match>> result = new ArrayList<>();

        clusters.stream().filter(m -> m.getPartner() == null).forEach(m1 -> {
            int maxSize = 0;
            Match opposingMatchMax = null;
            List<Agent> possList = new ArrayList<>();
            for (Agent a: m1.getPossDrivers()) {
                Match oppMatch;
                if (isToWork) {
                    oppMatch = a.getTeamOfAgentFrom();
                } else {
                    oppMatch = a.getTeamOfAgentTo();
                }
                List<Agent> intersect = m1.getAgents().stream().filter(oppMatch.getAgents()::contains).toList();
                if (intersect.size() > maxSize) {
                    maxSize = intersect.size();
                    opposingMatchMax = oppMatch;
                    possList = intersect;
                }
            }
            opposingMatchMax.removeFromTeam(possList);
            Match nextMatch = new Match(possList, !isToWork);
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

    private LocalDateTime roundDownTo30(LocalDateTime dateTime) {
        int minutesMinus = dateTime.getMinute() % 30;
        return dateTime.minusMinutes(minutesMinus);
    }
    private LocalDateTime roundUpTo30(LocalDateTime dateTime) {
        int minutesPlus = dateTime.getMinute() % 30;
        return dateTime.plusMinutes(30 - minutesPlus);
    }

    private List<Match> makeCluster(List<Agent> agents, boolean isToWork) {

        List<Match> clusters = new ArrayList<>(agents.stream().map(a -> {
            List<Agent> aList = new ArrayList<>();
            aList.add(a);
            return new Match(aList, isToWork);
        }).toList());

        List<List<Object>> potential = new ArrayList<>();
        //compute distances between each cluster, add trigram (cluster1, cluster2, distance)
        for (int i = 0; i < clusters.size(); i++) {
            for (int j = i+1; j < clusters.size(); j++) {
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
                while(count < potential.size()) {
                    Match checkM1 = (Match) potential.get(count).get(0);
                    Match checkM2 = (Match) potential.get(count).get(1);
                    if (checkM1 == m1 || checkM1 == m2 || checkM2 == m1 || checkM2 == m2) {
                        if(checkM1 == m2) {
                            potential.get(count).set(0, m1);
                            checkM1 = m1;
                        }
                        if(checkM2 == m2) {
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
/*
    private List<Match> divideTilDoable(Match match) {
        List<Match> res = new ArrayList<>();
        if (checkIfFeasible(match)) {
            res.add(match);
        } else {
            List<Agent> agentList = match.getAgents();
            double maxDist = 0;
            List<Agent> pair = new ArrayList<>();
            for (int i = 0; i < agentList.size(); i++) {
                for (int j = i + 1; j < agentList.size(); j++) {        //Driver zum aufsplitten müssen zusammen kapzität für alle haben, und max Distanz davon
                    double possDist = agentList.get(i).getHomePosition().computeDistance(agentList.get(j).getHomePosition());
                    if (((agentList.get(i).getCar().getSeatCount() + agentList.get(j).getCar().getSeatCount()) >= match.teamCount()) &&
                            possDist >= maxDist) {
                        List<Agent> nextPair = new ArrayList<>();
                        maxDist = possDist;
                        nextPair.add(agentList.get(i));
                        nextPair.add(agentList.get(j));
                        pair = nextPair;
                    }
                }
            }
            Agent firstOne = pair.get(0);
            Agent secondOne = pair.get(1);
            Match m1 = new Match(firstOne, match.isWayToWork());
            Match m2 = new Match(secondOne, match.isWayToWork());
            agentList = agentList.stream().filter(a -> a != firstOne && a != secondOne)
                    .sorted(comparingDouble(a -> Math.min(a.getHomePosition().computeDistance(firstOne.getHomePosition())
                            , a.getHomePosition().computeDistance(secondOne.getHomePosition())))).toList();
            for(Agent a : agentList) {
                if (a.getHomePosition().computeDistance(m1.getCentroid())
                        < a.getHomePosition().computeDistance(m2.getCentroid())) {
                    if (m1.teamCount() + 1 <= Math.max(m1.maxSeats(), a.getCar().getSeatCount())) {
                        List<Agent> addition = new ArrayList<>();
                        addition.add(a);
                        m1.addToTeam(addition);
                    } else {
                        List<Agent> addition = new ArrayList<>();
                        addition.add(a);
                        m2.addToTeam(addition);
                    }
                } else {
                    if (m2.teamCount() + 1 <= Math.max(m2.maxSeats(), a.getCar().getSeatCount())) {
                        List<Agent> addition = new ArrayList<>();
                        addition.add(a);
                        m2.addToTeam(addition);
                    } else {
                        List<Agent> addition = new ArrayList<>();
                        addition.add(a);
                        m1.addToTeam(addition);
                    }
                }
            }
            res.addAll(divideTilDoable(m1));
            res.addAll(divideTilDoable(m2));
        }
        return res;
    }
*/
    private List<List<Agent>> getPermut(List<Agent> agents) {
        List<List<Agent>> res = new ArrayList<>();
        if (agents.size() == 1) {
            List<Agent> only = new ArrayList<>();
            only.add(agents.get(0));
            res.add(only);
            return res;
        }
        for (int i = 0; i < agents.size(); i++) {
            int finalI = i;
            List<Agent> slice = agents.stream().filter(a -> a != agents.get(finalI)).toList();
            for (List<Agent> residual : getPermut(slice)) {
                residual.add(0, agents.get(i));
                res.add(residual);
            }
        }
        return res;
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
            List<List<Agent>> permutations = getPermut(possList.stream().filter(a -> a != driver).toList());
            for (List<Agent> permut : permutations) {
                List<Agent> permuList = new ArrayList<>(permut);
                permuList.add(0, driver);
                List<GHPoint> pointList = new ArrayList<>();
                for (Agent pass : permut) {
                    pointList.add(new GHPoint(pass.getHomePosition().getLatitude(), pass.getHomePosition().getLongitude()));
                }
                pointList.add(new GHPoint(driver.getRequest().getDropOffPosition().getLatitude(), driver.getRequest().getDropOffPosition().getLongitude()));
                double[] totalTraveltime = new double[permuList.size()];
                /*
                if (match1.isWayToWork()) {
                    Optional<LocalDateTime> earliest = permuList.stream().map(a -> a.getRequest().getArrivalIntervalEnd()).min(LocalDateTime::compareTo);
                    for (int i = 0; i < permuList.size(); i++) {
                        totalTraveltime[i] = (double) Duration.between(earliest.get(), permuList.get(i).getRequest().getArrivalIntervalEnd()).toMinutes();
                    }
                } else {
                    Optional<LocalDateTime> latest = permuList.stream().map(a -> a.getRequest().getDepartureIntervalStart()).max(LocalDateTime::compareTo);
                    for (int i = 0; i < permuList.size(); i++) {
                        totalTraveltime[i] = (double) Duration.between(latest.get(), permuList.get(i).getRequest().getDepartureIntervalStart()).toMinutes();
                    }
                }
                */
                for (int i = 0; i < pointList.size() - 1; i++) {
                    List<GHPoint> twoPoints = new ArrayList<>();
                    twoPoints.add(pointList.get(i));
                    twoPoints.add(pointList.get(i+1));
                    GHRequest req = new GHRequest(twoPoints);
                    req.setProfile("car");
                    GHResponse resp = graphHopper.route(req);
                    double timeSec = resp.getBest().getInstructions().get(0).getTime();
                    for (int j = 0; j < i + 1; j++) {
                        totalTraveltime[j] = totalTraveltime[j] + (timeSec / 60) ;
                    }
                }
                boolean hurt = false;
                for (int i = 0; i < permuList.size(); i++) {
                    if(totalTraveltime[i] > permuList.get(i).getMaxTravelTimeInMinutes()) {
                        hurt = true;
                        break;
                    }
                }
                if(!hurt) {
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

    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public boolean checkIfConstraintsAreBroken(List<Agent> agents) {
        return false;
    }

    @Override
    public void writeResultsToFile() {

    }

    @Override
    public List<Ride> returnFinishedRides() {
        return null;
    }
}
