package edu.umich.carlab.io;

import android.os.AsyncTask;
import android.util.Log;
import edu.umich.carlab.Constants;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by arunganesan on 2/27/18.
 */

public class UploadSurveyTask extends AsyncTask { ;
    int tripid;
    String response;

    final String TAG = "UploadSurvey";

    public UploadSurveyTask(int thisTripId, File surveyFile) {
        // Send to server
        this.tripid = thisTripId;

        try {
            FileInputStream fin = new FileInputStream(surveyFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            response = sb.toString();
            fin.close();
        } catch (Exception e) {
            Log.e(TAG, "Error reading survey file");
        }
    }

    @Override
    protected Object doInBackground(Object[] objects) {
        try {
            URL url = new URL(Constants.SURVEY_UPLOAD_URL);
            MultipartUtility mpu = new MultipartUtility(url);
            mpu.addFormField("tripid", String.format("%d", tripid));
            mpu.addFormField("uid", Constants.UID);
            mpu.addFormField("response", response);
            mpu.finish();
            Log.v(TAG, "Upload succeeded!");

        } catch (MalformedURLException mue) {

        } catch (IOException ieo) {
            Log.e(TAG, "Upload filed due to error: " + ieo.getMessage());
        }


        return null;
    }
}
