package edu.umich.carlabui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.*;
import edu.umich.carlab.CLService;
import edu.umich.carlab.DataMarshal;
import edu.umich.carlab.TriggerSession;
import edu.umich.carlab.io.AppLoader;
import edu.umich.carlab.io.DataDumpWriter;
import edu.umich.carlab.loadable.App;
import edu.umich.carlab.net.CheckUpdate;
import edu.umich.carlab.recurring.UploadFiles;
import edu.umich.carlab.utils.Utilities;

import java.io.File;
import java.util.*;

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
            dumpModeButton,
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
            updateButtons();
        }
    };
    BroadcastReceiver clStopped = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateButtons();
        }
    };
    BroadcastReceiver clStarted = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            manualOnOffToggle.setEnabled(true);
            updateButtons();
        }
    };
    /************************* UI callback functions *************************/
    View.OnClickListener toggleCarlab = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            boolean isOn = prefs.getBoolean(ManualChoiceKey, false);
            boolean setTo = !isOn;
            prefs.edit().putBoolean(ManualChoiceKey, setTo).commit();
            updateButtons();

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

    List<CharSequence> getFilenames (File[] files) {
        List<CharSequence> filenames = new ArrayList<>();
        for (File ifile : files) {
            filenames.add(ifile.getName());
        }
        return filenames;
    }

    View.OnClickListener selectTraceFileCallback = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            // Show the dialog box to select trace file

            File dumpsDir = DataDumpWriter.GetDumpsDir(ExperimentBaseActivity.this);
            Comparator<File> sortByLastModified = new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    if (f1.lastModified() == f2.lastModified()) return 0;
                    if (f1.lastModified() < f2.lastModified()) return -1;
                    return 1;
                }
            };

            List<File> allFiles = Arrays.asList(dumpsDir.listFiles());
            Collections.sort(allFiles, sortByLastModified);
            final File[] dumpFiles = allFiles.toArray(new File[]{});

            List<CharSequence> filenames = getFilenames(dumpFiles);
            filenames.add("None");

            // If they choose any of the dump files, set that value.
            // Otherwise set it to null.
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(ExperimentBaseActivity.this);
            dialogBuilder
                    .setTitle("Select Trace File")
                    .setItems(
                            filenames.toArray(new CharSequence[]{}),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    if (i == dumpFiles.length) {
                                        // This is the undefined one
                                        prefs.edit().putString(Load_From_Trace_Key, null).commit();
                                    } else {
                                        Toast.makeText(
                                                getApplicationContext(),
                                                String.format("Showing this file: %s", dumpFiles[i]),
                                                Toast.LENGTH_SHORT).show();
                                        prefs.edit().putString(Load_From_Trace_Key, dumpFiles[i].toString()).commit();
                                    }

                                    updateButtons();
                                }


                            });
            AlertDialog dialog = dialogBuilder.create();
            dialog.show();
        }
    };

    View.OnClickListener dumpDataCallback = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Boolean dumpMode = prefs.getBoolean(Dump_Data_Mode_Key, false);

            // If dump mode is OFF, then we turn it on along with manual switch to turn on carlab
            if (!dumpMode)
                prefs.edit()
                        .putBoolean(Dump_Data_Mode_Key, true)
                        .putBoolean(ManualChoiceKey, true)
                        .commit();
            // Else we just turn off carlab. Dump mode will be turned off once we're done saving in CL service
            else
                prefs.edit().putBoolean(ManualChoiceKey, false).commit();

            updateButtons();

            // If dump mode is now set to 1, we also want to turn on carlab.
            // If it is set to 0, we also want to stop the carlab dump.

            sendBroadcast(new Intent(
                    ExperimentBaseActivity.this,
                    ManualTrigger.class));
        }
    };

    /**
     * Function to get permission for location
     * Ideally this happens in PhoneSensors where the GPS sensor is turned on
     * But, since we need an Activity handle, this might be the most reasonable place to do it for now
     * https://developer.android.com/training/permissions/requesting.html#java
     */
    void checkAndRequestLocPermission() {
        // Here, thisActivity is the current activity
        if ((ContextCompat.checkSelfPermission(ExperimentBaseActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)

                ||

                (ContextCompat.checkSelfPermission(ExperimentBaseActivity.this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED)) {

            if (!ActivityCompat.shouldShowRequestPermissionRationale(ExperimentBaseActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(ExperimentBaseActivity.this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        edu.umich.carlab.Constants.TAG_CODE_PERMISSION_LOCATION);
            }
        }
    }


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
            updateButtons();
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
        updateButtons();
        checkAndRequestLocPermission();

        manualOnOffToggle.setEnabled(false);
        Utilities.scheduleOnce(this, ManualTrigger.class, 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateButtons();

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

        dumpModeButton = (Button) findViewById(R.id.saveCurrentSessionButton);
        dumpModeButton.setOnClickListener(dumpDataCallback);

        runFromTrace = (Button)findViewById(R.id.loadFromTrace);
        runFromTrace.setOnClickListener(selectTraceFileCallback);
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


    void updateButtons() {
        TriggerSession.SessionState sessionState = TriggerSession
                .SessionState
                .values()[prefs.getInt(edu.umich.carlab.Constants.Session_State_Key, 1)];

        Boolean dumpMode = prefs.getBoolean(Dump_Data_Mode_Key, false);

        String traceFile = prefs.getString(Load_From_Trace_Key, null);

        // Update the pause button
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


        // Update the carlab on/off button
        if (carlabService == null || carlabService.carlabCurrentlyStarting()) {
            manualOnOffToggle.setEnabled(false);
            manualOnOffToggle.setText("Starting");
        } else if (dumpMode) {
            manualOnOffToggle.setEnabled(false);
            manualOnOffToggle.setText("Dumping data");
        } else {
            manualOnOffToggle.setEnabled(true);
            manualOnOffToggle.setText(carlabService.isCarLabRunning() ? "Turn Off" : "Turn On");
        }

        // Update the dump mode button
        if (carlabService == null || carlabService.carlabCurrentlyStarting()) {
            dumpModeButton.setEnabled(false);
            dumpModeButton.setText("Starting");
        } else if (!carlabService.isCarLabRunning()) {
            dumpModeButton.setEnabled(true);
            dumpModeButton.setText("Start Data Dump");
        } else if (carlabService.isCarLabRunning() && !dumpMode) {
            dumpModeButton.setEnabled(false);
            dumpModeButton.setText("CarLab running");
        } else if (carlabService.isCarLabRunning() && dumpMode){
            dumpModeButton.setEnabled(true);
            dumpModeButton.setText("Stop Data Dump");
        }

        // Update the trace button
        if (carlabService == null || carlabService.carlabCurrentlyStarting()) {
            runFromTrace.setEnabled(false);
            runFromTrace.setText("Starting");
        } else if (carlabService.isCarLabRunning()) {
            runFromTrace.setEnabled(false);
        } else {
            runFromTrace.setEnabled(true);
            if (traceFile == null) {
                runFromTrace.setText("Load Trace");
            } else {
                runFromTrace.setText("Change Trace File");
            }
        }
    }
    /*************************************************************************/

    @Override
    public void onFragmentInteraction(Uri uri) {
    }
}
