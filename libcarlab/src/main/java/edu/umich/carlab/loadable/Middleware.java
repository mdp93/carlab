package edu.umich.carlab.loadable;

import edu.umich.carlab.DataMarshal;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public abstract class Middleware {
    public abstract String getName();

    public Map<String, Float> splitValues(DataMarshal.DataObject dataObject) {
        Map<String, Float> splitMap = new HashMap<>();
        String device = dataObject.device;
        splitMap.put(dataObject.sensor, dataObject.value[0]);
        return splitMap;
    }
}
