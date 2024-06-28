package camos.mode_execution.mobilitymodels.modehelpers;

import camos.mode_execution.Agent;
import org.jgrapht.Graph;
import org.jgrapht.alg.clique.ChordalGraphMaxCliqueFinder;
import org.jgrapht.alg.interfaces.CliqueAlgorithm;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class IntervalGraph {

    public static List<DefaultUndirectedGraph<Agent, DefaultEdge>> buildIntervalGraph(List<Agent> agents) {
        DefaultUndirectedGraph<Agent, DefaultEdge> graphTo = new DefaultUndirectedGraph<>(DefaultEdge.class);
        DefaultUndirectedGraph<Agent, DefaultEdge> graphFrom = new DefaultUndirectedGraph<>(DefaultEdge.class);
        List<DefaultUndirectedGraph<Agent, DefaultEdge>> result = new ArrayList<>();
        result.add(graphTo);
        result.add(graphFrom);
        agents.forEach(a ->  {
            graphTo.addVertex(a);
            graphFrom.addVertex(a);
        });

        for (int i = 0; i < agents.size(); i++) {
            for (int j = i + 1; j < agents.size(); j++) {
                Agent firstOne = agents.get(i);
                Agent secondOne = agents.get(j);
                if (checkIntersect(firstOne, secondOne, true)) {
                    DefaultEdge edge = new DefaultEdge();
                    graphTo.addEdge(firstOne, secondOne, edge);
                }
                if (checkIntersect(firstOne, secondOne, false)) {
                    DefaultEdge edge = new DefaultEdge();
                    graphFrom.addEdge(firstOne, secondOne, edge);
                }
            }
        }
        return result;
    }

    private static boolean checkIntersect(Agent firstOne, Agent secondOne, boolean isToWork) {
        LocalDateTime firstTimeStart;
        LocalDateTime secondTimeStart;
        LocalDateTime firstTimeEnd;
        LocalDateTime secondTimeEnd;

        if (isToWork) {
            firstTimeStart = firstOne.getRequest().getArrivalIntervalStart();
            secondTimeStart = secondOne.getRequest().getArrivalIntervalStart();
            firstTimeEnd = firstOne.getRequest().getArrivalIntervalEnd();
            secondTimeEnd = secondOne.getRequest().getArrivalIntervalEnd();
        } else {
            firstTimeStart = firstOne.getRequest().getDepartureIntervalStart();
            secondTimeStart = secondOne.getRequest().getDepartureIntervalStart();
            firstTimeEnd = firstOne.getRequest().getDepartureIntervalEnd();
            secondTimeEnd = secondOne.getRequest().getDepartureIntervalEnd();
        }
        if (firstTimeStart.isBefore(secondTimeStart) || firstTimeStart.isEqual(secondTimeStart)) {
            if(firstTimeEnd.isAfter(secondTimeStart) || firstTimeEnd.isEqual(secondTimeStart)) {
                return true;
            } else {
                return false;
            }
        } else {
            if(firstTimeStart.isBefore(secondTimeEnd) || firstTimeStart.isEqual(secondTimeEnd)) {
                return true;
            } else {
                return false;
            }
        }
    }

    public static List<List<Agent>> cliqueCover(DefaultUndirectedGraph<Agent, DefaultEdge> graph) {
        List<List<Agent>> result = new ArrayList<>();
        AsSubgraph<Agent, DefaultEdge> Gi = new AsSubgraph<>(graph);
        while(!Gi.vertexSet().isEmpty()) {
            ChordalGraphMaxCliqueFinder<Agent,DefaultEdge> finder = new ChordalGraphMaxCliqueFinder<>(Gi);
            CliqueAlgorithm.CliqueImpl<Agent> maxClique = (CliqueAlgorithm.CliqueImpl<Agent>) finder.getClique();
            List<Agent> cliqueList = maxClique.stream().toList();
            cliqueList.forEach(Gi::removeVertex);
            result.add(cliqueList);
        }
        return result;
    }
}
