package edu.umich.carlab.apps;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import edu.umich.carlab.CLDataProvider;
import edu.umich.carlab.Constants;
import edu.umich.carlab.DataMarshal;

import java.util.ArrayList;
import java.util.List;

public abstract class App implements IApp {
    CLDataProvider cl;
    protected Context context;
    public Activity parentActivity;
    boolean uploadData = true;
    String URL = Constants.DEFAULT_UPLOAD_URL;
    final static String TAG = "App base";

    public String name = null;
    public Type type = null;
    public List<Pair<String, String>> sensors = new ArrayList<>();
    public List<String> dependencies = new ArrayList<>();
    public String description = "";

    private App() {};

    public App(CLDataProvider cl, Context context) {
        this.cl = cl;
        this.context = context;
    }

    public void newData(DataMarshal.DataObject dObject) {}


    public String getName() {
        if (name == null)
            throw new RuntimeException("The app must specify a name.");
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getDependencies() { return dependencies; }

    public List<Pair<String, String>> getSensors() {
        return sensors;
    }

    public Type getType() {
        if (type == null)
            throw new RuntimeException("The app must specify a type.");
        return type;
    }


    public String getURL() {
        return URL;
    }

    public boolean getUploadDataCheck() {
        return uploadData;
    }


    public View initializeVisualization(Activity parentActivity) {
        this.parentActivity = parentActivity;
        return null;
    }

    public void destroyVisualization() {}

    public void shutdown() {}

    /********************************
     * Activity callbacks that the MapView needs.
     * We mights'well make it part of the App standard so other's can benefit from it too
     *******************************/
    public void onCreate(Bundle bundle) {}
    public void onResume() {}
    public void onPause() {}
}
