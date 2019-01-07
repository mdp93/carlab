package edu.umich.carlabui;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import edu.umich.carlab.CLService;
import edu.umich.carlab.Constants;
import edu.umich.carlab.apps.App;
import edu.umich.carlab.clog.UploadLog;
import edu.umich.carlab.net.CheckUpdate;
import edu.umich.carlab.recurring.UploadFiles;
import edu.umich.carlab.triggers.TriggerSession;
import edu.umich.carlab.trips.TripLog;
import edu.umich.carlab.trips.TripRecord;
import edu.umich.carlab.utils.Utilities;

import java.io.File;

import static android.view.View.INVISIBLE;
import static edu.umich.carlab.Constants.*;

public class CarLabUIBuilder {
    private TripLog tripLog;

    private SharedPreferences prefs;
    private CLService carlabService;
    private FrameLayout visWrapper;

    private Button pauseButton, checkUpdateButton, uploadFilesButton;
    private App app;
    private String shortname = "";
    private int versionNumber = -1;

    private Activity mainActivity;
    private View parentView;
    private String personID, devAddr;
    private Class<?> mainDisplayClass;

    public CarLabUIBuilder(Activity mainActivity, View parentView, String personID, String devAddr, int versionNumber, Class<?> mainDisplayClass) {
        this.mainActivity = mainActivity;
        this.parentView = parentView;
        this.personID = personID;
        this.devAddr = devAddr;
        this.versionNumber = versionNumber;
        this.mainDisplayClass = mainDisplayClass;
    }

    public void onCreate() {
        prefs = PreferenceManager.getDefaultSharedPreferences(mainActivity);
        tripLog = TripLog.getInstance(mainActivity);

        pauseButton = (Button) parentView.findViewById(R.id.pauseButton);
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int sessionStateInt = prefs.getInt(Constants.Session_State_Key, 1);
                TriggerSession.SessionState sessionState = TriggerSession.SessionState.values()[sessionStateInt];
                if (sessionState == TriggerSession.SessionState.ON) {
                    // Fire the pause action
                    prefs.edit().putInt(Constants.Session_State_Key, TriggerSession.SessionState.PAUSED.getValue()).apply();
                } else {
                    // Fire the resume action
                    prefs.edit().putInt(Constants.Session_State_Key, TriggerSession.SessionState.ON.getValue()).apply();
                }
                mainActivity.sendBroadcast(new Intent(STATUS_CHANGED));
            }
        });


        checkUpdateButton = (Button)parentView.findViewById(R.id.checkUpdate);
        checkUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkUpdate();
            }
        });

        uploadFilesButton = (Button)parentView.findViewById(R.id.flushFiles);
        uploadFilesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File[] localFiles = Utilities.listAllTripsInOrder(mainActivity);
                uploadFilesButton.setText(String.format("Upload Local Files (%d)", localFiles.length));
                mainActivity.sendBroadcast(new Intent(mainActivity, UploadFiles.class));
            }
        });


        updateButtonStyle();

        visWrapper = (FrameLayout) mainActivity.findViewById(R.id.visWrapper);

        // Hard code UID
        prefs.edit().putString(Constants.UID_key, personID).apply();
        Constants.UID = personID;

        // Hard code the Bluetooth MAC
        prefs.edit().putString(Constants.SELECTED_BLUETOOTH_KEY, devAddr).apply();

        ((TextView) mainActivity.findViewById(R.id.uid)).setText(personID + " v" + versionNumber);
        ((TextView) mainActivity.findViewById(R.id.bluetooth)).setText(devAddr);

        int sessionStateInt = prefs.getInt(Constants.Session_State_Key, 1);
        TriggerSession.SessionState sessionState = TriggerSession.SessionState.values()[sessionStateInt];
        long triggerIntervalMillis = (sessionState == TriggerSession.SessionState.OFF)
                ? Constants.wakeupCheckPeriod
                : Constants.sleepCheckPeriod;

        Utilities.schedule(mainActivity, UploadFiles.class, AlarmManager.INTERVAL_HALF_HOUR);
        Utilities.schedule(mainActivity, UploadLog.class, AlarmManager.INTERVAL_HALF_HOUR);

        if (prefs.getInt(Trip_Id_Offset, -1) == -1) {
            Utilities.keepTryingInit(mainActivity);
        }

        shortname = prefs.getString(Experiment_Shortname, "");
        checkAndRequestLocPermission();
    }


    public void checkUpdate() {
        boolean neededUpdate = prefs.getBoolean(Experiment_New_Version_Detected, false);
        if (neededUpdate) {
            prefs.edit().putBoolean(Experiment_New_Version_Detected, false).apply();
            AlertDialog.Builder builder = new AlertDialog.Builder(mainActivity);
            builder.setMessage("Press OK to download and install the latest version of the app.")
                    .setTitle("App out of date")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            String newApkUrl = BASE_URL
                                    + "/experiment/download?shortname="
                                    + shortname;
                            Uri webpage = Uri.parse(newApkUrl);
                            mainActivity.startActivity(new Intent(Intent.ACTION_VIEW, webpage));
                        }
                    });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // Cancel button.
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        } else {
            mainActivity.sendBroadcast(new Intent(mainActivity, CheckUpdate.class));
        }
    }

    public void onResume() {
        if (app != null) app.onResume();
        Intent intent = new Intent(mainActivity, CLService.class);
        mainActivity.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        updateButtonStyle();

        checkUpdate();
        mainActivity.registerReceiver(clStopped, new IntentFilter(CLSERVICE_STOPPED));
        mainActivity.registerReceiver(updateReceiver, new IntentFilter(STATUS_CHANGED));
        mainActivity.registerReceiver(clStarted, new IntentFilter(DONE_INITIALIZING_CL));
    }


    BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateButtonStyle();
        }
    };


    void updateButtonStyle() {
        TriggerSession.SessionState sessionState = TriggerSession.SessionState.values()[prefs.getInt(Constants.Session_State_Key, 1)];
        if (sessionState == TriggerSession.SessionState.OFF) {
            pauseButton.setText(mainActivity.getString(R.string.carlab_stopped_button));
            pauseButton.setEnabled(false);
        } else {
            if (sessionState == TriggerSession.SessionState.ON) {
                pauseButton.setText(mainActivity.getString(R.string.carlab_running_button));
            } else {
                pauseButton.setText(mainActivity.getString(R.string.carlab_paused_button));
            }
            pauseButton.setEnabled(true);
        }
    }


    /**
     * Function to get permission for location
     * Ideally this happens in PhoneSensors where the GPS sensor is turned on
     * But, since we need an Activity handle, this might be the most reasonable place to do it for now
     * https://developer.android.com/training/permissions/requesting.html#java
     */
    void checkAndRequestLocPermission() {
        // Here, thisActivity is the current activity
        if ((ContextCompat.checkSelfPermission(mainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)

                ||

                (ContextCompat.checkSelfPermission(mainActivity,
                        Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED)) {

            if (!ActivityCompat.shouldShowRequestPermissionRationale(mainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(mainActivity,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        Constants.TAG_CODE_PERMISSION_LOCATION);
            }
        }
    }

    /************************** CarLab Service Binding and Unbinding ************************/
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            CLService.LocalBinder binder = (CLService.LocalBinder) service;
            carlabService = binder.getService();
            showAppViewIfRunning();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {

        }
    };


    void showAppViewIfRunning() {
        // Connect to CarLab Service.
        // We assume it running our basic static app.
        // Get the view for this static app and display it
        // carlabService.addStaticApp(staticApp);
        app = carlabService.getRunningApp(mainDisplayClass.getName());
        if (app == null) {
            // This means nothing is running currently, which is entirely possible.
            // Opening this activity doesn't run the app. It's based on triggers.
            showPendingSurveys();
        } else {
            visWrapper.removeAllViews();
            View appView = app.initializeVisualization(mainActivity);
            if (appView != null) visWrapper.addView(appView);
        }
    }

    /************************** CarLab Service Binding and Unbinding ************************/


    BroadcastReceiver clStopped = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showPendingSurveys();
        }
    };
    BroadcastReceiver clStarted = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showAppViewIfRunning();
        }
    };


    void showPendingSurveys() {
        visWrapper.removeAllViews();
        LayoutInflater inflater = mainActivity.getLayoutInflater();
        View noPending = inflater.inflate(R.layout.no_pending_surveys, null);
        visWrapper.addView(noPending);
    }

    View createAndWireSurvey(final TripRecord record) {
        LayoutInflater inflater = mainActivity.getLayoutInflater();
        final View surveyParent = inflater.inflate(R.layout.individual_survey, null);
        TextView tripNumTV = surveyParent.findViewById(R.id.tripNum);
        tripNumTV.setText(mainActivity.getString(R.string.survey_details_trip, record.getID()));

        surveyParent.setMinimumHeight(400);
        ((TextView) surveyParent.findViewById(R.id.tripFrom))
                .setText(mainActivity.getString(R.string.survey_details_start, record.getStartDateString()));
        ((TextView) surveyParent.findViewById(R.id.tripTo))
                .setText(mainActivity.getString(R.string.survey_details_stop, record.getEndDateString()));
        surveyParent.findViewById(R.id.mountButton)
                .setOnClickListener(createListener("mount", surveyParent, record));
        surveyParent.findViewById(R.id.cupholderButton)
                .setOnClickListener(createListener("cupholder", surveyParent, record));
        surveyParent.findViewById(R.id.pocketButton)
                .setOnClickListener(createListener("pocket", surveyParent, record));
        surveyParent.findViewById(R.id.otherButton)
                .setOnClickListener(createListener("other", surveyParent, record));

        return surveyParent;
    }

    View.OnClickListener createListener(
            final String action,
            final View surveyParent,
            final TripRecord record
    ) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                record.setSurveyResponse(action);
                tripLog.updateRecord(record);
                surveyParent.setVisibility(INVISIBLE);
                showPendingSurveys();
            }
        };
    }

    public void onPause() {
        if (app != null)
            app.onPause();
    }

    public void onStop() {
        visWrapper.removeAllViews();
        if (app != null) {
            app.destroyVisualization();
        }
        mainActivity.unregisterReceiver(clStopped);
        mainActivity.unregisterReceiver(updateReceiver);
        mainActivity.unregisterReceiver(clStarted);
        mainActivity.unbindService(mConnection);
    }


}
