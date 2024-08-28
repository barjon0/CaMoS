package camos.mode_execution.mobilitymodels;

import camos.GeneralManager;
import camos.mode_execution.Agent;
import camos.mode_execution.Coordinate;
import camos.mode_execution.Requesttype;
import camos.mode_execution.groupings.Ride;
import camos.mode_execution.groupings.Stop;
import camos.mode_execution.groupings.Stopreason;
import camos.mode_execution.mobilitymodels.modehelpers.CommonFunctionHelper;
import com.graphhopper.ResponsePath;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.HistogramDataset;

import java.awt.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public abstract class SDCTSP extends MobilityMode {

    Map<Agent,List<Double>> emissionsBoth;
    Map<Agent,List<Double>> costsBoth;
    Map<Agent,List<Double>> kmTravelledBoth;
    Map<Agent,List<Double>> minutesTravelledBoth;

    Map<Ride,Double> emissionsRide;
    Map<Ride,Double> costsRide;
    Map<Ride,Double> kmTravelledRide;
    Map<Ride,Double> minutesRide;

    public SDCTSP() {
        costsBoth = new HashMap<>();
        emissionsBoth = new HashMap<>();
        kmTravelledBoth = new HashMap<>();
        minutesTravelledBoth = new HashMap<>();
        emissionsRide = new HashMap<>();
        costsRide = new HashMap<>();
        kmTravelledRide = new HashMap<>();
        minutesRide = new HashMap<>();
    }

    @Override
    public void prepareMode(List<Agent> agents) throws Exception {

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
        //checks if everyone has exactly one ride to and from, intervals fit, capacities fit, drivers match, and if max travel time is upheld
        Map<Coordinate, List<Agent>> agentsByTarget = agents.stream()
                .collect(Collectors.groupingBy(a -> a.getRequest().getDropOffPosition()));
        Map<Coordinate, List<Ride>> ridesByTarget = rides.stream().collect(Collectors.groupingBy(r -> {
            if(r.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                return r.getEndPosition();
            } else {
                return r.getStartPosition();
            }
        }));
        for(Coordinate target : agentsByTarget.keySet()) {
            List<Agent> agentList = agentsByTarget.get(target);
            List<Ride> rideList = ridesByTarget.get(target);
            int[] checkingTo = new int[agentList.size()];
            int[] checkingFrom = new int[agentList.size()];
            int[] checkingToDriver = new int[agentList.size()];
            int[] checkingFromDriver = new int[agentList.size()];

            for (Ride ride : rideList) {
                List<LocalDateTime> interval = CommonFunctionHelper.calculateInterval(
                        ride.getAgents(), ride.getTypeOfGrouping());
                if (interval == null) {
			        System.out.println("The time interval is no longer fullfilled");
                    return true;
                }
                if (ride.getDriver().getCar().getSeatCount() < ride.getAgents().size()) {
                    return true;
                }
                if (ride.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                    for (Agent a : ride.getAgents()) {
                        checkingTo[agentList.indexOf(a)] += 1;
                    }
                    Agent driver = ride.getDriver();
                    checkingToDriver[agentList.indexOf(driver)] += 1;
                } else {
                    for (Agent a : ride.getAgents()) {
                        checkingFrom[agentList.indexOf(a)] += 1;
                    }
                    Agent driver = ride.getDriver();
                    checkingFromDriver[agentList.indexOf(driver)] += 1;
                }
            }
            for (int i = 0; i < agentList.size(); i++) {
                if (checkingTo[i] != 1 || checkingFrom[i] != 1 ||
                        (checkingToDriver[i] != checkingFromDriver[i])) {
			        System.out.println("the rides to agents constraint is not fullfilled");
                    return true;
                }
            }
        }

        return false;
    }

    public List<String> createAccumData() {
        List<String> dataLines = new ArrayList<>();
        dataLines.add("Ride Id, TypeOfRide, Zielort, Kilometer, Minuten, CO2, Kosten, Fahrer, Agenten");
        rides = rides.stream().sorted(Comparator.comparing(Ride::getTypeOfGrouping)).toList();
        for(Ride r : rides){
            dataLines.add(r.getId() + "," + r.getTypeOfGrouping().toString() + "," + r.getAgents().get(0).getRequest().getUniPLZ() + "," + String.format(Locale.US,"%.2f", kmTravelledRide.get(r)) + "," + String.format(Locale.US,"%.2f", minutesRide.get(r)) + "," + String.format(Locale.US,"%.2f", emissionsRide.get(r)) + "," + String.format(Locale.US,"%.2f", costsRide.get(r)) + "," + r.getDriver().getId() + "," + r.getAgents().stream().map(Agent::getId).toList());
        }
        return dataLines;
    }

    public List<String> createSingleData() {
        List<String> dataLines2 = new ArrayList<>();
        double avgTimeTravelledTo = 0;
        dataLines2.add("Agent Id, Uni-PLZ, Hin-Kilometer,Hin-Minuten,Hin-CO2,Hin-Kosten,Rück-Kilometer,Rück-Minuten,Rück-CO2,Rück-Kosten");
        for(Agent a : agents) {
            avgTimeTravelledTo += minutesTravelledBoth.get(a).get(0) + minutesTravelledBoth.get(a).get(1);
            dataLines2.add(a.getId() + "," + a.getRequest().getUniPLZ() + "," + String.format(Locale.US, "%.2f", kmTravelledBoth.get(a).get(0)) + "," + String.format(Locale.US, "%.2f", minutesTravelledBoth.get(a).get(0)) + "," + String.format(Locale.US,"%.2f", emissionsBoth.get(a).get(0)) + ","
                    + String.format(Locale.US,"%.2f", costsBoth.get(a).get(0)) + "," + String.format(Locale.US,"%.2f", kmTravelledBoth.get(a).get(1)) + "," + String.format(Locale.US,"%.2f", minutesTravelledBoth.get(a).get(1)) + "," + String.format(Locale.US,"%.2f", emissionsBoth.get(a).get(1)) + ","
                    + String.format(Locale.US,"%.2f", costsBoth.get(a).get(1)));
        }
        System.out.println("Average Time Travelled: " + (avgTimeTravelledTo / (2L*agents.size())));
        return dataLines2;
    }

    @Override
    public List<Ride> returnFinishedRides() {
        return rides;
    }

    public void calculateMetrics() {
        double totalMinutes = 0;
        double totalKilometers = 0;
        double avgSeatCount = 0;
        int aloneRides = 0;
        //also find avg ride time per agent

        for(Ride r : rides) {
            emissionsRide.put(r, r.getDistanceCovered()*r.getDriver().getCar().getConsumptionPerKm()
                    *r.getDriver().getCar().getCo2EmissionPerLiter());
            costsRide.put(r, r.getDistanceCovered()*r.getDriver().getCar().getConsumptionPerKm()
                    *r.getDriver().getCar().getPricePerLiter());
            kmTravelledRide.put(r, r.getDistanceCovered());
            minutesRide.put(r, Duration.between(
                    r.getStartTime(), r.getEndTime()).toSeconds() / 60.0);
            totalMinutes += (Duration.between(r.getStartTime(), r.getEndTime()).toSeconds() / 60.0);
            totalKilometers += r.getDistanceCovered();
            avgSeatCount += r.getAgents().size();
            if(r.getAgents().size() == 1) {
                aloneRides += 1;
            }

            /*
            for(Agent a : r.getAgents()) {
                if(agentToRides.containsKey(a)) {
                    if(r.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                        agentToRides.get(a).add(0, r);
                    } else {
                        agentToRides.get(a).add(1, r);
                    }
                } else {
                    List<Ride> rideList = new ArrayList<>();
                    rideList.add(r);
                    agentToRides.put(a, rideList);
                }

            }
             */
        }
        System.out.println("Total Minutes Travelled: " + totalMinutes + "\n" +
                "Total Kilometers Travelled: " + totalKilometers + "\n" +
                "Average Seat Count: " + (avgSeatCount / rides.size()) + "\n" +
                "Number of Rides alone: " + aloneRides);

    }
    public void computeStops(Ride ride) {
        double[] distPerAgent = new double[ride.getAgents().size()];
        double[] timePerAgent = new double[ride.getAgents().size()];
        List<Stop> stopList = new ArrayList<>();
        LocalDateTime lastStopTime = ride.getStartTime();
        Coordinate lastPosition = ride.getStartPosition();
        Stop lastStop = null;

        if(ride.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
            for (int i = 1; i < ride.getAgents().size(); i++) {
                if (lastStop != null && lastStop.getStopCoordinate().equalValue(ride.getAgents().get(i).getHomePosition())) {
                    lastStop.getPersonsInQuestion().add(ride.getAgents().get(i));
                } else {
                    ResponsePath pw = CommonFunctionHelper
                            .getSimpleBestGraphhopperPath(lastPosition, ride.getAgents().get(i).getHomePosition());
                    for (int j = 0; j < i; j++) {
                        distPerAgent[j] += (pw.getDistance() / 1000.0);
                        timePerAgent[j] += (pw.getTime() / 60000.0) + GeneralManager.stopTime;
                    }
                    LocalDateTime arrivTime = lastStopTime.plusSeconds(Math.round(pw.getTime() / 1000.0));
                    lastStopTime = arrivTime.plusMinutes(GeneralManager.stopTime);
                    lastPosition = ride.getAgents().get(i).getHomePosition();
                    List<Agent> agentList = new ArrayList<>();
                    agentList.add(ride.getAgents().get(i));
                    lastStop = new Stop(arrivTime, lastStopTime, lastPosition, Stopreason.getReason(ride.getTypeOfGrouping()), agentList);
                    stopList.add(lastStop);
                }
            }
            ResponsePath lastPart = CommonFunctionHelper
                    .getSimpleBestGraphhopperPath(ride.getAgents()
                            .get(ride.getAgents().size() - 1).getHomePosition(), ride.getEndPosition());

            for (int j = 0; j < distPerAgent.length; j++) {
                distPerAgent[j] += (lastPart.getDistance() / 1000.0);
                timePerAgent[j] += (lastPart.getTime() / 60000.0);
            }
            ride.setDistanceCovered(distPerAgent[0]);
        } else {
            for (int i = 0; i < ride.getAgents().size() - 1; i++) {
                if (lastStop != null && lastStop.getStopCoordinate().equalValue(ride.getAgents().get(i).getHomePosition())) {
                    lastStop.getPersonsInQuestion().add(ride.getAgents().get(i));
                } else {
                    ResponsePath pw = CommonFunctionHelper
                            .getSimpleBestGraphhopperPath(lastPosition, ride.getAgents().get(i).getHomePosition());
                    for (int j = distPerAgent.length - 1; j > i - 1; j--) {
                        distPerAgent[j] += (pw.getDistance() / 1000.0);
                        timePerAgent[j] += (pw.getTime() / 60000.0) + GeneralManager.stopTime;
                    }
                    timePerAgent[i] -= GeneralManager.stopTime;
                    LocalDateTime arrivTime = lastStopTime.plusSeconds(Math.round(pw.getTime() / 1000.0));
                    lastStopTime = arrivTime.plusMinutes(GeneralManager.stopTime);
                    lastPosition = ride.getAgents().get(i).getHomePosition();
                    List<Agent> agentList = new ArrayList<>();
                    agentList.add(ride.getAgents().get(i));
                    lastStop = new Stop(arrivTime, lastStopTime, lastPosition, Stopreason.getReason(ride.getTypeOfGrouping()), agentList);
                    stopList.add(lastStop);
                }
            }

            ResponsePath lastPart = CommonFunctionHelper
                    .getSimpleBestGraphhopperPath(lastPosition, ride.getEndPosition());

            distPerAgent[distPerAgent.length - 1] += (lastPart.getDistance() / 1000.0);
            timePerAgent[timePerAgent.length - 1] += (lastPart.getTime() / 60000.0);

            ride.setDistanceCovered(distPerAgent[distPerAgent.length - 1]);
        }
        ride.setStops(stopList);

        for (int i = 0; i < ride.getAgents().size(); i++) {
            Agent agent = ride.getAgents().get(i);
            double emmison = distPerAgent[i]*agent.getCar().getConsumptionPerKm()*agent.getCar().getCo2EmissionPerLiter();
            double kmTravelled = distPerAgent[i];
            double minutesTravelled = timePerAgent[i];
            double cost = distPerAgent[i]*agent.getCar().getConsumptionPerKm()*agent.getCar().getPricePerLiter();
            if(agentToRides.containsKey(agent)) {
                int pos = 1;
                if(ride.getTypeOfGrouping() == Requesttype.DRIVETOUNI) {
                    pos = 0;
                }
                emissionsBoth.get(agent).add(pos, emmison);
                kmTravelledBoth.get(agent).add(pos, kmTravelled);
                minutesTravelledBoth.get(agent).add(pos, minutesTravelled);
                costsBoth.get(agent).add(pos, cost);
                agentToRides.get(agent).add(pos, ride);
            } else {
                List<Ride> rideList = new ArrayList<>();
                rideList.add(ride);
                List<Double> emList = new ArrayList<>();
                emList.add(emmison);
                List<Double> kmList = new ArrayList<>();
                kmList.add(kmTravelled);
                List<Double> mintravList = new ArrayList<>();
                mintravList.add(minutesTravelled);
                List<Double> cosList = new ArrayList<>();
                cosList.add(cost);
                emissionsBoth.put(agent, emList);
                kmTravelledBoth.put(agent, kmList);
                minutesTravelledBoth.put(agent, mintravList);
                costsBoth.put(agent, cosList);
                agentToRides.put(agent, rideList);
            }
        }
    }

    //calculate normalized time per shared agent (- x  /log(x)) (do this dependant on function) -> histogram by establishing some bins
    public JFreeChart createTimeChart(int bins) {
        System.out.println("Number of rides total: " + rides.size());
        List<Double> normTimes = new ArrayList<>();
        int zeroCounter = 0;
        for(Agent a: agents) {
            List<Double> times = minutesTravelledBoth.get(a);
            for (int i : new int[]{0, 1}) {
                double firstTime = times.get(i);
                double normTime = (firstTime - a.getMinTravelTime())
                        / (a.getMaxTravelTimeInMinutes() - a.getMinTravelTime());
                if(normTime > 1) {
                    //throw new IllegalStateException("Time is longer than max travel time by: " + (firstTime - a.getMaxTravelTimeInMinutes()));
                    double diff = firstTime - a.getMaxTravelTimeInMinutes();
                    if(diff > 1) {
                        System.out.println("Time is longer than max travel time by: " + diff);
                    }
                    normTime = 1;
                } else if (normTime < 0) {
                    //throw new IllegalStateException("Time is shorter then shortest travel time by " + (a.getMinTravelTime() - firstTime));
                    double diff = a.getMinTravelTime() - firstTime;
                    if(diff > 1) {
                        System.out.println("Time is shorter then shortest travel time by" + diff);
                    }
                    normTime = 0;
                    zeroCounter++;
                } else if (normTime == 0.0) {
                    zeroCounter++;
                }
                normTimes.add(normTime);
            }
        }
        double[] dArray = new double[normTimes.size()];
        for (int i = 0; i < dArray.length; i++) {
            dArray[i] = normTimes.get(i);
        }
        HistogramDataset dataset = new HistogramDataset();
        dataset.addSeries("Frequency", dArray, bins, 0.0, 1.0);
        JFreeChart chart = ChartFactory.createHistogram(
                "Normalized transport times with respect to max travel time",
                "Normalized times",
                "Frequency",
                dataset
        );
        chart.removeLegend();
        XYPlot plot = chart.getXYPlot();
        XYItemRenderer renderer = plot.getRenderer();
        plot.setShadowGenerator(null);
        NumberAxis domainAxis = (NumberAxis) plot.getDomainAxis();
        domainAxis.setRange(0.0, 1.0);

        renderer.setSeriesPaint(0, Color.RED);
        System.out.println("Number of users without any deviation: " + zeroCounter);
        return chart;

    }

    //check straight-line distance to targets, mean number of passengers per 5km disks
    //bar chart
    public JFreeChart createDistanceChart(int bins) {
        List<List<Double>> values = new ArrayList<>();
        double maxDistance = 0;
        double minDistance = 999999999;
        for(Agent a : agents) {
            List<Ride> rideList = agentToRides.get(a);
            if(rideList.get(0).getDriver() == a) {
                List<Double> tuple = new ArrayList<>();
                double distance = a.getDistanceToTarget();
                minDistance = Math.min(distance, minDistance);
                maxDistance = Math.max(distance, maxDistance);
                tuple.add(distance);
                tuple.add((rideList.get(0).getAgents().size() + rideList.get(1).getAgents().size()) / 2.0);
                values.add(tuple);
            }
        }
        //sort into bins
        double[] avgPerBin = new double[bins];

        double binLength = (maxDistance - minDistance) / bins;
        values = values.stream().sorted(Comparator.comparingDouble(obj -> obj.get(0))).collect(Collectors.toList());
        int counter = 0;
        int numbOfVals = 0;
        int currBin = 1;
        while (counter < values.size()) {
            if(values.get(counter).get(0) <= ((currBin * binLength) + minDistance)
                    || (currBin == bins && values.get(counter).get(0) <= maxDistance)) {
                avgPerBin[currBin - 1] += values.get(counter).get(1);
                numbOfVals++;
                counter++;
            } else {
                avgPerBin[currBin - 1] = avgPerBin[currBin - 1] / numbOfVals;
                numbOfVals = 0;
                currBin++;
            }
        }
        avgPerBin[avgPerBin.length - 1] = avgPerBin[avgPerBin.length - 1] / numbOfVals;

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (int i = 0; i < avgPerBin.length; i++) {
            dataset.addValue(avgPerBin[i], "Series1",
                    (Math.round(minDistance + (binLength*i)) + "-" + (Math.round(minDistance + (binLength*(i+1))))));
        }
        JFreeChart chart = ChartFactory.createBarChart(
                "Distances to avg Seat Occupancy",  // Chart title
                "Distance in km",                    // X-Axis label
                "Average Seats used",                    // Y-Axis label
                dataset,                    // Dataset
                org.jfree.chart.plot.PlotOrientation.VERTICAL,
                false,                       // Show legend
                true,                       // Use tooltips
                false                       // Configure chart to generate URLs
        );

        CategoryPlot plot = chart.getCategoryPlot();
        //plot.setInsets(new RectangleInsets(10,10,10,50));
        CategoryItemRenderer renderer = plot.getRenderer();
        renderer.setSeriesPaint(0, Color.BLUE);

        return chart;
    }
}
