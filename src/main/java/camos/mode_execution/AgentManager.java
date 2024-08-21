package camos.mode_execution;

import camos.GeneralManager;
import camos.mobilitydemand.AgentCollector;
import camos.mode_execution.carmodels.StandInVehicle;
import camos.mode_execution.carmodels.StudentVehicle;
import camos.mode_execution.carmodels.Vehicle;
import org.apache.commons.io.IOUtils;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.CoordinateXY;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

public class AgentManager {

    public static List<Agent> readAgentsWithRequestData(String filePathToAllAgents, String filePathToAgentCountInRange) {
        Random secRandom = new Random(10203040);
        List<Object> maps = AgentCollector.createPostcodeToNeededAgentsMap(filePathToAgentCountInRange);
        Map<String,Integer> postcodesWithRemainingNeededAgents = (Map<String, Integer>) maps.get(0);
        List<Agent> agents = new ArrayList<>();

        File agentsFile = new File(filePathToAllAgents);
        try {
            if (agentsFile.exists()) {
                InputStream is = new FileInputStream(filePathToAllAgents);
                String jsonTxt = IOUtils.toString(is, "UTF-8");
                org.json.JSONObject json = new org.json.JSONObject(jsonTxt);
                org.json.JSONArray array = json.getJSONArray("postcodes");

                for(Object postcodeObj : array){
                    org.json.JSONObject postcodeObject = (org.json.JSONObject) postcodeObj;
                    String postcode = postcodeObject.getString("postcode");
                    if(postcodesWithRemainingNeededAgents.containsKey(postcode) && (!GeneralManager.disregardLocals || checkIfNotWue(postcode))){
                        int numberOfAgentsToRetrieve = postcodesWithRemainingNeededAgents.get(postcode);
                        org.json.JSONArray agentArray = (org.json.JSONArray) postcodeObject.get("agents");
                        for(int i = 0; i < numberOfAgentsToRetrieve; i++){
                            int low = 1;
                            int high = 101;
                            int result = GeneralManager.random.nextInt(high-low) + low;
                            if(result <= GeneralManager.percentOfWillingStudents) {
                                if (i >= agentArray.length()) {
                                    break;
                                }
                                org.json.JSONObject agentObject = agentArray.getJSONObject(i);
                                Coordinate homePosition = new Coordinate(agentObject.getDouble("home location longitude"), agentObject.getDouble("home location latitude"));
                                String uniPLZ = agentObject.getString("uni location");
                                Agent agent;
                                Vehicle vehicle;
                                if((secRandom.nextInt(high-low) + low) <= GeneralManager.percentPassengers) {
                                    vehicle = new StandInVehicle();
                                } else {
                                    vehicle = new StudentVehicle();
                                }
                                agent = new Agent(agentObject.getLong("agent id"), homePosition, vehicle);
                                Coordinate uniPosition = ModeExecutionManager.turnUniPostcodeIntoCoordinate(uniPLZ);
                                Request request = new Request(agent, Requesttype.BOTH, postcode, uniPLZ, homePosition, uniPosition);

                                try {
                                    String departureTimeString = "02.02.2023 " + agentObject.getString("departure time");
                                    LocalDateTime departureTime = LocalDateTime.parse(departureTimeString, GeneralManager.dateTimeFormatter);
                                    String arrivalTimeString = "02.02.2023 " + agentObject.getString("arrival time");
                                    LocalDateTime arrivalTime = LocalDateTime.parse(arrivalTimeString, GeneralManager.dateTimeFormatter);
                                    String requestTimeString = "02.02.2023 " + agentObject.getString("request time");
                                    LocalDateTime requestTime = LocalDateTime.parse(requestTimeString, GeneralManager.dateTimeFormatter);

                                    if (departureTime.isBefore(arrivalTime)) {
                                        departureTime = departureTime.plusDays(1);
                                    }
                                    if (arrivalTime.isBefore(requestTime)) {
                                        requestTime = requestTime.minusDays(1);
                                    }

                                    request.setFavoredDepartureTime(departureTime);
                                    request.setDepartureIntervalStart(departureTime);
                                    request.setDepartureIntervalEnd(departureTime.plusMinutes(agent.getTimeIntervalInMinutes() * 2));
                                    request.setFavoredArrivalTime(arrivalTime);
                                    request.setArrivalIntervalStart(arrivalTime.minusMinutes(agent.getTimeIntervalInMinutes()));
                                    request.setArrivalIntervalEnd(arrivalTime.plusMinutes(agent.getTimeIntervalInMinutes()));
                                    if (!request.getArrivalIntervalEnd().isBefore(request.getDepartureIntervalStart())) {
                                        request.setArrivalIntervalEnd(request.getDepartureIntervalStart().minusMinutes(5L));
                                    }
                                    request.setRequestTime(requestTime);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                double distance = homePosition.computeDistance(uniPosition);
                                if (GeneralManager.plzRadius) {
                                    agent.setDistanceToTarget(distance);
                                    agent.setRequest(request);
                                    agents.add(agent);
                                } else {
                                    if (distance <= GeneralManager.upperRadius && distance >= GeneralManager.lowerRadius) {
                                        agent.setDistanceToTarget(distance);
                                        agent.setRequest(request);
                                        agents.add(agent);
                                    }
                                }
                            }
                        }

                    }

                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return agents;
    }

    private static boolean checkIfNotWue(String plz) {
        String[] wuePLZ = {"97070", "97072", "97074", "97076", "97078", "97080", "97082", "97084"};
        String normPLZ = plz;
        if (plz.length() > 5) {
            normPLZ = normPLZ.substring(0, 5);
        }
        return !Arrays.asList(wuePLZ).contains(normPLZ);
    }

}
