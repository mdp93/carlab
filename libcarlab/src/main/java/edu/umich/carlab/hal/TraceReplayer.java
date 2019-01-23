package edu.umich.carlab.hal;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import edu.umich.carlab.CLService;
import edu.umich.carlab.Constants;
import edu.umich.carlab.DataMarshal;
import edu.umich.carlab.ManualTrigger;
import edu.umich.carlab.io.DataDumpWriter;

import java.io.File;
import java.util.List;

import static edu.umich.carlab.Constants.Load_From_Trace_Key;
import static edu.umich.carlab.Constants.ManualChoiceKey;

public class TraceReplayer implements Runnable {
    final int INITIAL_WAIT_TIME = 3000;

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

        Long newStartTime = System.currentTimeMillis();
        Long dataOffsetTime = traceData.get(0).time;

        DataMarshal.DataObject dataObject;

        long previousDataTime = 0;

        for (int i = 0; i < traceData.size(); i++) {
            dataObject = traceData.get(i);
            previousDataTime = dataObject.time;
            dataObject.time -= dataOffsetTime + newStartTime;
            dataObject.tripid = tripID;
            carlabService.newData(dataObject);

            if (i < traceData.size() - 1) {
                try {
                    Thread.sleep(traceData.get(i+1).time - previousDataTime);
                } catch (Exception e) {}
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
