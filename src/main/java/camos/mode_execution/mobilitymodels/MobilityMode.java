package camos.mode_execution.mobilitymodels;

import com.graphhopper.GraphHopper;
import camos.mode_execution.Agent;
import camos.mode_execution.ModeExecutionManager;
import camos.mode_execution.groupings.Ride;
import java.io.IOException;

import java.util.*;


/**
 * An abstract class for defining mobility modes.
 */
public abstract class MobilityMode {

    GraphHopper graphHopper;

    boolean comparing = false; //TODO

    List<Agent> agents;
    Map<Agent,Double> emissions;
    Map<Agent,Double> costs;
    Map<Agent,Double> kmTravelled;
    Map<Agent,Double> minutesTravelled;
    Map<Set<Object>,Double> oneWayEmissions;
    Map<Set<Object>,Double> oneWayCosts;
    Map<Set<Object>,Double> oneWayKmTravelled;
    Map<Set<Object>,Double> oneWayMinutesTravelled;
    Map<Agent,List<Ride>> agentToRides;
    List<Ride> rides;

    public MobilityMode(){
        this.graphHopper = ModeExecutionManager.graphHopper;
        emissions = new HashMap<>();
        costs = new HashMap<>();
        kmTravelled = new HashMap<>();
        minutesTravelled = new HashMap<>();
        oneWayEmissions = new HashMap<>();
        oneWayCosts = new HashMap<>();
        oneWayKmTravelled = new HashMap<>();
        oneWayMinutesTravelled = new HashMap<>();
        agentToRides = new HashMap<>();
        rides = new ArrayList<>();
    }

    /**
     * Executes necessary steps before the actual mode execution can start.
     *
     * @param agents the agents input into the mode
     */
    public abstract void prepareMode(List<Agent> agents) throws Exception;

    /**
     * Starts the actual execution of the mobility mode.
     */
    public abstract void startMode();

    public abstract String getName();

    public abstract boolean checkIfConstraintsAreBroken(List<Agent> agents);

    public abstract void writeResultsToFile() throws IOException;

    public abstract List<Ride> returnFinishedRides();

    public Map<Agent, Double> getEmissions() {
        return this.emissions;
    }

    public Map<Agent, Double> getCosts() {
        return this.costs;
    }

    public Map<Agent, Double> getKmTravelled() {
        return this.kmTravelled;
    }

    public Map<Agent, Double> getMinutesTravelled() {
        return this.minutesTravelled;
    }

    public Map<Set<Object>, Double> getOneWayEmissions() {
        return this.oneWayEmissions;
    }

    public Map<Set<Object>, Double> getOneWayCosts() {
        return this.oneWayCosts;
    }

    public Map<Set<Object>, Double> getOneWayKmTravelled() {
        return this.oneWayKmTravelled;
    }

    public Map<Set<Object>, Double> getOneWayMinutesTravelled() {
        return this.oneWayMinutesTravelled;
    }

    public Map<Agent, List<Ride>> getAgentToRides() {
        return this.agentToRides;
    }

    public List<Ride> getRides() {
        return rides;
    }

    public boolean isComparing() {
        return this.comparing;
    }

    public void setComparing(boolean comparing) {
        this.comparing = comparing;
    }

    public GraphHopper getGraphHopper() {
        return graphHopper;
    }

    public void setGraphHopper(GraphHopper graphHopper) {
        this.graphHopper = graphHopper;
    }

    public List<Agent> getAgents() {
        return agents;
    }

    public void setAgents(List<Agent> agents) {
        this.agents = agents;
    }
}
