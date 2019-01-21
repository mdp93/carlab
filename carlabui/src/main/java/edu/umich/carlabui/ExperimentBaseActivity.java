package edu.umich.carlabui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import edu.umich.carlab.CLService;
import edu.umich.carlab.DataMarshal;
import edu.umich.carlab.TriggerSession;
import edu.umich.carlab.io.AppLoader;
import edu.umich.carlab.loadable.App;
import edu.umich.carlab.net.CheckUpdate;
import edu.umich.carlab.recurring.UploadFiles;
import edu.umich.carlab.utils.Utilities;

import edu.umich.carlabui.Constants;

import java.io.File;
import java.util.*;

import static edu.umich.carlab.Constants.*;
import static edu.umich.carlabui.AppsAdapter.AppState.ACTIVE;
import static edu.umich.carlabui.Constants.ManualChoiceKey;

public class ExperimentBaseActivity extends Activity {
    Button  showMiddleware,
            manualOnOffToggle,
            pauseCarlab,
            saveCurrentTrace,
            runFromTrace,
            downloadUpdate,
            uploadFiles,
            showInfo,
            showDependenyMap;


    SharedPreferences prefs;
    CLService carlabService;
    List<AppsAdapter.AppModel> appModels;
    Map<String, Integer> appModelIndexMap;
    AppsAdapter appsAdapter;
    FrameLayout mainWrapper;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.experiment_container);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        appModels = new ArrayList<>();
        appModelIndexMap = new HashMap<>();

        wireUI();
        loadAndInitializeInfo();
        updatePauseButton();
        Utilities.scheduleOnce(this, ManualTrigger.class, 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTriggerButton();
        updatePauseButton();
        bindService(
                new Intent(
                        this,
                        CLService.class),
                mConnection,
                Context.BIND_AUTO_CREATE);

        registerReceiver(appStateReceiver, appStateIntentFilter);
        registerReceiver(clStopped, new IntentFilter(CLSERVICE_STOPPED));
        registerReceiver(updateReceiver, new IntentFilter(STATUS_CHANGED));
        registerReceiver(clStarted, new IntentFilter(DONE_INITIALIZING_CL));
    }

    @Override
    public void onStop() {
        super.onStop();

        unbindService(mConnection);

        unregisterReceiver(appStateReceiver);
        unregisterReceiver(clStopped);
        unregisterReceiver(updateReceiver);
        unregisterReceiver(clStarted);
    }

    void wireUI() {
        mainWrapper = findViewById(R.id.main_wrapper);

        showMiddleware = findViewById(R.id.showMiddleware);
        showMiddleware.setOnClickListener(loadMiddlewareActivity);

        showInfo = findViewById(R.id.showInfo);
        showInfo.setOnClickListener(loadInfoActivity);

        downloadUpdate = (Button) findViewById(R.id.downloadUpdate);
        downloadUpdate.setOnClickListener(downloadUpdateCallback);

        uploadFiles = (Button) findViewById(R.id.uploadTrips);
        uploadFiles.setOnClickListener(uploadFilesCallback);

        manualOnOffToggle = (Button) findViewById(R.id.toggleCarlab);
        manualOnOffToggle.setOnClickListener(toggleCarlab);

        pauseCarlab = (Button)findViewById(R.id.pauseCarlab);
        pauseCarlab.setOnClickListener(togglePauseCarlab);
    }


    void loadAndInitializeInfo() {
        LayoutInflater inflater = getLayoutInflater();
        View infoView = inflater.inflate(R.layout.experiment_info, null);
        TextView uidText = infoView.findViewById(R.id.uid_text);
        String UID = prefs.getString(UID_key, "UNDEFINED");
        uidText.setText("UID: " + UID);

        mainWrapper.removeAllViews();
        mainWrapper.addView(infoView);
    }


    /********************** Receivers for CarLab and status changes **********/
    BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updatePauseButton();
        }
    };

    BroadcastReceiver clStopped = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        }
    };

    BroadcastReceiver clStarted = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updatePauseButton();
        }
    };

    /************************* UI callback functions *************************/
    View.OnClickListener toggleCarlab = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            boolean isOn = prefs.getBoolean(ManualChoiceKey, false);
            prefs.edit().putBoolean(ManualChoiceKey, !isOn).commit();
            updateTriggerButton();
            sendBroadcast(new Intent(
                    ExperimentBaseActivity.this,
                    ManualTrigger.class));
        }
    };

    View.OnClickListener togglePauseCarlab = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            int sessionStateInt = prefs.getInt(edu.umich.carlab.Constants.Session_State_Key, 1);
            TriggerSession.SessionState sessionState = TriggerSession.SessionState.values()[sessionStateInt];
            if (sessionState == TriggerSession.SessionState.ON) {
                prefs.edit().putInt(
                        edu.umich.carlab.Constants.Session_State_Key,
                        TriggerSession.SessionState.PAUSED.getValue()
                ).apply();
            } else {
                prefs.edit().putInt(
                        edu.umich.carlab.Constants.Session_State_Key,
                        TriggerSession.SessionState.ON.getValue()
                ).apply();
            }
            sendBroadcast(new Intent(STATUS_CHANGED));
        }
    };

    void updateTriggerButton() {
        boolean isOn = prefs.getBoolean(ManualChoiceKey, false);
        manualOnOffToggle.setText(isOn ? "Turn Off" : "Turn On");
    }


    void updatePauseButton() {
        TriggerSession.SessionState sessionState = TriggerSession.SessionState.values()[prefs.getInt(edu.umich.carlab.Constants.Session_State_Key, 1)];
        if (sessionState == TriggerSession.SessionState.OFF) {
            pauseCarlab.setText(getString(R.string.carlab_running_button));
            pauseCarlab.setEnabled(false);
        } else {
            if (sessionState == TriggerSession.SessionState.ON) {
                pauseCarlab.setText(getString(R.string.carlab_running_button));
            } else {
                pauseCarlab.setText(getString(R.string.carlab_paused_button));
            }
            pauseCarlab.setEnabled(true);
        }
    }


    View.OnClickListener uploadFilesCallback = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            File[] localFiles = Utilities.listAllTripsInOrder(ExperimentBaseActivity.this);
            uploadFiles.setText(String.format("Upload Trips (%d)", localFiles.length));
            sendBroadcast(new Intent(ExperimentBaseActivity.this, UploadFiles.class));
        }
    };

    View.OnClickListener downloadUpdateCallback = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            boolean neededUpdate = prefs.getBoolean(Experiment_New_Version_Detected, false);
            final String shortname = prefs.getString(Experiment_Shortname, null);
            if (shortname == null) return;

            if (neededUpdate) {
                prefs.edit().putBoolean(Experiment_New_Version_Detected, false).apply();
                AlertDialog.Builder builder = new AlertDialog.Builder(ExperimentBaseActivity.this);
                builder.setMessage("Press OK to download and install the latest version of the app.")
                        .setTitle("App out of date")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                String newApkUrl = BASE_URL
                                        + "/experiment/download?shortname="
                                        + shortname;
                                Uri webpage = Uri.parse(newApkUrl);
                                startActivity(new Intent(Intent.ACTION_VIEW, webpage));
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
                sendBroadcast(new Intent(
                        ExperimentBaseActivity.this,
                        CheckUpdate.class));
            }
        }
    };



    View.OnClickListener loadMiddlewareActivity = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            LayoutInflater inflater = getLayoutInflater();
            FrameLayout middlewareGridLayout = (FrameLayout) inflater.inflate(R.layout.middleware_grid, null);
            GridView middlewareGrid = middlewareGridLayout.findViewById(R.id.middleware_grid);

            appModels.clear();

            // If CarLab is running, we can create live middleware links
            // Otherwise, we will just use the static app loader
            List<App> allApps = AppLoader.getInstance().instantiateApps(null, null);

            for (App app : allApps) {
                AppsAdapter.AppState appState = ACTIVE;
                appModels.add(new AppsAdapter.AppModel(
                        app.getName(),
                        app.getClass().getCanonicalName(),
                        appState));
            }

            // This index is useful later
            for (int i = 0; i < appModels.size(); i++) {
                String classname = appModels.get(i).className;
                appModelIndexMap.put(classname, i);
            }

            appsAdapter = new AppsAdapter(ExperimentBaseActivity.this, appModels);
            middlewareGrid.setAdapter(appsAdapter);
            middlewareGrid.setOnItemClickListener(openAppDetails);
            mainWrapper.removeAllViews();
            mainWrapper.addView(middlewareGridLayout);
        }
    };

    private AdapterView.OnItemClickListener openAppDetails = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            if (carlabService == null) return;

            AppsAdapter.AppModel appModel = appModels.get(position);
            App app = carlabService.getRunningApp(appModel.className);
            if (app == null) return;

            LayoutInflater inflater = getLayoutInflater();
            View middlewareWrapper = inflater.inflate(R.layout.middleware_wrapper, null);
            TextView middlewareTitle = middlewareWrapper.findViewById(R.id.middleware_title);
            middlewareTitle.setText(app.getName());

            FrameLayout middlewareContent = middlewareWrapper.findViewById(R.id.middleware_content);
            View appView = app.initializeVisualization(ExperimentBaseActivity.this);
            if (appView != null) middlewareContent.addView(appView);
            mainWrapper.removeAllViews();
            mainWrapper.addView(middlewareWrapper);
        }
    };


    View.OnClickListener loadInfoActivity = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            loadAndInitializeInfo();
        }
    };
    /*************************************************************************/

    /**
     * Once we receive any app state updates, we will change the app state.
     */
    IntentFilter appStateIntentFilter = new IntentFilter(edu.umich.carlab.Constants.INTENT_APP_STATE_UPDATE);
    private BroadcastReceiver appStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //activeApps = prefs.getStringSet(Constants.ACTIVE_APPS_KEY, new HashSet<String>());

            String appClassName = intent.getStringExtra("appClassName");
            DataMarshal.MessageType appState = (DataMarshal.MessageType) intent.getSerializableExtra("appState");

            // This means we haven't set up this app yet
            if (appModelIndexMap == null) return;
            if (!appModelIndexMap.containsKey(appClassName))
                return;

            int appIndex = appModelIndexMap.get(appClassName);

            switch (appState) {
                case ERROR:
                    appModels.get(appIndex).state = AppsAdapter.AppState.ERROR;
                    break;
                case DATA:
                    appModels.get(appIndex).state = AppsAdapter.AppState.DATA;
                    break;
                case STATUS:
                    appModels.get(appIndex).state = AppsAdapter.AppState.PROCESSING;
            }

            appsAdapter.notifyDataSetChanged();
        }
    };

    /************************* CarLab service binding ************************/
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            CLService.LocalBinder binder = (CLService.LocalBinder) service;
            carlabService = binder.getService();

            // TODO Initialize the middleware view if that is currently loaded
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            carlabService = null;
        }
    }
    ;
    /*************************************************************************/
}
