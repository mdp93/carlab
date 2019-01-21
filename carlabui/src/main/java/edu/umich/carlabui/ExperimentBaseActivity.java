package edu.umich.carlabui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.*;
import edu.umich.carlab.CLService;
import edu.umich.carlab.DataMarshal;
import edu.umich.carlab.TriggerSession;
import edu.umich.carlab.io.AppLoader;
import edu.umich.carlab.loadable.App;
import edu.umich.carlab.net.CheckUpdate;
import edu.umich.carlab.recurring.UploadFiles;
import edu.umich.carlab.utils.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.umich.carlab.Constants.*;
import static edu.umich.carlabui.AppsAdapter.AppState.ACTIVE;
import static edu.umich.carlabui.Constants.ManualChoiceKey;

public class ExperimentBaseActivity extends AppCompatActivity
        implements InfoViewFragment.OnFragmentInteractionListener,
            MiddlewareGridFragment.OnFragmentInteractionListener,
            AppViewFragment.OnFragmentInteractionListener {
    Button showMiddleware,
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
    FrameLayout mainWrapper;

    boolean mBound = false;
    InfoViewFragment infoFragment = new InfoViewFragment();
    MiddlewareGridFragment middlewareGridFragment = new MiddlewareGridFragment();

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
            updateTriggerButton();
        }
    };
    BroadcastReceiver clStarted = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updatePauseButton();
            manualOnOffToggle.setEnabled(true);
            updateTriggerButton();
        }
    };
    /************************* UI callback functions *************************/
    View.OnClickListener toggleCarlab = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            boolean isOn = prefs.getBoolean(ManualChoiceKey, false);
            boolean setTo = !isOn;
            prefs.edit().putBoolean(ManualChoiceKey, setTo).commit();
            updateTriggerButton();

            if (setTo) {
                manualOnOffToggle.setText("Starting");
                manualOnOffToggle.setEnabled(false);
            }

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
    View.OnClickListener loadInfoActivity = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            loadAndInitializeInfo();
        }
    };

    View.OnClickListener loadMiddlewareActivity = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
           replaceFragmentWithAnimation(middlewareGridFragment, "MIDDLEWARE");
        }
    };

    /************************* CarLab service binding ************************/
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            CLService.LocalBinder binder = (CLService.LocalBinder) service;
            carlabService = binder.getService();

            mBound = true;
            prefs   .edit()
                    .putBoolean(
                            ManualChoiceKey,
                            carlabService.isCarLabRunning())
                    .commit();
            updateTriggerButton();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            carlabService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        requestWindowFeature(Window.FEATURE_NO_TITLE);//will hide the title
        getSupportActionBar().hide();

        setContentView(R.layout.experiment_container);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (prefs.getInt(Trip_Id_Offset, -1) == -1) {
            Utilities.keepTryingInit(this);
        }

        wireUI();
        loadAndInitializeInfo();
        updatePauseButton();

        manualOnOffToggle.setEnabled(false);
        Utilities.scheduleOnce(this, ManualTrigger.class, 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTriggerButton();
        updatePauseButton();

        manualOnOffToggle.setEnabled(false);
        bindService(
                new Intent(
                        this,
                        CLService.class),
                mConnection,
                Context.BIND_AUTO_CREATE);

        registerReceiver(clStopped, new IntentFilter(CLSERVICE_STOPPED));
        registerReceiver(updateReceiver, new IntentFilter(STATUS_CHANGED));
        registerReceiver(clStarted, new IntentFilter(DONE_INITIALIZING_CL));
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }

        unregisterReceiver(clStopped);
        unregisterReceiver(updateReceiver);
        unregisterReceiver(clStarted);
    }

    void enableOnOffButton() {
        if (carlabService == null)
            manualOnOffToggle.setEnabled(true);
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
        manualOnOffToggle.setEnabled(false);

        pauseCarlab = (Button) findViewById(R.id.pauseCarlab);
        pauseCarlab.setOnClickListener(togglePauseCarlab);
    }

    // https://stackoverflow.com/questions/4932462/animate-the-transition-between-fragments
    public void replaceFragmentWithAnimation(Fragment fragment, String tag) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        transaction.replace(R.id.main_wrapper, fragment);
        transaction.addToBackStack(tag);
        transaction.commit();
    }
    /*************************************************************************/

    void loadAndInitializeInfo() {
        replaceFragmentWithAnimation(infoFragment, "TAG");
    }

    void updateTriggerButton() {
        if (carlabService == null || carlabService.carlabCurrentlyStarting()) {
            manualOnOffToggle.setEnabled(false);
            manualOnOffToggle.setText("Starting");
        } else {
            manualOnOffToggle.setEnabled(true);
            manualOnOffToggle.setText(carlabService.isCarLabRunning() ? "Turn Off" : "Turn On");
        }
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
    /*************************************************************************/

    @Override
    public void onFragmentInteraction(Uri uri) {
    }
}
