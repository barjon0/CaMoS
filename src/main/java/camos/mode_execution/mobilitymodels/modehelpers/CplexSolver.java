package camos.mode_execution.mobilitymodels.modehelpers;
import camos.mode_execution.Agent;
import camos.mode_execution.groupings.RouteSet;
import ilog.cplex.*;
import ilog.concert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CplexSolver {

    public static List<RouteSet> solveProblem(List<RouteSet> toWorkRoutes, List<RouteSet> fromWorkRoutes, List<Agent> agents) {
        try (IloCplex cplex = new IloCplex()){
            cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0.001);
            cplex.setParam(IloCplex.Param.MIP.Limits.Nodes, 60000);
            cplex.setParam(IloCplex.Param.MIP.Strategy.File, 2);
            cplex.setParam(IloCplex.Param.TimeLimit, 3600);
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
                List<IloNumVar> varListTo = new ArrayList<>();
                List<IloNumVar> varListToDriver = new ArrayList<>();
                for (int j = 0; j < toWorkRoutes.size(); j++) {
                    RouteSet routeSet = toWorkRoutes.get(j);
                    if (routeSet.getMembers().contains(agent)) {
                        if (toWorkRoutes.get(j).getDriver() == agent) {
                            varListToDriver.add(binVars[j]);
                        }
                        varListTo.add(binVars[j]);
                    }
                }
                List<IloNumVar> varListFrom = new ArrayList<>();
                List<IloNumVar> varListFromDriver = new ArrayList<>();
                for (int j = 0; j < fromWorkRoutes.size(); j++) {
                    if (fromWorkRoutes.get(j).getMembers().contains(agent)) {
                        if (fromWorkRoutes.get(j).getDriver() == agent) {
                            varListFromDriver.add(binVars[j + toWorkRoutes.size()]);
                        }
                        varListFrom.add(binVars[j + toWorkRoutes.size()]);
                    }
                }
                //constraint #agent is picked as driver to work = # agent is picked as driver from work
                List<IloNumVar> varListBothDriver = new ArrayList<>(varListToDriver);
                varListBothDriver.addAll(varListFromDriver);
                IloNumVar[] varArrayBothDriver = varListBothDriver.toArray(new IloNumVar[0]);
                double[] coeffBoth = new double[varArrayBothDriver.length];
                Arrays.fill(coeffBoth, 0, varListToDriver.size(), 1);
                Arrays.fill(coeffBoth, varListToDriver.size(), coeffBoth.length, -1);
                IloLinearNumExpr constraint1 = cplex.linearNumExpr();
                constraint1.addTerms(coeffBoth, varArrayBothDriver);
                constraint1.setConstant(0);
                cplex.addEq(constraint1, 0);

                //constraint each agent picked exactly once to Work
                IloNumVar[] varArrayTo = varListTo.toArray(new IloNumVar[0]);
                double[] coeffTo = new double[varArrayTo.length];
                Arrays.fill(coeffTo, 1);
                IloLinearNumExpr constraint2 = cplex.linearNumExpr();
                constraint2.addTerms(varArrayTo, coeffTo);
                constraint2.setConstant(0);
                cplex.addEq(constraint2, 1);

                //constraint each agent picked exactly once from work
                IloNumVar[] varArrayFrom = varListFrom.toArray(new IloNumVar[0]);
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
            List<RouteSet> result = new ArrayList<>(solutionTo);
            result.addAll(solutionFrom);
            System.out.println("The number of node was: " + cplex.getNnodes());
            System.out.println("CPU time spent: " + cplex.getCplexTime() + " seconds");
            cplex.close();

            return result;

        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
            return null;
        }
    }
}
