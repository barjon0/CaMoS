package camos.mode_execution.mobilitymodels;

import camos.GeneralManager;
import camos.mode_execution.Agent;
import camos.mode_execution.Coordinate;
import camos.mode_execution.Requesttype;
import camos.mode_execution.carmodels.Vehicle;
import camos.mode_execution.groupings.Ride;
import camos.mode_execution.groupings.RouteSet;
import camos.mode_execution.mobilitymodels.modehelpers.CommonFunctionHelper;
import camos.mode_execution.mobilitymodels.modehelpers.CplexSolver;
import camos.mode_execution.mobilitymodels.modehelpers.IntervalGraph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingDouble;

public class ExactSolution extends SDCTSP{

    List<RouteSet> foundRouteSets;
    List<RouteSet> lookUpTo;
    List<RouteSet> lookUpFrom;

    public ExactSolution() {
        super();
        lookUpTo = new ArrayList<>();
        lookUpFrom = new ArrayList<>();
        foundRouteSets = new ArrayList<>();
    }

    @Override
    public void prepareMode(List<Agent> agents) {

        this.agents = agents;
        System.out.println("before enumerating " + (System.nanoTime()/ 1_000_000) + "ms");

        Map<Coordinate, List<Agent>> agentsByTarget = agents.stream()
                .collect(Collectors.groupingBy(a -> a.getRequest().getDropOffPosition()));

        int numberOfCores = Runtime.getRuntime().availableProcessors() - 1;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfCores);

        for(List<Agent> oneTarget : agentsByTarget.values()) {

                System.out.println("starting one Target " + (System.nanoTime() / 1_000_000) + "ms");
                /*
                //get all max Time Cliques -> smaller instance sizes (possible rough spatial cluster before here)
                List<DefaultUndirectedGraph<Agent, DefaultEdge>> graphs  = IntervalGraph.buildIntervalGraph(oneTarget);
                List<List<List<Agent>>> allCliques = new ArrayList<>();
                allCliques.add(IntervalGraph.getAllMaxCliques(graphs.get(0)));
                allCliques.add(IntervalGraph.getAllMaxCliques(graphs.get(1)));
                Requesttype requesttype = Requesttype.DRIVETOUNI;
                */
                List<Future<List<RouteSet>>> futureListTo = new ArrayList<>();
                List<Future<List<RouteSet>>> futureListBack = new ArrayList<>();
                List<List<Agent>> preClusters = makePreclusteringRadial(oneTarget);
                for(List<Agent> preCluster : preClusters) {
                    Callable<List<RouteSet>> listCallable = () -> routeEnumerate(preCluster, Requesttype.DRIVETOUNI);
                    Future<List<RouteSet>> result = executorService.submit(listCallable);
                    futureListTo.add(result);

                    Callable<List<RouteSet>> listCallableBack = () -> routeEnumerate(preCluster, Requesttype.DRIVEHOME);
                    Future<List<RouteSet>> resultBack = executorService.submit(listCallableBack);
                    futureListBack.add(resultBack);
                }
                /*
                for (List<Agent> preCluster : preClusters) {
                    for (int i = 0; i < preCluster.size(); i++) {
                        List<Agent> members = new ArrayList<>();
                        members.add(preCluster.get(i));
                        List<Agent> rem = new ArrayList<>(preCluster);
                        rem.remove(i);
                        buildUp(members, rem, Requesttype.DRIVETOUNI,
                                CommonFunctionHelper.getTimeInterval(preCluster.get(i), Requesttype.DRIVETOUNI));
                        buildUp(members, rem, Requesttype.DRIVEHOME,
                                CommonFunctionHelper.getTimeInterval(preCluster.get(i), Requesttype.DRIVEHOME));
                    }
                }

                 */

            futureListTo.forEach(ls -> {
                try {
                    lookUpTo.addAll(ls.get());
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });

            futureListBack.forEach(ls -> {
                try {
                    lookUpFrom.addAll(ls.get());
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });

            System.out.println("enumarating done for target " + (System.nanoTime() / 1_000_000) + "ms");

            List<RouteSet> sets = CplexSolver.solveProblem(lookUpTo, lookUpFrom, oneTarget);
            if(sets != null) {
                foundRouteSets.addAll(sets);
                lookUpTo = new ArrayList<>();
                lookUpFrom = new ArrayList<>();
                System.out.println("solved for one target");
            } else {
                throw new IllegalStateException("NO solution found");
            }

        }
        executorService.shutdown();
        System.out.println("preparing done " + (System.nanoTime()/ 1_000_000) + "ms");

    }

    private List<RouteSet> routeEnumerate(List<Agent> candidates, Requesttype isToWork) {

        DefaultUndirectedGraph<Agent, DefaultEdge> shareGraphTo =
                buildShareGraph(candidates, isToWork);

        return buildingCliques(shareGraphTo, isToWork);
    }
    private List<RouteSet> buildingCliques(DefaultUndirectedGraph<Agent, DefaultEdge> shareGraph, Requesttype isToWork) {
        List<RouteSet> routeSetList = new ArrayList<>();

        List<Set<Agent>> finalLastCliques = new ArrayList<>();
        shareGraph.vertexSet().forEach(v -> {
            Set<Agent> members = new HashSet<>();
            members.add(v);
            finalLastCliques.add(members);
            long time = CommonFunctionHelper
                    .computeTimeBetweenPoints(v.getHomePosition(), v.getRequest().getDropOffPosition());
            routeSetList.add(new RouteSet(members.stream().toList(), v, time, isToWork));
        });
        List<Set<Agent>> lastCliques = finalLastCliques;

        int k = 1;
        while (!lastCliques.isEmpty()) {
            List<Set<Agent>> nextCliques = new ArrayList<>();
            for(Set<Agent> clique : lastCliques) {
                int finalK = k;
                if(clique.stream().anyMatch(a -> a.getCar().getSeatCount() > finalK)) {
                    Iterator<Agent> iterator = clique.iterator();
                    Set<Agent> candidates = Graphs.neighborSetOf(shareGraph, iterator.next());
                    while(iterator.hasNext() && !candidates.isEmpty()) {
                        Agent nextAgent = iterator.next();
                        candidates = candidates.stream().filter(a ->
                                Graphs.neighborSetOf(shareGraph, nextAgent).contains(a))
                                .collect(Collectors.toSet());
                    }
                    for (Agent candidate : candidates) {
                        //only expand candidate when he has bigger id -> every clique only looked at once
                        if (clique.stream().allMatch(u -> u.getId() < candidate.getId())) {
                            Set<Agent> memberSet = new HashSet<>(clique);
                            memberSet.add(candidate);
                            boolean found = false;
                            List<LocalDateTime> interval =
                                    CommonFunctionHelper.calculateInterval(memberSet.stream().toList(), isToWork);
                            if (interval != null) {
                                List<Agent> possDrivers = memberSet.stream()
                                        .filter(a -> a.getCar().getSeatCount() > finalK).toList();
                                for (Agent driver : possDrivers) {
                                    RouteSet rS = null;
                                    List<Agent> withoutDriver = new ArrayList<>(memberSet);
                                    withoutDriver.remove(driver);
                                    List<List<Agent>> permutations = CommonFunctionHelper.getPermut(withoutDriver);
                                    for (List<Agent> permut : permutations) {
                                        permut.add(0, driver);
                                        Long travelTime = CommonFunctionHelper.checkFeasTime(permut);
                                        if (travelTime != null) {
                                            if (rS == null) {
                                                rS = new RouteSet(permut, driver, travelTime, isToWork);
                                                found = true;
                                                routeSetList.add(rS);
                                            } else {
                                                if (rS.getTimeInMinutes() > travelTime) {
                                                    rS.setTimeInMinutes(travelTime);
                                                    rS.setOrder(permut);
                                                }
                                            }
                                        }
                                    }

                                }
                            }
                            if (found) {
                                nextCliques.add(memberSet);
                            }
                        }
                    }

                }
            }
            lastCliques = nextCliques;
            k += 1;
            System.out.println("CliqueSize is " + k);
        }
        return routeSetList;
    }

    private DefaultUndirectedGraph<Agent, DefaultEdge> buildShareGraph(List<Agent> agents, Requesttype isToWork) {

        DefaultUndirectedGraph<Agent, DefaultEdge> shareGraph =
                new DefaultUndirectedGraph<>(DefaultEdge.class);
        agents.forEach(shareGraph::addVertex);

        for (int i = 0; i < agents.size(); i++) {
            for (int j = i + 1; j < agents.size(); j++) {
                List<Agent> pair = new ArrayList<>();
                pair.add(agents.get(i));
                pair.add(agents.get(j));
                List<LocalDateTime> interval = CommonFunctionHelper.calculateInterval(pair, isToWork);
                if (interval != null) {
                    Long travelTime = CommonFunctionHelper.checkFeasTime(pair);
                    if (travelTime == null) {
                        Collections.reverse(pair);
                        travelTime = CommonFunctionHelper.checkFeasTime(pair);
                    }
                    if (travelTime != null) {
                        shareGraph.addEdge(pair.get(0), pair.get(1));
                    }

                }
            }
        }
        return shareGraph;
    }

    private List<List<Agent>> makePreclusteringRadial(List<Agent> oneTarget) {
        //create method to get slope between agent and target
        Point2D target = CommonFunctionHelper.convertToMercator(
                oneTarget.get(0).getRequest().getDropOffPosition());
        List<List<Agent>> result = new ArrayList<>();
        List<List<Object>> radials = new ArrayList<>();
        for(Agent ag : oneTarget) {
            List<Object> tuple = new ArrayList<>();
            tuple.add(ag);
            tuple.add(CommonFunctionHelper.angleWithVertical(target,
                    CommonFunctionHelper.convertToMercator(ag.getHomePosition())));
            radials.add(tuple);
        }
        radials = radials.stream().sorted(comparingDouble(obj -> (double) obj.get(1))).toList();

        int counter = 0;
        List<Agent> sector = new ArrayList<>();
        for (int i = 0; i < radials.size(); i++) {

            sector.add((Agent) radials.get(i).get(0));
            counter += 1;

            if(counter == GeneralManager.preClusterSize) {
                counter = 0;
                result.add(sector);
                sector = new ArrayList<>();
            }
        }
        if (counter != 0) {
            result.add(sector);
        }
        return result;

    }

    private List<List<List<Agent>>> makePreclusteringbyTime(List<Agent> agenList) {
        List<List<List<Agent>>> result = new ArrayList<>();
        List<DefaultUndirectedGraph<Agent, DefaultEdge>> graphs
                = IntervalGraph.buildIntervalGraph(agenList);
        result.add(IntervalGraph.cliqueCover(graphs.get(0)));
        result.add(IntervalGraph.cliqueCover(graphs.get(1)));

        return result;
    }

/*
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

 */

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
