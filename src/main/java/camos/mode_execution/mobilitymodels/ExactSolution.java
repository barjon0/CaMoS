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
import camos.mode_execution.mobilitymodels.modehelpers.StartHelpers;
import org.jfree.chart.ChartUtils;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingDouble;
import static java.util.Comparator.comparingInt;

public class ExactSolution extends SDCTSP{

    List<RouteSet> foundRouteSets;
    Set<RouteSet> lookUpTo;
    Set<RouteSet> lookUpFrom;

    AtomicInteger lock;

    public ExactSolution() {
        super();
        lookUpTo = new HashSet<>();
        lookUpFrom = new HashSet<>();
        foundRouteSets = new ArrayList<>();
        lock = new AtomicInteger(0);
    }



    @Override
    public void prepareMode(List<Agent> agents) throws InterruptedException {

        this.agents = agents;
        System.out.println("before enumerating " + (System.nanoTime()/ 1_000_000) + "ms");

        Map<Coordinate, List<Agent>> agentsByTarget = agents.stream()
                .collect(Collectors.groupingBy(a -> a.getRequest().getDropOffPosition()));

        int numberOfCores = Runtime.getRuntime().availableProcessors() - 1;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfCores);
        System.out.println("The system is using " + numberOfCores + " cores.");

        for(List<Agent> oneTarget : agentsByTarget.values()) {
            List<List<Agent>> preClusterAgents = makePreclusteringRadial(oneTarget, 400);
            for (List<Agent> agentCluster : preClusterAgents) {
                System.out.println("starting one Agent Cluster " + (System.nanoTime() / 1_000_000) + "ms");
                System.out.println("Number of agents is: " + agentCluster.size());

                List<Future<List<RouteSet>>> futureListTo = new ArrayList<>();
                List<Future<List<RouteSet>>> futureListBack = new ArrayList<>();

                List<List<Agent>> preClusters = makePreclusteringRadial(agentCluster, GeneralManager.preClusterSize);

                for (List<Agent> preCluster : preClusters) {
                    Callable<List<RouteSet>> listCallable = () -> routeEnumerate(preCluster, Requesttype.DRIVETOUNI);
                    Future<List<RouteSet>> result = executorService.submit(listCallable);
                    futureListTo.add(result);

                    Callable<List<RouteSet>> listCallableBack = () -> routeEnumerate(preCluster, Requesttype.DRIVEHOME);
                    Future<List<RouteSet>> resultBack = executorService.submit(listCallableBack);
                    futureListBack.add(resultBack);
                }
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

                System.out.println("enumarating done for one Agent Cluster " + (System.nanoTime() / 1_000_000) + "ms");

                System.gc();
                Thread.sleep(1000);

                List<RouteSet> sets = CplexSolver.solveProblem(lookUpTo.stream().toList(), lookUpFrom.stream().toList(), agentCluster);
                if (sets != null) {
                    foundRouteSets.addAll(sets);
                    lookUpTo = new HashSet<>();
                    lookUpFrom = new HashSet<>();
                    System.out.println("solved for one agent Cluster");
                } else {
                    throw new IllegalStateException("NO solution found");
                }
            }

        }
        executorService.shutdown();
        System.out.println("preparing done " + (System.nanoTime()/ 1_000_000) + "ms");

    }



/*
    public void prepareMode(List<Agent> agents) throws ExecutionException, InterruptedException {

        this.agents = agents;

        System.out.println("before enumerating " + (System.nanoTime()/ 1_000_000) + "ms");

        Map<Coordinate, List<Agent>> agentsByTarget = agents.stream()
                .collect(Collectors.groupingBy(a -> a.getRequest().getDropOffPosition()));

        int numberOfCores = Runtime.getRuntime().availableProcessors() - 1;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfCores);
        System.out.println("Using " + numberOfCores + " many cores for computation.");

        for(List<Agent> oneTarget : agentsByTarget.values()) {

            Map<Set<Agent>, Boolean> foundSetsTo = new HashMap<>();
            Map<Set<Agent>, Boolean> foundSetsFrom = new HashMap<>();

            System.out.println("starting one Target " + (System.nanoTime() / 1_000_000) + "ms");
            System.out.println(oneTarget.size());

            List<DefaultUndirectedGraph<Agent, DefaultEdge>> timeGraphs = IntervalGraph.buildIntervalGraph(oneTarget);
            List<List<List<Agent>>> allCliques = new ArrayList<>();

            Callable<List<List<Agent>>> toCall = () -> IntervalGraph.getAllMaxCliques(timeGraphs.get(0));
            Callable<List<List<Agent>>> fromCall = () -> IntervalGraph.getAllMaxCliques(timeGraphs.get(1));
            Future<List<List<Agent>>> futureTo = executorService.submit(toCall);
            Future<List<List<Agent>>> futureFrom = executorService.submit(fromCall);
            List<List<Agent>> resultTo = futureTo.get();
            List<List<Agent>> resultFrom = futureFrom.get();

            List<List<Agent>> allNecessaryTo = findNeededBubbles(resultTo, oneTarget);
            List<List<Agent>> allNecessaryFrom = findNeededBubbles(resultFrom, oneTarget);

            allCliques.add(allNecessaryTo.stream().sorted(Comparator.comparingInt(List::size)).toList());
            allCliques.add(allNecessaryFrom.stream().sorted(Comparator.comparingInt(List::size)).toList());

            System.out.println("All time cliques were found, there are " + allCliques.get(0).size() + " to work \nAnd " + allCliques.get(1).size() + " from work.");

            List<Future<List<RouteSet>>> checkListTo = new ArrayList<>();
            List<Future<List<RouteSet>>> checkListFrom = new ArrayList<>();

            for (List<Agent> timeClique : allCliques.get(0)) {
                List<List<Agent>> preClusters = makePreclusteringRadial(timeClique, GeneralManager.preClusterSize);
                for (List<Agent> preCluster : preClusters) {
                    Callable<List<RouteSet>> runn = () -> routeEnumerateTime(preCluster, Requesttype.DRIVETOUNI, foundSetsTo);
                    checkListTo.add(executorService.submit(runn));
                }
            }

            for (List<Agent> timeClique : allCliques.get(1)) {
                List<List<Agent>> preClusters = makePreclusteringRadial(timeClique, GeneralManager.preClusterSize);
                for (List<Agent> preCluster : preClusters) {
                    Callable<List<RouteSet>> runn = () -> routeEnumerateTime(preCluster, Requesttype.DRIVEHOME, foundSetsFrom);
                    checkListFrom.add(executorService.submit(runn));
                }
            }

            for (Future<List<RouteSet>> task : checkListTo) {
                lookUpTo.addAll(task.get());
            }

            for (Future<List<RouteSet>> task : checkListFrom) {
                lookUpFrom.addAll(task.get());
            }

            System.out.println("enumarating done for target " + (System.nanoTime() / 1_000_000) + "ms");

            List<RouteSet> sets = CplexSolver.solveProblem(lookUpTo.stream().toList(), lookUpFrom.stream().toList(), oneTarget);
            if(sets != null) {
                foundRouteSets.addAll(sets);
                lookUpFrom = new HashSet<>();
                lookUpTo = new HashSet<>();
                System.out.println("solved for one target");
            } else {
                throw new IllegalStateException("NO solution found");
            }

        }
        executorService.shutdown();
        System.out.println("preparing done " + (System.nanoTime()/ 1_000_000) + "ms");

    }

 */



    //does greedy Set Cover
    private List<List<Agent>> findNeededBubbles(List<List<Agent>> allBubbles, List<Agent> agentList) {
        List<List<Agent>> result = new ArrayList<>();
        List<List<Set<Agent>>> allList = new ArrayList<>();
        for (List<Agent> bubble : allBubbles) {
            List<Set<Agent>> tuple = new ArrayList<>();
            tuple.add(new HashSet<>(bubble));
            tuple.add(new HashSet<>(bubble));
            allList.add(tuple);
        }
        Set<Agent> allAgents = new HashSet<>(agentList);
        while (!allAgents.isEmpty()) {
            List<List<Set<Agent>>> copyAll = new ArrayList<>();
            List<Set<Agent>> maxTup = allList.stream().max(comparingInt(o -> o.get(1).size())).get();
            Set<Agent> originalClique = maxTup.get(0);
            Set<Agent> remSet = new HashSet<>(maxTup.get(1));
            for (List<Set<Agent>> tuple : allList) {
                tuple.get(1).removeAll(remSet);
                if (!tuple.get(1).isEmpty()) {
                    copyAll.add(tuple);
                }
            }
            allAgents.removeAll(remSet);
            result.add(originalClique.stream().toList());
            allList = copyAll;
        }
        return result;
    }


    private List<RouteSet> routeEnumerateTime(List<Agent> agents, Requesttype isToWork, Map<Set<Agent>, Boolean> foundSetsOriginal) throws InterruptedException {

        //long timeStart = System.currentTimeMillis();
        System.out.println("Thread No. " + Thread.currentThread().getId() + ": Starting to enumerate Cluster of size: " + agents.size());
        List<RouteSet> routeSetList = new ArrayList<>();
        DefaultUndirectedGraph<Agent, DefaultEdge> shareGraph =
                buildShareGraph(agents, isToWork);
        Map<Set<Agent>, Boolean> foundSets;

        while (true) {
            if(lock.get() != 0) {
                Thread.sleep(100);
            } else {
                lock.set(1);
                foundSets = new HashMap<>(foundSetsOriginal);
                lock.set(0);
                break;
            }
        }

        Map<Set<Agent>, Boolean> nextFoundSets = new HashMap<>();
        List<Set<Agent>> finalLastCliques = new ArrayList<>();
        shareGraph.vertexSet().forEach(v -> {
            Set<Agent> members = new HashSet<>();
            members.add(v);
            finalLastCliques.add(members);
            double time = (CommonFunctionHelper
                    .getSimpleBestGraphhopperPath(v.getHomePosition(), v.getRequest().getDropOffPosition())
                    .getTime() / 60000.0);
            if(!foundSets.containsKey(members)) {
                nextFoundSets.put(members, true);
                routeSetList.add(new RouteSet(members.stream().toList(), v, time, isToWork));
            }
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
                            if (!foundSets.containsKey(memberSet)) {
                                boolean foundValidRoute = false;
                                List<Agent> possDrivers = memberSet.stream()
                                        .filter(a -> a.getCar().getSeatCount() > finalK).toList();
                                //build distances graph
                                SimpleWeightedGraph<Coordinate, DefaultWeightedEdge> timeGraph = buildTimesGraph(memberSet.stream().toList());
                                for (Agent driver : possDrivers) {
                                    RouteSet rS = null;
                                    List<Agent> withoutDriver = new ArrayList<>(memberSet);
                                    withoutDriver.remove(driver);
                                    List<List<Agent>> permutations = CommonFunctionHelper.getPermut(withoutDriver);

                                    for (List<Agent> permut : permutations) {
                                        permut.add(0, driver);
                                        //use function that gets graph and Agent-permutation -> checks if possible, returns total time
                                        Double travelTime = checkTime(permut, timeGraph);
                                        if (travelTime != null) {
                                            if (rS == null) {
                                                rS = new RouteSet(permut, driver, travelTime, isToWork);
                                                rS.setTimeInMinutes(travelTime);
                                                foundValidRoute = true;
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
                                if (foundValidRoute) {
                                    nextCliques.add(memberSet);
                                }
                                nextFoundSets.put(memberSet, foundValidRoute);
                            } else {
                                if (foundSets.get(memberSet)) {
                                    nextCliques.add(memberSet);
                                }
                            }
                        }
                    }
                }
            }
            lastCliques = nextCliques;
            k += 1;
            System.out.println("Thread No. " + Thread.currentThread().getId() + ": CliqueSize is " + k + "\nNumber of Cliques: " + lastCliques.size());
        }
        //multithreading update overall Routeset list -> there will be some routesets found simultaneously but filtered out later
        while (true) {
            if (lock.get() != 0) {
                Thread.sleep(100);
            } else {
                lock.set(1);
                foundSetsOriginal.putAll(nextFoundSets);
                lock.set(0);
                break;
            }
        }
        //System.out.println("Done enumerate  of Cluster with size: " + agents.size() + "\n It took " + ((System.nanoTime() - timeStart) / 60000.0));
        return routeSetList;
    }



    private List<RouteSet> routeEnumerate(List<Agent> candidates, Requesttype isToWork) {

        DefaultUndirectedGraph<Agent, DefaultEdge> shareGraphTo =
                buildShareGraph(candidates, isToWork);

        return buildingCliques(shareGraphTo, isToWork);
    }
    private List<RouteSet> buildingCliques(DefaultUndirectedGraph<Agent, DefaultEdge> shareGraph, Requesttype isToWork) {
        List<RouteSet> routeSetList = new ArrayList<>();

        Set<Set<Agent>> finalLastCliques = new HashSet<>();
        shareGraph.vertexSet().forEach(v -> {
            Set<Agent> members = new HashSet<>();
            members.add(v);
            finalLastCliques.add(members);
            double time = (CommonFunctionHelper
                    .getSimpleBestGraphhopperPath(v.getHomePosition(), v.getRequest().getDropOffPosition())
                    .getTime() / 60000.0);
            routeSetList.add(new RouteSet(members.stream().toList(), v, time, isToWork));
        });
        Set<Set<Agent>> lastCliques = finalLastCliques;
        System.out.println("Thread No. " + Thread.currentThread().getId() + ": Starting to enumerate Cluster of size: " + shareGraph.vertexSet().size());
        int k = 1;
        while (!lastCliques.isEmpty()) {
            Set<Set<Agent>> nextCliques = new HashSet<>();
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
                                    //build distances graph
                                    SimpleWeightedGraph<Coordinate, DefaultWeightedEdge> timeGraph = buildTimesGraph(memberSet.stream().toList());
                                    for (Agent driver : possDrivers) {
                                        RouteSet rS = null;

                                        List<Agent> withoutDriver = new ArrayList<>(memberSet);
                                        withoutDriver.remove(driver);
                                        List<List<Agent>> permutations = CommonFunctionHelper.getPermut(withoutDriver);
                                        for (List<Agent> permut : permutations) {
                                            permut.add(0, driver);
                                            Double travelTime = checkTime(permut, timeGraph);
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
                                    if (found) {
                                        nextCliques.add(memberSet);
                                    }
                                }
                        }
                    }
                }
            }
            lastCliques = nextCliques;
            k += 1;
            System.out.println("Thread No. " + Thread.currentThread().getId() + ": CliqueSize is " + k + "\nNumber of Cliques: " + lastCliques.size());
        }
        return routeSetList;
    }
/*
    private List<RouteSet> enumerateSmart(List<Agent> agentList, Requesttype isToWork) {
        List<RouteSet> result = new ArrayList<>();
        List<Set<Agent>> lastCliques = new ArrayList<>();
        System.out.println("Thread No. " + Thread.currentThread().threadId() + ": Starting to enumerate Cluster of size: " + agentList.size());
        for (Agent agent : agentList) {
            Set<Agent> members = new HashSet<>();
            members.add(agent);
            lastCliques.add(members);
            double time = (CommonFunctionHelper
                    .getSimpleBestGraphhopperPath(agent.getHomePosition(), agent.getRequest().getDropOffPosition())
                    .getTime() / 60000.0);
            result.add(new RouteSet(members.stream().toList(), agent, time, isToWork));
        }
        Set<Set<Agent>> nextCliques;
        int k = 1;
        while (!lastCliques.isEmpty()) {
            nextCliques = new HashSet<>();
            for (int i = 0; i < lastCliques.size(); i++) {
                for (int j = i + 1; j < lastCliques.size(); j++) {
                    Set<Agent> members = new HashSet<>(lastCliques.get(i));
                    members.addAll(lastCliques.get(j));
                    int finalK = k;
                    if (members.size() == (k + 1) && !nextCliques.contains(members) && members.stream().anyMatch(agent -> agent.getCar().getSeatCount() > finalK)) {
                        List<Set<Agent>> allSubCliques = findAllSubCliques(members);
                        if(new HashSet<>(lastCliques).containsAll(allSubCliques)) {
                            List<LocalDateTime> interval =
                                    CommonFunctionHelper.calculateInterval(members.stream().toList(), isToWork);
                            if(interval != null) {
                                boolean found = false;
                                List<Agent> possDrivers = members.stream()
                                        .filter(a -> a.getCar().getSeatCount() > finalK).toList();
                                //build distances graph
                                SimpleWeightedGraph<Coordinate, DefaultWeightedEdge> timeGraph = buildTimesGraph(members.stream().toList());

                                for (Agent driver : possDrivers) {
                                    RouteSet rS = null;
                                    List<Agent> withoutDriver = new ArrayList<>(members);
                                    withoutDriver.remove(driver);
                                    List<List<Agent>> permutations = CommonFunctionHelper.getPermut(withoutDriver);

                                    for (List<Agent> permut : permutations) {
                                        permut.add(0, driver);
                                        Double travelTime = checkTime(permut, timeGraph);
                                        if (travelTime != null) {
                                            if (rS == null) {
                                                rS = new RouteSet(permut, driver, travelTime, isToWork);
                                                found = true;
                                                result.add(rS);
                                            } else {
                                                if (rS.getTimeInMinutes() > travelTime) {
                                                    rS.setTimeInMinutes(travelTime);
                                                    rS.setOrder(permut);
                                                }
                                            }
                                        }
                                    }
                                }
                                if (found) {
                                    nextCliques.add(members);
                                }
                            }
                        }

                    }
                }
            }
            k += 1;
            lastCliques = nextCliques.stream().toList();
            System.out.println("Thread No. " + Thread.currentThread().threadId() + ": CliqueSize is " + k + "\nNumber of Cliques: " + lastCliques.size());
        }

        return result;
    }

 */

    private List<Set<Agent>> findAllSubCliques(Set<Agent> memberSet) {
        List<Set<Agent>> result = new ArrayList<>();
        for (Agent agent : memberSet) {
            Set<Agent> aClique = new HashSet<>(memberSet);
            aClique.remove(agent);
            result.add(aClique);
        }
        return result;
    }

    private Double checkTime(List<Agent> permut, SimpleWeightedGraph<Coordinate, DefaultWeightedEdge> timeGraph) {

        double[] totalTraveltime = new double[permut.size()];
        for (int i = 0; i < permut.size() - 1; i++) {
            double timeMin;
            if (permut.get(i).getHomePosition().equalValue(permut.get(i+1).getHomePosition())) {
                timeMin = 0;
            } else {
                DefaultWeightedEdge edge = timeGraph.getEdge(permut.get(i).getHomePosition(), permut.get(i+1).getHomePosition());
                timeMin = timeGraph.getEdgeWeight(edge) + GeneralManager.stopTime;
            }
            for (int j = 0; j < i + 1; j++) {
                totalTraveltime[j] += timeMin;
            }
        }
        for (int i = 0; i < permut.size(); i++) {
            DefaultWeightedEdge edge = timeGraph.getEdge(permut.get(permut.size()-1).getHomePosition(), permut.get(0).getRequest().getDropOffPosition());
            double timeMin = timeGraph.getEdgeWeight(edge);
            totalTraveltime[i] += timeMin;
            if(totalTraveltime[i] > permut.get(i).getMaxTravelTimeInMinutes()) {
                return null;
            }
        }
        return totalTraveltime[0];

    }

    private SimpleWeightedGraph<Coordinate, DefaultWeightedEdge> buildTimesGraph(List<Agent> clique) {
        SimpleWeightedGraph<Coordinate, DefaultWeightedEdge> timeGraph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        List<Coordinate> coordinates = new ArrayList<>(clique.stream().map(Agent::getHomePosition).toList());
        coordinates.add(clique.get(0).getRequest().getDropOffPosition());
        coordinates.forEach(timeGraph::addVertex);
        for (int i = 0; i < coordinates.size(); i++) {
            for (int j = i + 1; j < coordinates.size(); j++) {
                double time = (CommonFunctionHelper.getSimpleBestGraphhopperPath(coordinates.get(i), coordinates.get(j))
                        .getTime() / 60000.0);
                DefaultWeightedEdge edge = timeGraph.addEdge(coordinates.get(i), coordinates.get(j));
                timeGraph.setEdgeWeight(edge, time);
            }
        }
        return timeGraph;
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
                    Double travelTime = CommonFunctionHelper.checkFeasTime(pair);
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

    private List<List<Agent>> makePreclusteringRadial(List<Agent> oneTarget, int clusterSize) {
        //create method to get slope between agent and target
        List<List<Agent>> result = new ArrayList<>();
        Coordinate target = oneTarget.get(0).getRequest().getDropOffPosition();
        List<List<Object>> radials = new ArrayList<>();
        for(Agent ag : oneTarget) {
            List<Object> tuple = new ArrayList<>();
            tuple.add(ag);
            tuple.add(CommonFunctionHelper.calcBearing(ag.getHomePosition(), target));
            radials.add(tuple);
        }
        radials = radials.stream().sorted(comparingDouble(obj -> (double) obj.get(1))).toList();

        int counter = 0;
        List<Agent> sector = new ArrayList<>();
        for (int i = 0; i < radials.size(); i++) {

            sector.add((Agent) radials.get(i).get(0));
            counter += 1;

            if(counter == clusterSize) {
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
/*
    private List<List<List<Agent>>> makePreclusteringbyTime(List<Agent> agenList) {
        List<List<List<Agent>>> result = new ArrayList<>();
        List<DefaultUndirectedGraph<Agent, DefaultEdge>> graphs
                = IntervalGraph.buildIntervalGraph(agenList);
        result.add(IntervalGraph.cliqueCover(graphs.get(0)));
        result.add(IntervalGraph.cliqueCover(graphs.get(1)));

        return result;
    }

 */

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
                startTime = endTime.minusSeconds(Math.round(rs.getTimeInMinutes() * 60));
                vehicle = rs.getDriver().getCar();
                req = rs.getTypeOfGrouping();
                order = rs.getOrder();
            } else {
                startPosition = rs.getDriver().getRequest().getDropOffPosition();
                destination = rs.getDriver().getHomePosition();
                startTime = CommonFunctionHelper
                        .calculateInterval(rs.getAgents(), rs.getTypeOfGrouping()).get(0);
                endTime = startTime.plusSeconds(Math.round(rs.getTimeInMinutes() * 60));
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
    public void writeResultsToFile() throws IOException {
        calculateMetrics();
        List<String> dataSingle = createSingleData();
        List<String> dataAccum = createAccumData();

        String path1 = "output\\accumResultsExact.csv";
        File csvOutputFile = new File(StartHelpers.correctFilename(path1));
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            for(String data : dataAccum){
                pw.println(data);
            }
        }catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
        String path2 = "output\\singleResultsExact.csv";
        File csvOutputFile2 = new File(StartHelpers.correctFilename(path2));
        try (PrintWriter pw = new PrintWriter(csvOutputFile2)) {
            for(String data : dataSingle){
                pw.println(data);
            }
        }catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }

        String path3 = "output\\exactTimeHist.png";
        try {
            File timeHist3 = new File(StartHelpers.correctFilename(path3));
            ChartUtils.saveChartAsPNG(timeHist3, createTimeChart(20), 800, 600);
        }catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }

        String path4 = "output\\exactDistHist.png";
        try {
            File timeHist4 = new File(StartHelpers.correctFilename(path4));
            ChartUtils.saveChartAsPNG(timeHist4, createDistanceChart(20), 800, 600);
        }catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
        String generalStuff = "";
        double totalMinutes = 0;
        double totalKilometers = 0;
        double avgSeatCount = 0;
        int aloneRides = 0;
        for (Ride r : rides) {
            totalMinutes += Duration.between(r.getStartTime(), r.getEndTime()).toMinutes();
            totalKilometers += r.getDistanceCovered();
            avgSeatCount += r.getAgents().size();
            if(r.getAgents().size() == 1) {
                aloneRides += 1;
            }
        }
        generalStuff += "Total Kilometers: " + totalKilometers + "\n";
        generalStuff += "Total Minutes: " + totalMinutes + "\n";
        generalStuff += "The average Seat Count " + (avgSeatCount / rides.size()) + "\n";
        generalStuff += "The number of rides alone: " + aloneRides + "\n";
        double avgTimeTravelledTo = 0;
        for (Agent a : agents) {
            avgTimeTravelledTo += minutesTravelledBoth.get(a).get(0) + minutesTravelledBoth.get(a).get(1);
        }
        generalStuff += "Average Time Travelled: " + (avgTimeTravelledTo / (2L*agents.size()));
        String path5 = "output/generalStuff.txt";
        Files.writeString(Path.of(path5), generalStuff);
    }

}
