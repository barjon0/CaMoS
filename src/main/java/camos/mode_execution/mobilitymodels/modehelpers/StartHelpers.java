package camos.mode_execution.mobilitymodels.modehelpers;

import camos.mode_execution.ModeExecutionManager;
import org.json.JSONArray;
import org.json.JSONObject;

public class StartHelpers {

    public static void readInConfigForModes(JSONObject config, JSONObject modejson, String[] modes){
        if(modejson.has("global") && modejson.get("global") instanceof JSONObject && modejson.getJSONObject("global").has("required")){
            JSONArray requiredModeAttributeArray = modejson.getJSONObject("global").getJSONArray("required");
            String[] requiredModeAttributes = new String[requiredModeAttributeArray.length()];
            for(int i=0; i<requiredModeAttributeArray.length(); i++){
                if(requiredModeAttributeArray.get(i) instanceof String){
                    requiredModeAttributes[i] = (String) requiredModeAttributeArray.get(i);
                }else throw new RuntimeException("'modes' parameter not a list of Strings.");
            }

            if(modejson.getJSONObject("global").has("optional")){
                JSONArray optionalModeAttributeArray = modejson.getJSONObject("global").getJSONArray("optional");
                String[] optionalModeAttributes = new String[optionalModeAttributeArray.length()];
                for(int i=0; i<optionalModeAttributeArray.length(); i++){
                    if(optionalModeAttributeArray.get(i) instanceof String){
                        optionalModeAttributes[i] = (String) optionalModeAttributeArray.get(i);
                    }else throw new RuntimeException("'modes' parameter not a list of Strings.");
                }
            }

            for(String mode : modes){
                checkThatModeIsFine(modejson,mode,requiredModeAttributes);
                readInConfigForMode(config,modejson,mode);
            }

        }else throw new RuntimeException("object 'global' missing or attribute 'required' of 'global' missing.");
    }


    public static void checkThatModeIsFine(JSONObject modejson, String modeName, String[] requiredModeAttributes){
        if(modejson.has(modeName) && modejson.get(modeName) instanceof JSONObject){
            JSONObject modeObject = modejson.getJSONObject(modeName);
            for(String required : requiredModeAttributes){
                if(!modeObject.keySet().contains(required)){
                    throw new RuntimeException("mode lacks required attribute '" + required + "'.");
                }
                if(required.equals("comparing") && modeObject.getBoolean("comparing")){
                    if(!modeObject.keySet().contains("compare to")){ //TODO
                        throw new RuntimeException("mode is comparing but lacks attribute 'compare to'.");
                    }
                }
            }
        }else throw new RuntimeException("mode with name " + modeName + "is missing or not a valid JSONObject.");
    }


    public static void readInConfigForMode(JSONObject config, JSONObject modejson, String modeName){
        JSONObject modeConfig = new JSONObject();
        JSONObject modeObject = modejson.getJSONObject(modeName);
        if(!modeObject.has("config")){
            throw new RuntimeException("mode with name " + modeName + "lacks attribute 'config'.");
        }
        for(String key : modeObject.getJSONObject("config").keySet()){
            if(config.has(key)){
                if(modeConfig.has(key)){
                    //TODO überprüfen, dass config objekte richtig strukturiert
                }
                modeConfig.put(key,config.get(key));
            }else throw new RuntimeException("parameter '" + key + "' is missing in config json, needed for mode " + modeName + ".");
        }
        modeObject.put("config",modeConfig);
       ModeExecutionManager.modeValues.put(modeName,modeObject);
    }

}
