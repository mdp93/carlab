package edu.umich.carlab.loadable;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.util.Pair;
import android.view.View;
import edu.umich.carlab.CLDataProvider;
import edu.umich.carlab.Constants;
import edu.umich.carlab.DataMarshal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class App implements IApp {
    final static String TAG = "App base";
    public Activity parentActivity;
    public String name = null;
    public String middlewareName = null;
    public List<Pair<String, String>> sensors = new ArrayList<>();
    public List<String> dependencies = new ArrayList<>();
    public String description = "";
    public CLDataProvider cl;
    public boolean foregroundApp = false;
    protected Context context;
    boolean uploadData = true;
    String URL = Constants.DEFAULT_UPLOAD_URL;
    public SharedPreferences prefs;


    Map<String, Map<String, DataMarshal.DataObject>> latestData = new HashMap<>();
    Map<String, Map<String, Long>> latestDataTime = new HashMap<>();

    private App() {
    }

    public App(CLDataProvider cl, Context context) {
        this.cl = cl;
        this.context = context;

        if (context != null)
            prefs = context.getSharedPreferences(this.getClass().getCanonicalName(), Context.MODE_PRIVATE);
    }


    protected double loadValue(String variableName, Double defaultValue) {
        return prefs.getFloat(variableName, defaultValue.floatValue());
    }

    protected void saveValue(String variableName, Double value) {
        prefs.edit().putFloat(variableName, value.floatValue()).commit();
    }

    public boolean isValidData(DataMarshal.DataObject dObject) {
        return (dObject.dataType == DataMarshal.MessageType.DATA)
                && (dObject.value != null);
    }

    @CallSuper
    @Override
    public void newData(DataMarshal.DataObject dObject) {
        if (!isValidData(dObject)) return;

        if (!latestData.containsKey(dObject.device)) {
            latestData.put(dObject.device, new HashMap<String, DataMarshal.DataObject>());
            latestDataTime.put(dObject.device, new HashMap<String, Long>());
        }

        latestData.get(dObject.device).put(dObject.sensor, dObject);
        latestDataTime.get(dObject.device).put(dObject.sensor, System.currentTimeMillis());
    }

    public DataMarshal.DataObject getLatestData(String device, String sensor) {
        if (!latestData.containsKey(device)) return null;
        if (!latestData.get(device).containsKey(sensor)) return null;
        return latestData.get(device).get(sensor);
    }


    public void subscribe(String device, String sensor) {
        sensors.add(new Pair<>(device, sensor));
    }

    @Override
    public String getName() {
        if (name == null)
            throw new RuntimeException("The app must specify a name.");
        return name;
    }


    public String getMiddlewareName() {
        if (middlewareName == null)
            throw new RuntimeException("The app must specify a middleware name");
        return middlewareName;
    }

    @Override
    public List<Pair<String, String>> getSensors() {
        return sensors;
    }


    @Override
    public View initializeVisualization(Activity parentActivity) {
        this.parentActivity = parentActivity;
        return null;
    }

    @Override
    public void destroyVisualization() {
    }

    @Override
    public void shutdown() {
    }

    public void outputData(String device, String sensor, Float value) {
        outputData(device, sensor, new Float[] { value });
    }

    public void outputData(String device, String sensor, Float[] values) {
        DataMarshal.DataObject d = new DataMarshal.DataObject();
        d.time = System.currentTimeMillis();
        d.device = device;
        d.sensor = sensor;
        d.dataType = DataMarshal.MessageType.DATA;
        d.value = values;
        cl.newData(d);
    }

    public void outputData(String APP, DataMarshal.DataObject dObject, String sensor, Float value) {
        outputData(APP, dObject, sensor, new Float[]{value});
    }

    public DataMarshal.DataObject outputData(
            String APP,
            DataMarshal.DataObject dObject,
            String sensor,
            Float[] value) {
        DataMarshal.DataObject secondaryDataObject = dObject.clone();
        secondaryDataObject.device = APP;
        secondaryDataObject.sensor = sensor;
        secondaryDataObject.value = value;
        cl.newData(secondaryDataObject);
        return secondaryDataObject;
    }

    /********************************
     * Activity callbacks that the MapView needs.
     * We mights'well make it part of the App standard so other's can benefit from it too
     *******************************/
    public void onCreate(Bundle bundle) {
    }

    public void onResume() {
    }

    public void onPause() {
    }
}
