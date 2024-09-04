package camos.mode_execution.mobilitymodels.modehelpers;
import camos.mode_execution.Agent;
import camos.mode_execution.Requesttype;
import camos.mode_execution.groupings.RouteSet;
import ilog.cplex.*;
import ilog.concert.*;

import java.time.LocalDateTime;
import java.util.*;

public class CplexSolver {

    public static List<RouteSet> solveProblem(List<RouteSet> toWorkRoutes, List<RouteSet> fromWorkRoutes, List<Agent> agents) {
/*
        for (Agent agent : agents) {
            boolean foundTo = false;
            for (RouteSet routeTo : toWorkRoutes) {
                if(routeTo.getAgents().contains(agent) && routeTo.getAgents().size() == 1) {
                    foundTo = true;
                    break;
                }
            }
            boolean foundFrom = false;
            for (RouteSet routeFrom : fromWorkRoutes) {
                if(routeFrom.getAgents().contains(agent) && routeFrom.getAgents().size() == 1) {
                    foundFrom = true;
                    break;
                }
            }
            if(!foundTo) {
                System.out.println("There is no toWorkRoute for Agent " + agent.getId());
            }
            if(!foundFrom) {
                System.out.println("There is no fromWorkRoute for Agent " + agent.getId());
            }
        }

 */

        List<RouteSet> result = new ArrayList<>();
        try (IloCplex cplex = new IloCplex()) {
            long usedMem = Runtime.getRuntime().totalMemory() / 1048576;
            System.out.println("At the moment there is " + usedMem + " MB used by jvm");

            cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0.01);
            cplex.setParam(IloCplex.Param.WorkMem, 8000 - usedMem);
            cplex.setParam(IloCplex.Param.MIP.Strategy.File, 1);
            cplex.setParam(IloCplex.Param.Emphasis.Memory, true);
            cplex.setParam(IloCplex.Param.Preprocessing.Presolve, true);
            cplex.setParam(IloCplex.Param.Threads, 4);
            cplex.setParam(IloCplex.Param.MIP.Limits.TreeMemory, 3000);
            cplex.setParam(IloCplex.Param.TimeLimit, 3600);
            cplex.setParam(IloCplex.Param.MIP.Display, 4);
            //array of variables one for each route
            //min number of routes
            //for each driver: number of in and out routes equal
            //for each driver: number of routes at most 1
            //for each driver: sum of all routes (that contain him) = 1 for toWork and fromWork
            int numBinVars = toWorkRoutes.size() + fromWorkRoutes.size();
            IloNumVar[] binVars = cplex.boolVarArray(numBinVars);
            IloLinearNumExpr objective = cplex.linearNumExpr();
            for (int i = 0; i < toWorkRoutes.size(); i++) {
                objective.addTerm(toWorkRoutes.get(i).getTimeInMinutes(), binVars[i]);
            }
            for (int i = 0; i < fromWorkRoutes.size(); i++) {
                objective.addTerm(fromWorkRoutes.get(i).getTimeInMinutes(), binVars[i + toWorkRoutes.size()]);
            }
            cplex.addMinimize(objective);

            //go through each agent, check in which routes he is and where he drives, then create constraints
            for (Agent agent : agents) {
                //if(agent.getId() == 7416) {
                 //   System.out.println("stop here");
                //}
                List<IloNumVar> varListTo = new ArrayList<>();
                List<IloNumVar> varListToDriver = new ArrayList<>();
                for (int j = 0; j < toWorkRoutes.size(); j++) {
                    RouteSet routeSet = toWorkRoutes.get(j);
                    if (routeSet.getAgents().contains(agent)) {
                        if (toWorkRoutes.get(j).getDriver() == agent) {
                            varListToDriver.add(binVars[j]);
                        }
                        varListTo.add(binVars[j]);
                    }
                }
                List<IloNumVar> varListFrom = new ArrayList<>();
                List<IloNumVar> varListFromDriver = new ArrayList<>();
                for (int j = 0; j < fromWorkRoutes.size(); j++) {
                    if (fromWorkRoutes.get(j).getAgents().contains(agent)) {
                        if (fromWorkRoutes.get(j).getDriver() == agent) {
                            varListFromDriver.add(binVars[j + toWorkRoutes.size()]);
                        }
                        varListFrom.add(binVars[j + toWorkRoutes.size()]);
                    }
                }
                //constraint #agent is picked as driver to work = # agent is picked as driver from work
                List<IloNumVar> varListBothDriver = new ArrayList<>(varListToDriver);
                varListBothDriver.addAll(varListFromDriver);
                IloNumVar[] varArrayBothDriver = convert2Array(varListBothDriver);
                double[] coeffBoth = new double[varArrayBothDriver.length];
                Arrays.fill(coeffBoth, 0, varListToDriver.size(), 1);
                Arrays.fill(coeffBoth, varListToDriver.size(), coeffBoth.length, -1);
                IloLinearNumExpr constraint1 = cplex.linearNumExpr();
                constraint1.addTerms(coeffBoth, varArrayBothDriver);
                constraint1.setConstant(0);
                cplex.addEq(constraint1, 0);

                //constraint each agent picked exactly once to Work
                IloNumVar[] varArrayTo = convert2Array(varListTo);
                double[] coeffTo = new double[varArrayTo.length];
                Arrays.fill(coeffTo, 1);
                IloLinearNumExpr constraint2 = cplex.linearNumExpr();
                constraint2.addTerms(varArrayTo, coeffTo);
                constraint2.setConstant(0);
                cplex.addEq(constraint2, 1);

                //constraint each agent picked exactly once from work
                IloNumVar[] varArrayFrom = convert2Array(varListFrom);
                double[] coeffFrom = new double[varArrayFrom.length];
                Arrays.fill(coeffFrom, 1);
                IloLinearNumExpr constraint3 = cplex.linearNumExpr();
                constraint3.addTerms(varArrayFrom, coeffFrom);
                constraint3.setConstant(0);
                cplex.addEq(constraint3, 1);
            }

            if (!cplex.solve()) {
                System.out.println("No solution found");
                return null;
            }
            double[] solution = cplex.getValues(binVars);
            List<RouteSet> solutionTo = new ArrayList<>();
            for (int i = 0; i < toWorkRoutes.size(); i++) {
                if (solution[i] == 1) {
                    solutionTo.add(toWorkRoutes.get(i));
                }
            }
            List<RouteSet> solutionFrom = new ArrayList<>();
            for (int i = toWorkRoutes.size(); i < solution.length; i++) {
                if (solution[i] == 1) {
                    solutionFrom.add(fromWorkRoutes.get(i - toWorkRoutes.size()));
                }
            }
            result.addAll(solutionTo);
            result.addAll(solutionFrom);
            repairSolution(result, agents, toWorkRoutes, fromWorkRoutes);
            if(!checkIfValid(result, agents)) {
                throw new IllegalStateException("There is a missmatch");
            }
            System.out.println("The number of node was: " + cplex.getNnodes());
            System.out.println("CPU time spent: " + cplex.getCplexTime() + " seconds");

            cplex.end();

        } catch(IloException e) {
            System.err.println("Concert exception caught: " + e);
            return null;
        }

        return result;
    }

    private static boolean checkIfValid(List<RouteSet> routeSetList, List<Agent> agents) {
        int[] checkingTo = new int[agents.size()];
        int[] checkingFrom = new int[agents.size()];
        int[] checkingToDriver = new int[agents.size()];
        int[] checkingFromDriver = new int[agents.size()];

        for (RouteSet route : routeSetList) {
            if (route.getTypeOfGrouping().equals(Requesttype.DRIVETOUNI)) {
                for (Agent a : route.getAgents()) {
                    checkingTo[agents.indexOf(a)] += 1;
                }
                Agent driver = route.getDriver();
                checkingToDriver[agents.indexOf(driver)] += 1;
            } else {
                for (Agent a : route.getAgents()) {
                    checkingFrom[agents.indexOf(a)] += 1;
                }
                Agent driver = route.getDriver();
                checkingFromDriver[agents.indexOf(driver)] += 1;
            }
        }
	boolean result = true;
        for (int i = 0; i < agents.size(); i++) {
            if(checkingTo[i] != 1) {
                System.out.println("Agent " + agents.get(i).getId() + " to work missmatch: " + checkingTo[i]);
                result = false;
            }
            if(checkingFrom[i] != 1) {
                System.out.println("Agent " + agents.get(i).getId() + " from work missmatch: " + checkingFrom[i]);
                result = false;
	        }
            if(checkingFromDriver[i] != checkingToDriver[i]) {
                System.out.println("Driver " + agents.get(i).getId() + " missmatch: " + checkingToDriver[i] + " and " + checkingFromDriver[i]);
                result = false;
            }
        }
        return result;
    }

    private static void repairSolution(List<RouteSet> solution, List<Agent> agents, List<RouteSet> toWorkRoutes, List<RouteSet> fromWorkRoutes) {
        int[] checkingTo = new int[agents.size()];
        int[] checkingFrom = new int[agents.size()];
        int[] checkingToDriver = new int[agents.size()];
        int[] checkingFromDriver = new int[agents.size()];

        for (RouteSet route : solution) {
            if (route.getTypeOfGrouping().equals(Requesttype.DRIVETOUNI)) {
                for (Agent a : route.getAgents()) {
                    checkingTo[agents.indexOf(a)] += 1;
                }
                Agent driver = route.getDriver();
                checkingToDriver[agents.indexOf(driver)] += 1;
            } else {
                for (Agent a : route.getAgents()) {
                    checkingFrom[agents.indexOf(a)] += 1;
                }
                Agent driver = route.getDriver();
                checkingFromDriver[agents.indexOf(driver)] += 1;
            }
        }

        for (int i = 0; i < agents.size(); i++) {
            if(checkingTo[i] != 1 || checkingFrom[i] != 1) {
                System.out.println("Repairing: for Agent " + agents.get(i).getId() + " missmatch: " + checkingTo[i] + " " + checkingFrom[i]);
                if(checkingTo[i] < 2 && checkingFrom[i] < 2) {
                    List<Agent> agentList = new ArrayList<>();
                    agentList.add(agents.get(i));
                    RouteSet rSTo = findRoute(agentList, toWorkRoutes);
                    RouteSet rSFrom = findRoute(agentList, fromWorkRoutes);
                    if(checkingTo[i] == 0 && checkingFrom[i] == 0) {
                        solution.add(rSTo);
                        solution.add(rSFrom);
                    } else if(checkingTo[i] == 0) {
                        solution.add(rSTo);
                        if(checkingFromDriver[i] == 0) {
                            int finalI = i;
                            RouteSet oldSet = solution.stream()
                                    .filter(rout -> rout.getTypeOfGrouping() == Requesttype.DRIVEHOME && rout.getAgents().contains(agents.get(finalI)))
                                    .findAny().get();
                            List<Agent> remainder = new ArrayList<>(oldSet.getOrder());
                            remainder.remove(agents.get(i));
                            RouteSet rSspecial = findRoute(remainder, fromWorkRoutes);
                            solution.add(rSspecial);
                            solution.remove(oldSet);
                            solution.add(rSFrom);
                        }
                    } else {
                        solution.add(rSFrom);
                        if(checkingToDriver[i] == 0) {
                            int finalI = i;
                            RouteSet oldSet = solution.stream()
                                    .filter(rout -> rout.getTypeOfGrouping() == Requesttype.DRIVETOUNI && rout.getAgents().contains(agents.get(finalI)))
                                    .findAny().get();
                            List<Agent> remainder = new ArrayList<>(oldSet.getOrder());
                            remainder.remove(agents.get(i));
                            RouteSet rSspecial = findRoute(remainder, toWorkRoutes);
                            solution.add(rSspecial);
                            solution.remove(oldSet);
                            solution.add(rSTo);
                        }
                    }
                } else {
                    throw new IllegalStateException("cplex result complete BS");
                }
            }
        }
    }

    private static RouteSet findRoute(List<Agent> agentList, List<RouteSet> routeSetList) {
        Optional<RouteSet> rS = routeSetList.stream().filter(route ->
            route.getDriver() == agentList.get(0) && new HashSet<>(route.getAgents()).equals(new HashSet<>(agentList))
        ).findAny();
        if(rS.isPresent()) {
            return rS.get();
        } else {
            throw new IllegalStateException("The searched Routeset was not available");
        }
    }

    private static IloNumVar[] convert2Array(List<IloNumVar> varListBothDriver) {
        IloNumVar[] result = new IloNumVar[varListBothDriver.size()];
        for (int i = 0; i < varListBothDriver.size(); i++) {
            result[i] = varListBothDriver.get(i);
        }
        return result;
    }

}
