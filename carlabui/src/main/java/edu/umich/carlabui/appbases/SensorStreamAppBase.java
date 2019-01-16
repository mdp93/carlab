package edu.umich.carlabui.appbases;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.umich.carlab.hal.HardwareAbstractionLayer;
import edu.umich.carlab.loadable.App;
import edu.umich.carlab.CLDataProvider;
import edu.umich.carlab.Constants;
import edu.umich.carlab.DataMarshal;
import edu.umich.carlab.sensors.PhoneSensors;

/**
 * A basic sensor stream app.
 *
 * This app accepts new data coming in from {@link #newData(DataMarshal.DataObject)} visualizes
 * them as streams in a real-time line graph. All incoming data is normalized to 0-1 to make the
 * visualization clearer.
 */

public class SensorStreamAppBase extends App {
    long starttime = 0, latesttime = 0;
    final float MAX_TIMESCALE = 10;
    final String TAG = "SensorStreamParentApp";
    Map<String, List<Entry>> entries;
    Map<String,LineDataSet> dataset;
    LineData lineData;
    LineChart lineChart;
    Object threadLock = new Object();


    List<Integer> colors;
    List<Pair<String, String>> onlyDoDeviceSensors;

    long UPDATE_EVERY = 50;
    Map<String, Long> lastUpdateTimer = new HashMap<>();

    public SensorStreamAppBase(CLDataProvider cl, Context context) {
        super(cl, context);
        colors = new ArrayList<>();
        colors.add(Color.BLACK);
        colors.add(Color.BLUE);
        colors.add(Color.RED);
        colors.add(Color.GREEN);
        colors.add(Color.MAGENTA);
        colors.add(Color.CYAN);
        colors.add(Color.YELLOW);
        colors.add(Color.WHITE);
        onlyDoDeviceSensors = new ArrayList<>();
    }

    public void addLineGraph(String device, String sensor) {
        String valuesKey = device + "/" + sensor;
        onlyDoDeviceSensors.add(new Pair(device, sensor));
        lastUpdateTimer.put(valuesKey, 0L);
    }

    public void newData(final DataMarshal.DataObject fullDataObject) {
        // Split it first before acting on it
        if ((fullDataObject.dataType != DataMarshal.MessageType.DATA)
            || (parentActivity == null))
            return;

        List<DataMarshal.DataObject> splitObjects = HardwareAbstractionLayer.splitValues(fullDataObject);
        for (final DataMarshal.DataObject dObject : splitObjects) {
            final String device = dObject.device;
            final String sensor = dObject.sensor;
            final String valuesKey = device + "/" + sensor;

            final long currtime = System.currentTimeMillis();

            if ((!onlyDoDeviceSensors.contains(new Pair<>(device, sensor)))
                    || (currtime < lastUpdateTimer.get(valuesKey) + UPDATE_EVERY)
                    || parentActivity == null)
                continue;


            if (lineChart == null)  return;

            parentActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (dObject.value == null) return;
                        float value = dObject.value[0];

                        long longtime = dObject.time;
                        if (starttime == 0) starttime = longtime;
                        latesttime = Math.max(latesttime, longtime);
                        double timeInSeconds = (longtime - starttime) / 1000.0;


                        if (!dataset.containsKey(valuesKey)) {
                            entries.put(valuesKey, new ArrayList<Entry>());
                            //entries.get(valuesKey).add(new Entry(0,0));
                            dataset.put(valuesKey, new LineDataSet(entries.get(valuesKey), valuesKey));

                            lineData.addDataSet(dataset.get(valuesKey));
                            dataset.get(valuesKey).setColor(colors.get(dataset.size() % colors.size()));
                            dataset.get(valuesKey).setDrawCircles(false);
                            dataset.get(valuesKey).setLineWidth(5);
                            dataset.get(valuesKey).setDrawValues(false);
                            dataset.get(valuesKey).setMode(LineDataSet.Mode.CUBIC_BEZIER);
                        }

                        entries.get(valuesKey).add(new Entry((float)timeInSeconds, value));

                        // Notify and reload everything
                        dataset.get(valuesKey).notifyDataSetChanged();
                        lineData.notifyDataChanged();

                        if (lineChart != null) {
                            synchronized (lineChart) {
                                lineChart.notifyDataSetChanged();
                                // Set end points
                                float startPoint = Math.max(0, ((float) ((double) (latesttime - starttime) / 1000.0) - MAX_TIMESCALE));
                                lineChart.setVisibleXRangeMaximum(MAX_TIMESCALE);
                                lineChart.moveViewToX(startPoint);
                                lastUpdateTimer.put(valuesKey, currtime);
                            }
                        }

                    } catch (NumberFormatException nfe) {
                        // Not a valid number. Skipping this.
                    }
                }
            });
        }
    }



    @Override
    public View initializeVisualization(Activity parentActivity) {
        //if (1==1) return null;
        Log.v(TAG, "Thread lock ID: " + System.identityHashCode(threadLock));
        super.initializeVisualization(parentActivity);
        entries = new HashMap<>();
        dataset = new HashMap<>();
        lineData = new LineData();
        lineChart = new LineChart(context);
        lineChart.setData(lineData);
        lineChart.invalidate();
        return lineChart;
    }

    @Override
    public void destroyVisualization() {
        synchronized (lineChart) {
            lineChart = null;
        }
    }
}
