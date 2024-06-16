package camos.mode_execution.mobilitymodels.modehelpers;

import camos.mode_execution.groupings.Match;
import org.jgrapht.Graph;
import org.jgrapht.alg.matching.GreedyMaximumCardinalityMatching;
import org.jgrapht.graph.*;

import java.util.*;

public class MaximumMatching {

    private static Graph<Match, DefaultEdge> makeGraph(List<Match> toWorkClust, List<Match> fromWorkClust) {
        Graph<Match, DefaultEdge> G =
                new DefaultUndirectedGraph<>(DefaultEdge.class);
        toWorkClust.forEach(G::addVertex);
        fromWorkClust.forEach(G::addVertex);
        for (Match match : toWorkClust) {
            for (Match value : fromWorkClust) {
                if (match.getPossDrivers().stream().filter(value.getPossDrivers()::contains).anyMatch(a -> true)) {
                    DefaultEdge edge = new DefaultEdge();
                    G.addEdge(match, value, edge);
                }
            }
        }
        return G;
    }

    // Driver Code
    public static List<List<Match>> getMatching (List<Match> toWorkClust, List<Match> fromWorkClust) {
        List<List<Match>> matchedPairs = new ArrayList<>();
        // make graph
        Graph<Match, DefaultEdge> G = makeGraph(toWorkClust, fromWorkClust);
        GreedyMaximumCardinalityMatching<Match, DefaultEdge> gmc = new GreedyMaximumCardinalityMatching<>(G, false);
        Set<DefaultEdge> edges = gmc.getMatching().getEdges();
        edges.forEach(e -> {
            List<Match> pair = new ArrayList<>();
            Match m1 = G.getEdgeSource(e);
            Match m2 = G.getEdgeTarget(e);
            m1.setPartner(m2);
            m2.setPartner(m1);
            pair.add(m1);
            pair.add(m2);
            matchedPairs.add(pair);
        });
        return matchedPairs;
    }
}
