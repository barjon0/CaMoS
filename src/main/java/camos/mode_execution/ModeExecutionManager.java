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
                System.out.println("Postcode stuff done");
                //TODO sortiere modes nach compare to
                for(String mode : modes){
                    System.out.println("\nTrying " + mode);
                    startMode(mode,agents);
                }

            }else throw new RuntimeException("'modes' parameter not a list of Strings.");

        }else throw new RuntimeException("modes json file not found.");
    }


    public static void startMode(String modeName, List<Agent> agents) throws Exception {
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
            Class<?> modeClass = Class.forName("camos.mode_execution.mobilitymodels."+ ModeExecutionManager.modeValues.get(modeName).getString("class name"));
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
        GeneralManager.busSeatCount = json.optInt("bus seat count");
        GeneralManager.preClusterSize = json.optInt("ClusterSize");
        GeneralManager.busWithDriver = json.optBoolean("busWithDriver");
        GeneralManager.busCount = json.optInt("bus count");
        GeneralManager.busConsumptionPerKm = json.optDouble("busConsumptionPerKm");
        GeneralManager.busCo2EmissionPerLiter = json.optDouble("busCo2EmissionPerLiter");
        GeneralManager.busPricePerLiter = json.optDouble("busPricePerLiter");

        GeneralManager.acceptedWalkingDistance = json.optLong("accepted walking distance");

        GeneralManager.studentCarSeatCount = json.optInt("student car seat count");
        GeneralManager.studentCarConsumptionPerKm = json.optDouble("studentCarConsumptionPerKm");
        GeneralManager.studentCarCo2EmissionPerLiter = json.optDouble("studentCarCo2EmissionPerLiter");
        GeneralManager.studentCarPricePerLiter = json.optDouble("studentCarPricePerLiter");
        GeneralManager.stopTime = json.optLong("stop time");
        GeneralManager.timeInterval = json.optLong("time interval");

        GeneralManager.acceptedDrivingTime = json.optString("accepted ridesharing time");
        GeneralManager.acceptedRidepoolingTime = json.optString("accepted ridepooling time");
        GeneralManager.centralCoordinate = new Coordinate(json.optJSONObject("centralCoordinate").getDouble("longitude"),json.optJSONObject("centralCoordinate").getDouble("latitude"));
        GeneralManager.countOfGroups = json.optInt("countOfGroups");
        GeneralManager.radiusToExclude = json.optDouble("radiusToExclude");
        GeneralManager.disregardLocals = json.optBoolean("disregardLocals");
        GeneralManager.percentPassengers = json.optInt("percentPassengers");


        GeneralManager.percentOfWillingStudents = json.optInt("percentWilling");


        uniPositions = new ArrayList<>();
        Map<String,Coordinate> postcodeToCoordinate = new HashMap<>();
        org.json.JSONObject postcodeMapping = json.optJSONObject("postcode mapping");
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
            System.out.println("reading in osm file");
            graphHopper.setOSMFile("sources\\merged.osm.pbf"); //TODO
            System.out.println("done reading");
            graphHopper.setGraphHopperLocation("target/routing-graph-cache");
            System.out.println("done reading");
            graphHopper.setProfiles(new Profile("car").setVehicle("car").setTurnCosts(false),new Profile("foot").setVehicle("foot").setTurnCosts(false));
            System.out.println("done reading");
            graphHopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"),new CHProfile("foot"));
            System.out.println("done reading");
            graphHopper.importOrLoad();
            System.out.println("done generating profiles");
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
