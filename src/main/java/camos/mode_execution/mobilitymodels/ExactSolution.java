package camos.mode_execution.mobilitymodels;

import camos.mode_execution.Agent;
import camos.mode_execution.Coordinate;
import camos.mode_execution.Requesttype;
import camos.mode_execution.carmodels.Vehicle;
import camos.mode_execution.groupings.Ride;
import camos.mode_execution.groupings.RouteSet;
import camos.mode_execution.mobilitymodels.modehelpers.CommonFunctionHelper;
import camos.mode_execution.mobilitymodels.modehelpers.CplexSolver;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class ExactSolution extends SDCTSP{

    List<RouteSet> foundRouteSets;
    Map<Integer, List<RouteSet>> lookUpTo;
    Map<Integer, List<RouteSet>> lookUpFrom;

    public ExactSolution() {
        super();
        lookUpTo = new HashMap<>();
        lookUpFrom = new HashMap<>();
        foundRouteSets = new ArrayList<>();
    }

    @Override
    public void prepareMode(List<Agent> agents) throws Exception {

        this.agents = agents;
        System.out.println("before enumerating " + (System.nanoTime()/ 1_000_000) + "ms");

        for (int i = 0; i < agents.size(); i++) {
            System.out.println("one agent done" + (System.nanoTime()/ 1_000_000) + "ms");
            List<Agent> members = new ArrayList<>();
            members.add(agents.get(i));
            List<Agent> rem = new ArrayList<>(agents);
            rem.remove(i);
            buildUp(members, rem, Requesttype.DRIVETOUNI,
                    CommonFunctionHelper.getTimeInterval(agents.get(i), Requesttype.DRIVETOUNI));
            buildUp(members, rem, Requesttype.DRIVEHOME,
                    CommonFunctionHelper.getTimeInterval(agents.get(i), Requesttype.DRIVEHOME));
        }
        System.out.println("enumarating done " + (System.nanoTime()/ 1_000_000) + "ms");

        foundRouteSets = CplexSolver.solveProblem(lookUpTo.values().stream().flatMap(List::stream)
                .toList(), lookUpFrom.values().stream().flatMap(List::stream)
                .toList(), agents);

        System.out.println("preparing done " + (System.nanoTime()/ 1_000_000) + "ms");

    }



    private void buildUp(List<Agent> members, List<Agent> remaining, Requesttype isToWork, List<LocalDateTime> currInterval) {

        Long minutes = CommonFunctionHelper.checkFeasTime(members);
        Map<Integer, List<RouteSet>> lookUp;
        RouteSet routeSet;
        if (isToWork == Requesttype.DRIVETOUNI) {
            lookUp = lookUpTo;
        } else {
            lookUp = lookUpFrom;
        }
        //check if route is feasible
        if (minutes != null) {
            //find routeset
            boolean found = false;
            int key = getHash(members);
            List<RouteSet> list = lookUp.get(key);
            //check if already list set up
            if (list == null) {
                //create new list
                List<RouteSet> aList = new ArrayList<>();
                lookUp.put(key, aList);
                list = aList;
            } else {
                //go through existing list and check if set is there

                for (RouteSet r : list) {
                    if (r.getDriver() == members.get(0) && new HashSet<>(members).containsAll(r.getMembers())) {
                        //check if permutation found is better, then before
                        if (r.getTimeInMinutes() > minutes) {
                            r.setTimeInMinutes(minutes);
                            r.setOrder(members);
                        }
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                routeSet = new RouteSet(members, members.get(0), isToWork);
                routeSet.setTimeInMinutes(minutes);
                routeSet.setOrder(members);
                list.add(routeSet);
            }
            //check if more people fit in car, then continue recursive call
            if (members.get(0).getCar().getSeatCount() > members.size()) {
                List<Agent> nextRemain = new ArrayList<>(remaining);
                for (int i = 0; i < remaining.size(); i++) {
                    List<Agent> nextRoute = new ArrayList<>(members);
                    nextRoute.add(remaining.get(i));

                    List<LocalDateTime> interval =
                            CommonFunctionHelper.calcDoub(
                                    currInterval, CommonFunctionHelper.getTimeInterval(remaining.get(i), isToWork));
                    //check if time interval fits
                    if (interval != null) {
                        List<Agent> specRemain = new ArrayList<>(nextRemain);
                        specRemain.remove(remaining.get(i));
                        buildUp(nextRoute, specRemain, isToWork, interval);
                    } else {
                        nextRemain.remove(remaining.get(i));
                    }
                }
            }
        }
    }

    private int getHash(List<Agent> members) {
        int value = 0;
        Agent driver = members.get(0);
        members = members.stream().sorted(Comparator.comparing(Agent::hashCode))
                .collect(Collectors.toList());
        for(Agent m: members) {
            value = (value * 31 + m.hashCode()) % 10007;
        }
        return (driver.hashCode() + value) % 10007;
    }
    @Override
    public void startMode() {
        for(RouteSet rs : foundRouteSets) {
            Coordinate startPosition;
            Coordinate destination;
            LocalDateTime startTime;
            LocalDateTime endTime;
            Vehicle vehicle;
            Requesttype req;
            List<Agent> order;
            if (rs.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                startPosition = rs.getDriver().getHomePosition();
                destination = rs.getDriver().getRequest().getDropOffPosition();
                endTime = CommonFunctionHelper
                        .calculateInterval(rs.getAgents(), rs.getTypeOfGrouping()).get(0);
                startTime = endTime.minusMinutes(rs.getTimeInMinutes());
                vehicle = rs.getDriver().getCar();
                req = rs.getTypeOfGrouping();
                order = rs.getOrder();
            } else {
                startPosition = rs.getDriver().getRequest().getDropOffPosition();
                destination = rs.getDriver().getHomePosition();
                startTime = CommonFunctionHelper
                        .calculateInterval(rs.getAgents(), rs.getTypeOfGrouping()).get(0);
                endTime = startTime.plusMinutes(rs.getTimeInMinutes());
                vehicle = rs.getDriver().getCar();
                req = rs.getTypeOfGrouping();
                order = rs.getOrder();
                Collections.reverse(order);
            }
            Ride r = new Ride(startPosition, destination, startTime, endTime, vehicle, rs.getDriver(), req, order);
            rides.add(r);
            computeStops(r);
        }
    }
    @Override
    public String getName() {
        return "ExactSolution";
    }

    @Override
    public void writeResultsToFile() {
        calculateMetrics();
        List<String> dataSingle = createSingleData();
        List<String> dataAccum = createAccumData();

        File csvOutputFile = new File("accumResultsExact.csv");
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            for(String data : dataAccum){
                pw.println(data);
            }
        }catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
        File csvOutputFile2 = new File("singleResultsExact.csv");
        try (PrintWriter pw = new PrintWriter(csvOutputFile2)) {
            for(String data : dataSingle){
                pw.println(data);
            }
        }catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }

}
