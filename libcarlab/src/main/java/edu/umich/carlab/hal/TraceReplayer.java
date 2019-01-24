package edu.umich.carlab.hal;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import edu.umich.carlab.CLService;
import edu.umich.carlab.Constants;
import edu.umich.carlab.DataMarshal;
import edu.umich.carlab.ManualTrigger;
import edu.umich.carlab.io.DataDumpWriter;

import java.io.File;
import java.util.List;

import static edu.umich.carlab.Constants.*;

public class TraceReplayer implements Runnable {
    final int INITIAL_WAIT_TIME = 3000;
    final String TAG = "TraceReplayer";

    final long broadcastEvery = 500L;
    long lastBroadcast = 0L;

    CLService carlabService;
    File ifile;
    String tripID;
    List<DataMarshal.DataObject> traceData;
    SharedPreferences prefs;

    public TraceReplayer (CLService carlabService, String filename, int tripID) {
        this.carlabService = carlabService;
        ifile = new File(filename);
        this.tripID = "" + tripID;
        DataDumpWriter dataDumpWriter = new DataDumpWriter(carlabService);
        traceData = dataDumpWriter.readData(ifile);
        prefs = PreferenceManager.getDefaultSharedPreferences(carlabService);
    }

    @Override
    public void run () {
        try {
            Thread.sleep(INITIAL_WAIT_TIME);
        } catch (Exception e) {}

        Long startTime = System.currentTimeMillis();
        Long newStartTime = System.currentTimeMillis();
        Long dataOffsetTime = traceData.get(0).time;
        Long sleepTime = 0L;
        String uid = prefs.getString(UID_key, null);
        if (uid == null) {
            String errorMessage = "UID is null. Something went wrong with replay";
            Toast.makeText(carlabService, errorMessage, Toast.LENGTH_SHORT).show();
            Log.e(TAG, errorMessage);
            return;
        }

        DataMarshal.DataObject dataObject;

        long previousDataTime = 0;
        long currTime = 0;

        for (int i = 0; i < traceData.size(); i++) {
            dataObject = traceData.get(i);
            previousDataTime = dataObject.time;
            dataObject.time -= dataOffsetTime;
            dataObject.time += newStartTime;
            dataObject.tripid = tripID;
            dataObject.uid = uid;
            carlabService.newData(dataObject);

            if (i < traceData.size() - 1) {
                try {
                    sleepTime = traceData.get(i+1).time - previousDataTime;
//                    sleepTime = 1L;
                    if (sleepTime > 0)
                        Thread.sleep(sleepTime);
                } catch (Exception e) {}
            }

            currTime = System.currentTimeMillis();
            if (currTime > lastBroadcast + broadcastEvery) {
                Intent intent = new Intent(REPLAY_STATUS);
                intent.putExtra(REPLAY_PERCENTAGE, (double)i / traceData.size());
                carlabService.sendBroadcast(intent);
                lastBroadcast = currTime;
            }
        }

        prefs
                .edit()
                .putString(Load_From_Trace_Key, null)
                .putBoolean(ManualChoiceKey, false).commit();

        carlabService.sendBroadcast(new Intent(
                carlabService,
                ManualTrigger.class));
    }
}
