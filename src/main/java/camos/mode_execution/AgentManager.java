package camos.mode_execution;

import camos.GeneralManager;
import camos.mobilitydemand.AgentCollector;
import camos.mode_execution.carmodels.StudentVehicle;
import org.apache.commons.io.IOUtils;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.CoordinateXY;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AgentManager {

    public static List<Agent> readAgentsWithRequestData(String filePathToAllAgents, String filePathToAgentCountInRange) {
        List<Object> maps = AgentCollector.createPostcodeToNeededAgentsMap(filePathToAgentCountInRange);
        Map<String,Integer> postcodesWithRemainingNeededAgents = (Map<String, Integer>) maps.get(0);
        List<Agent> agents = new ArrayList<>();

        File agentsFile = new File(filePathToAllAgents);
        try {
            if (agentsFile.exists()) {
                InputStream is = new FileInputStream(filePathToAllAgents);
                String jsonTxt = IOUtils.toString(is, "UTF-8");
                org.json.JSONObject json = new org.json.JSONObject(jsonTxt);
                org.json.JSONArray array = (org.json.JSONArray) json.get("postcodes");

                for(Object postcodeObj : array){
                    org.json.JSONObject postcodeObject = (org.json.JSONObject) postcodeObj;
                    String postcode = postcodeObject.getString("postcode");
                    if(postcodesWithRemainingNeededAgents.containsKey(postcode)){
                        int numberOfAgentsToRetrieve = postcodesWithRemainingNeededAgents.get(postcode);
                        org.json.JSONArray agentArray = (org.json.JSONArray) postcodeObject.get("agents");
                        for(int i=0; i<numberOfAgentsToRetrieve; i++){
                            if(i==agentArray.length()){
                                break;
                            }
                            org.json.JSONObject agentObject = agentArray.getJSONObject(i);
                            Coordinate homePosition = new Coordinate(agentObject.getDouble("home location longitude"),agentObject.getDouble("home location latitude"));
                            Agent agent;

                            agent = new Agent(agentObject.getLong("agent id"),homePosition, new StudentVehicle());

                            int low = 1;
                            int high = 101;
                            int result = GeneralManager.random.nextInt(high-low) + low;
                            if(result> ModeExecutionManager.percentOfWillingStudents){
                                agent.setWillingToUseAlternatives(false);
                            }

                            // FIXME: changed target to same PLZ
                            Request request = new Request(agent,Requesttype.BOTH,postcode,GeneralManager.uniPLZ ,homePosition, ModeExecutionManager.turnUniPostcodeIntoCoordinate(GeneralManager.uniPLZ));

                            try {
                                String departureTimeString = "02.02.2023 " + agentObject.getString("departure time");
                                LocalDateTime departureTime = LocalDateTime.parse(departureTimeString, GeneralManager.dateTimeFormatter);
                                String arrivalTimeString = "02.02.2023 " + agentObject.getString("arrival time");
                                LocalDateTime arrivalTime = LocalDateTime.parse(arrivalTimeString, GeneralManager.dateTimeFormatter);
                                String requestTimeString = "02.02.2023 " + agentObject.getString("request time");
                                LocalDateTime requestTime = LocalDateTime.parse(requestTimeString, GeneralManager.dateTimeFormatter);

                                if(departureTime.isBefore(arrivalTime)){
                                    departureTime = departureTime.plusDays(1);
                                }
                                if(arrivalTime.isBefore(requestTime)){
                                    requestTime = requestTime.minusDays(1);
                                }

                                request.setFavoredDepartureTime(departureTime);
                                request.setDepartureIntervalStart(departureTime);
                                request.setDepartureIntervalEnd(departureTime.plusMinutes(agent.getTimeIntervalInMinutes()*2));
                                request.setFavoredArrivalTime(arrivalTime);
                                request.setArrivalIntervalStart(arrivalTime.minusMinutes(agent.getTimeIntervalInMinutes()));
                                request.setArrivalIntervalEnd(arrivalTime.plusMinutes(agent.getTimeIntervalInMinutes()));
                                if(!request.getArrivalIntervalEnd().isBefore(request.getDepartureIntervalStart())){
                                    request.setArrivalIntervalEnd(request.getDepartureIntervalStart().minusMinutes(5L));
                                }
                                request.setRequestTime(requestTime);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            if(GeneralManager.plzRadius){
                                agent.setRequest(request);
                                agents.add(agent);
                            }else{
                                org.locationtech.jts.geom.Coordinate homeCoordinate = new CoordinateXY(agent.getHomePosition().getLongitude(),agent.getHomePosition().getLatitude());
                                org.locationtech.jts.geom.Coordinate campusCoordinate = new CoordinateXY(request.getDropOffPosition().getLongitude(),request.getDropOffPosition().getLatitude());

                                double distance = JTS.orthodromicDistance(homeCoordinate,campusCoordinate, DefaultGeographicCRS.WGS84);
                                distance = distance/1000;
                                if(distance <= GeneralManager.upperRadius && distance>= GeneralManager.lowerRadius){
                                    agent.setRequest(request);
                                    agents.add(agent);
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

}
