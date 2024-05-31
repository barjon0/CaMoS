package camos.mode_execution;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.jsprit.core.problem.Location;
import camos.GeneralManager;
import camos.mobilitydemand.PostcodeManager;
import camos.mode_execution.mobilitymodels.MobilityMode;
import camos.mode_execution.mobilitymodels.modehelpers.StartHelpers;
import org.apache.commons.io.IOUtils;
import org.geotools.referencing.CRS;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;


public class ModeExecutionManager {


    public static Map<String,Object> configValues;

    public static MobilityMode compareMode;

    public static List<Coordinate> uniPositions;

    public static Map<String, Coordinate> postcodeToCoordinate;

    public static Map<List<Location>,Long> timeMap;

    public static Map<List<Location>,Double> distanceMap;

    public static Map<String,JSONObject> modeValues;

    public static List<Agent> agents;

    public static GraphHopper graphHopper;

    public static double percentOfWillingStudents;


    public static void startModes(String configPath) throws Exception {
        distanceMap = new HashMap<>();
        timeMap = new HashMap<>();
        modeValues = new HashMap<>();
        String[] modes;

        graphHopper = new GraphHopper();
        graphHopper.setOSMFile("sources\\merged.osm.pbf"); //TODO
        graphHopper.setGraphHopperLocation("target/routing-graph-cache");
        graphHopper.setProfiles(new Profile("car").setVehicle("car").setTurnCosts(false),new Profile("foot").setVehicle("foot").setTurnCosts(false));
        graphHopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"),new CHProfile("foot"));
        graphHopper.importOrLoad();

        JSONObject config = new JSONObject(IOUtils.toString(new FileInputStream(configPath), "UTF-8"));
        getGlobalConfig(config);

        if(config.keySet().contains("modes") && config.keySet().contains("modejson") && new File(config.getString("modejson")).exists()){
            if(config.get("modes") instanceof JSONArray modesArray){
                modes = new String[modesArray.length()];
                for(int i=0; i<modesArray.length(); i++){
                    if(modesArray.get(i) instanceof String){
                        modes[i] = (String) modesArray.get(i);
                    }else throw new RuntimeException("'modes' parameter has to be a list of Strings.");
                }

                JSONObject modejson = new JSONObject(IOUtils.toString(new FileInputStream(config.getString("modejson")), "UTF-8"));
                StartHelpers.readInConfigForModes(config,modejson,modes);

                PostcodeManager.setCoordinateReferenceSystem(CRS.decode("EPSG:3857"));
                PostcodeManager.makePostcodePolygonMap();
                //TODO sortiere modes nach compare to
                for(String mode : modes){
                    startMode(mode,agents);
                }

            }else throw new RuntimeException("'modes' parameter not a list of Strings.");

        }else throw new RuntimeException("modes json file not found.");
    }


    public static void startMode(String modeName, List<Agent> agents){
        MobilityMode mode = findMode(modeName);
        mode.prepareMode(agents);
        mode.startMode();
        mode.checkIfConstraintsAreBroken(agents);
        if(mode.getName().equals("EverybodyDrives")){ // TODO
            compareMode = mode;
        }
        mode.writeResultsToFile();
    }


    public static MobilityMode findMode(String modeName){
        try {
            Class<?> modeClass = Class.forName("mobilitysimulation.simulation.mobilitymodels."+ ModeExecutionManager.modeValues.get(modeName).getString("class name"));
            Constructor<?> ctor = modeClass.getConstructor();
            return (MobilityMode) ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }



    public static void getGlobalConfig(JSONObject config) throws Exception {
        ModeExecutionManager.setGeneralManagerAttributes(config);

        GeneralManager.lowerRadius = config.getDouble("lower radius");
        double upperRadius = config.getDouble("upper radius");
        GeneralManager.upperRadius = upperRadius;

        String requestFile = config.getString("request file");
        String radiusFileName;
        if(config.keySet().contains("radius file")){
            radiusFileName = config.getString("radius file");
        }else{
            radiusFileName = "sources\\radius" + upperRadius + ".json";
        }

        File radiusFile = new File(radiusFileName);
        if(!radiusFile.exists()){
            String postcodePairFileString = config.getString("postcodePairFile");
            File postcodePairFile = new File(postcodePairFileString);
            if(!postcodePairFile.exists()){
                throw new RuntimeException("The specified postcode pair file does not exist.");
            }
            Map<String,Integer> postcodes = PostcodeManager.getPostcodesWithinDistance(upperRadius,postcodePairFileString);
            PostcodeManager.putOutPostcodeJson(postcodes,"sources\\radius" + upperRadius + ".json");
        }
        agents = AgentManager.readAgentsWithRequestData(requestFile,radiusFileName);
    }


    public static void setGeneralManagerAttributes(org.json.JSONObject json){
        GeneralManager.busSeatCount = json.getInt("bus seat count");
        GeneralManager.busWithDriver = json.getBoolean("busWithDriver");
        GeneralManager.busCount = json.getInt("bus count");
        GeneralManager.busConsumptionPerKm = json.getDouble("busConsumptionPerKm");
        GeneralManager.busCo2EmissionPerLiter = json.getDouble("busCo2EmissionPerLiter");
        GeneralManager.busPricePerLiter = json.getDouble("busPricePerLiter");
        GeneralManager.studentCarSeatCount = json.getInt("student car seat count");
        GeneralManager.studentCarConsumptionPerKm = json.getDouble("studentCarConsumptionPerKm");
        GeneralManager.studentCarCo2EmissionPerLiter = json.getDouble("studentCarCo2EmissionPerLiter");
        GeneralManager.studentCarPricePerLiter = json.getDouble("studentCarPricePerLiter");
        GeneralManager.stopTime = json.getLong("stop time");
        GeneralManager.timeInterval = json.getLong("time interval");
        GeneralManager.acceptedWalkingDistance = json.getLong("accepted walking distance");
        GeneralManager.acceptedDrivingTime = json.getString("accepted ridesharing time");
        GeneralManager.acceptedRidepoolingTime = json.getString("accepted ridepooling time");
        GeneralManager.centralCoordinate = new Coordinate(json.getJSONObject("centralCoordinate").getDouble("longitude"),json.getJSONObject("centralCoordinate").getDouble("latitude"));
        GeneralManager.countOfGroups = json.getInt("countOfGroups");
        GeneralManager.radiusToExclude = json.getDouble("radiusToExclude");
        if(json.keySet().contains("percentOfWillingStudents")){
            GeneralManager.percentOfWillingStudents = json.getDouble("percentOfWillingStudents");
        }

        uniPositions = new ArrayList<>();
        Map<String,Coordinate> postcodeToCoordinate = new HashMap<>();
        org.json.JSONObject postcodeMapping = json.getJSONObject("postcode mapping");
        for(String postcode : postcodeMapping.keySet()){
            org.json.JSONObject postcodeJson = postcodeMapping.getJSONObject(postcode);
            Coordinate uniCoordinate = new Coordinate(postcodeJson.getDouble("longitude"),postcodeJson.getDouble("latitude"));
            postcodeToCoordinate.put(postcode,uniCoordinate);
            uniPositions.add(uniCoordinate);
        }

        for(String postcode : postcodeMapping.keySet()){
            org.json.JSONObject postcodeJson = postcodeMapping.getJSONObject(postcode);
            Coordinate uniCoordinate = new Coordinate(postcodeJson.getDouble("longitude"),postcodeJson.getDouble("latitude"));
            uniPositions.add(uniCoordinate);
        }
        uniPositions.sort(Comparator.comparing(p -> p.getLongitude() + p.getLatitude()));
        ModeExecutionManager.postcodeToCoordinate = postcodeToCoordinate;
    }


    public static Coordinate turnUniPostcodeIntoCoordinate(String postcode){
        return ModeExecutionManager.postcodeToCoordinate.get(postcode);
    }


    public static String turnUniCoordinateIntoPostcode(Coordinate coordinate){
        for(String postcode : ModeExecutionManager.postcodeToCoordinate.keySet()){
            if(ModeExecutionManager.postcodeToCoordinate.get(postcode).equals(coordinate)){
                return postcode;
            }
        }
        return "wrong coordinate";
    }


    public static void testMode(MobilityMode mode) throws Exception {
        distanceMap = new HashMap<>();
        timeMap = new HashMap<>();

        if(GeneralManager.useGraphhopperForTests){
            graphHopper = new GraphHopper();
            graphHopper.setOSMFile("sources\\merged.osm.pbf"); //TODO
            graphHopper.setGraphHopperLocation("target/routing-graph-cache");
            graphHopper.setProfiles(new Profile("car").setVehicle("car").setTurnCosts(false),new Profile("foot").setVehicle("foot").setTurnCosts(false));
            graphHopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"),new CHProfile("foot"));
            graphHopper.importOrLoad();
        }

        JSONObject config = new JSONObject(IOUtils.toString(new FileInputStream("testConfig.json"), "UTF-8"));
        getGlobalConfig(config);
        mode.setGraphHopper(ModeExecutionManager.graphHopper);

        // Hier beginnt der tatsächliche Test
        mode.prepareMode(agents);
        mode.startMode();
        mode.checkIfConstraintsAreBroken(agents);
        mode.writeResultsToFile();
    }

}
