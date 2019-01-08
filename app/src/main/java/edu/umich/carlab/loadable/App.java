package edu.umich.carlab.loadable;

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
    final static String TAG = "App base";
    public Activity parentActivity;
    public String name = null;
    public List<Pair<String, String>> sensors = new ArrayList<>();
    public List<String> dependencies = new ArrayList<>();
    public String description = "";
    protected Context context;
    CLDataProvider cl;
    boolean uploadData = true;
    String URL = Constants.DEFAULT_UPLOAD_URL;

    private App() {
    }

    ;

    public App(CLDataProvider cl, Context context) {
        this.cl = cl;
        this.context = context;
    }

    @Override
    public void newData(DataMarshal.DataObject dObject) {
    }


    @Override
    public String getName() {
        if (name == null)
            throw new RuntimeException("The app must specify a name.");
        return name;
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
