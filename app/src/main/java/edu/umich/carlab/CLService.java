package edu.umich.carlab;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;
import edu.umich.carlab.hal.HardwareAbstractionLayer;
import edu.umich.carlab.io.AppLoader;
import edu.umich.carlab.io.CLTripWriter;
import edu.umich.carlab.loadable.App;
import edu.umich.carlab.loadable.IApp;
import edu.umich.carlab.loadable.Middleware;
import edu.umich.carlab.trips.TripLog;
import edu.umich.carlab.trips.TripRecord;
import edu.umich.carlab.utils.NotificationsHelper;
import edu.umich.carlab.utils.Utilities;

import java.util.*;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;
import static edu.umich.carlab.Constants.*;

/**
 * CL Service has life beyond the presense of an activity.
 * CL Service is responsible for keeping Apps alive.
 * When CL starts for the first time, it will set everything up and by default turn on the master
 * switch. The master switch fires an intent with the appropriate action and calls CLService's
 * onStartCommand().
 * <p>
 * * When master switch is turned on,
 * Start CLS as foreground service (it sets notification, won't die even if app swipe closed)
 * CLS instantiates all Apps that are active and has public functions for turning on/off apps
 * It reads the list of active apps
 * For each app, it gets the list of required sensors
 * For each sensors of the app, it uses the Hal to register that sensor
 * And it registers a listener for that sensor
 * Then, when it receives data, it goes through all apps that need that data, and sends it to the app
 * * When master switch is turned off
 * Call CLS destructor (through binding)
 * Unset the notifications
 * Tell all apps that we’re going to shut them off
 * Call stopSelf()
 */

public class CLService extends Service implements CLDataProvider {
    static boolean runningDataCollection = false;
    final String TAG = "CarLab Service";
    final int CL_NOTIFICATION_ID = CARLAB_NOTIFICATION_ID;
    final long DATA_UPDATE_INTERVAL_IN_MS = 100;
    final long UPDATE_NOTIFICATION_INTERVAL = 5000;
    final IBinder mBinder = new LocalBinder();
    public long startTimestamp;
    // Downclock everything to 50 ms at least.
    // Eventually we want to add the option for apps to specify the data rate.
    Map<String, Map<String, Long>> lastDataUpdate = new HashMap<>();
    long lastNotificationUpdate = 0;
    Map<String, DataMarshal.MessageType> lastStateUpdate = new HashMap<>();
    TripLog tripLog;
    TripRecord currentTrip;
    SharedPreferences prefs;
    HardwareAbstractionLayer hal;
    CLTripWriter clTripWriter;
    Map<String, App> runningApps;
    Map<String, Set<String>> dataMultiplexing;

    public CLService() {
        Log.e(TAG, "Service constructor");
    }

    public static void turnOffCarLab(Context context) {
        Intent intent = new Intent(context, CLService.class);
        intent.setAction(Constants.MASTER_SWITCH_OFF);
        context.startService(intent);
    }

    public static void turnOnCarLab(Context context) {
        // This means we havent' connected in a while.
        // And this re-establishment isn't due to a temporary break
        // And we just connected to the actual OBD device

        Intent intent = new Intent(context, CLService.class);
        intent.setAction(Constants.MASTER_SWITCH_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        startTimestamp = System.currentTimeMillis();
        prefs = getDefaultSharedPreferences(this);
        tripLog = TripLog.getInstance(this);
        Log.e(TAG, "Service On create: " + startTimestamp);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(Constants.MASTER_SWITCH_ON)) {
            // Check if we're already running. If we are, then unload the running app and then start this
            if (isCarLabRunning()) {
                shutdownSequence();
            }

            int tripOffset = prefs.getInt(Trip_Id_Offset, -1);
            if (tripOffset == -1)
                return Service.START_NOT_STICKY;

            Log.e(TAG, "Service on start cmd: " + startTimestamp);
            NotificationsHelper.setNotificationForeground(this, NotificationsHelper.Notifications.COLLECTING_DATA);
            currentTrip = tripLog.startTrip(tripOffset);
            clTripWriter = new CLTripWriter(this, currentTrip);

            Constants.tripid = currentTrip.getID().toString();
            startupSequence();
            Toast.makeText(this, "CarLab starting data collection. T=" + currentTrip.getID(), Toast.LENGTH_SHORT).show();
            return Service.START_NOT_STICKY;
        } else if (intent.getAction().equals(Constants.MASTER_SWITCH_OFF)) {
            Toast.makeText(this, "Turning off CarLab data collection.", Toast.LENGTH_SHORT).show();
            shutdownSequence();
            // Stop this service
            stopForeground(true);
            stopSelf();
            return Service.START_NOT_STICKY;
        }

        return Service.START_NOT_STICKY;
    }

    public boolean isCarLabRunning() {
        return runningDataCollection;
    }

    /**
     * Outside people can bind to this service and get running apps.
     * This is used in app details. That binds to this
     * service and renders the visualization of the running app.
     *
     * @param classname
     * @return
     */
    public App getRunningApp(String classname) {
        if (runningApps == null) return null;
        if (!runningApps.containsKey(classname))
            return null;
        return runningApps.get(classname);
    }

    /**
     * The app fragment will bind to this on resume and get the initial values from this function
     *
     * @param classname
     * @return
     */
    public DataMarshal.MessageType getLastStateUpdate(String classname) {
        if (!lastStateUpdate.containsKey(classname))
            return null;
        else
            return lastStateUpdate.get(classname);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.e(TAG, "Service bind: " + startTimestamp);
        return mBinder;
    }

    private void shutdownSequence() {
        if (!runningDataCollection) return;

        runningDataCollection = false;
        Log.e(TAG, "Shutting down CL! Thread ID: " + Thread.currentThread().getId());
        // Turn off all sensors
        if (hal != null)
            hal.turnOffAllSensors();

        // Shut down all apps
        if (runningApps != null) {
            shutdownAllApps();
            runningApps.clear();
        }


        // Reset the data multiplexer
        if (dataMultiplexing != null)
            dataMultiplexing.clear();


        dataMultiplexing = null;
        runningApps = null;

        if (clTripWriter != null) {
            clTripWriter.stopTrip();
            Utilities.wakeUpMainActivity(this);
        }

        Intent stoppedIntent = new Intent();
        stoppedIntent.setAction(CLSERVICE_STOPPED);
        sendBroadcast(stoppedIntent);
    }

    /**
     * It reads the list of active apps
     * For each app, it gets the list of required sensors
     * For each sensors of the app, it uses the Hal to register that sensor
     * And it registers a listener for that sensor
     * Then, when it receives data, it goes through all apps that need that data, and sends it to the app
     */
    private void startupSequence() {
        runningDataCollection = true;
        Runnable startupTask = new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "Starting CL! Thread ID: " + Thread.currentThread().getId());

                runningApps = new HashMap<>();
                dataMultiplexing = new HashMap<>();
                clTripWriter.startNewTrip();

                hal = new HardwareAbstractionLayer(CLService.this);
                bringAppsToLife();
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
                Log.v(TAG, "Just returned from bringing apps to life");
                registerAllSensors();

                Log.v(TAG, "Finished startup sequence. We are multiplexing these keys: ");
                for (Map.Entry<String, Set<String>> appEntry : dataMultiplexing.entrySet()) {
                    Log.v(TAG, "Key: " + appEntry.getKey());
                    for (String appName : appEntry.getValue()) {
                        Log.v(TAG, "\t" + appName);
                    }
                }


                // We can enable master switch now
                Intent doneIntent = new Intent();
                doneIntent.setAction(DONE_INITIALIZING_CL);
                CLService.this.sendBroadcast(doneIntent);
            }
        };

        Thread startupThread = new Thread(startupTask);
        startupThread.setName("Startup Thread");
        startupThread.start();
    }


    /**
     * Registers all sensors for all apps
     */
    private void registerAllSensors() {
        String device, sensor, multiplexKey;

        for (Map.Entry<String, App> appEntry : runningApps.entrySet()) {
            List<Pair<String, String>> sensors = appEntry.getValue().getSensors();
            for (Pair<String, String> devSensor : sensors) {
                device = devSensor.first;
                sensor = devSensor.second;
                // Add this to the sensor multiplexing
                multiplexKey = toMultiplexKey(device, sensor);
                if (!dataMultiplexing.containsKey(multiplexKey))
                    dataMultiplexing.put(multiplexKey, new HashSet<String>());
                dataMultiplexing.get(multiplexKey).add(appEntry.getKey());
                lastDataUpdate.get(appEntry.getKey()).put(multiplexKey, 0L);
                // Then turn it on a little while later
                try {
                    Thread.sleep(250);
                } catch (Exception e) {
                }
                hal.turnOnSensor(device, sensor);
            }
        }
    }

    /**
     * Converting device and sensor to the multiplex key.
     * Just useful to put this in a function since we need it
     * in multiple places and we don't want to mess it up due to
     * a typo.
     *
     * @param dev
     * @param sen
     * @return
     */
    String toMultiplexKey(String dev, String sen) {
        return dev + ":" + sen;
    }

    /**
     * Brings all apps to life using their class name
     */
    private void bringAppsToLife() {

        List<App> apps = AppLoader.instance.instantiateApps(this, this);
        for (App appInstance : apps) {
            String classname = appInstance.getClass().getCanonicalName();
            runningApps.put(classname, appInstance);
            lastStateUpdate.put(classname, null);
            lastDataUpdate.put(classname, new HashMap<String, Long>());
        }
    }

    /**
     * Just call the shut down sequence of all apps.
     */
    private void shutdownAllApps() {
        for (App app : runningApps.values())
            app.shutdown();
    }

    /**
     * Receives data. Based on our multiplexing, we may
     * need to feed in the data to the individual apps.
     * Data uploading is handled by a separate service.
     * <p>
     * We also don't do any UI update here. This is purely
     * for multiplexing. There's also a data receiver inside
     * the DCTFragment that's responsible for flashing various
     * colors.
     */
    public void newData(DataMarshal.DataObject dataObject) {
        if (dataObject == null) return;
        if (dataMultiplexing == null) return;
        if (prefs.getInt(
                Constants.Session_State_Key,
                TriggerSession.SessionState.PAUSED.getValue()
        ) == TriggerSession.SessionState.PAUSED.getValue())
            return;

        String multiplexKey = dataObject.device + ":" + dataObject.sensor;
        Intent statusIntent;
        long currTime = System.currentTimeMillis();

        if (dataMultiplexing.containsKey(multiplexKey)) {
            dataObject.uid = Constants.UID;
            dataObject.tripid = Constants.tripid;

            Set<String> classNames = dataMultiplexing.get(multiplexKey);
            for (String appClassName : classNames) {
                App app = runningApps.get(appClassName);
                if (app == null) continue;

                // Throttle the data rate for each sensor
                if (currTime > lastDataUpdate.get(appClassName).get(multiplexKey) + DATA_UPDATE_INTERVAL_IN_MS) {
                    // The app gets new data
                    dataObject.appClassName = appClassName;
                    app.newData(dataObject);
                    clTripWriter.addNewData(appClassName, dataObject);
                    lastDataUpdate.get(appClassName).put(multiplexKey, currTime);
                }

                // Update the notification
                // If the BT service scans in the meantime, it resets the notification to it's own thing
                if (currTime > lastNotificationUpdate + UPDATE_NOTIFICATION_INTERVAL) {
                    NotificationsHelper.setNotificationForeground(this, NotificationsHelper.Notifications.COLLECTING_DATA);
                    lastNotificationUpdate = currTime;
                }

                // Only broadcast state if it's changed since last time
                if ((lastStateUpdate.get(appClassName) != dataObject.dataType)) {
                    statusIntent = new Intent();
                    statusIntent.setAction(Constants.INTENT_APP_STATE_UPDATE);
                    statusIntent.putExtra("appClassName", appClassName);
                    statusIntent.putExtra("appState", dataObject.dataType);
                    CLService.this.sendBroadcast(statusIntent);
                    lastStateUpdate.put(appClassName, dataObject.dataType);
                }
            }
        } else {
            //Log.e(TAG, "CLService got data that no one asked for: " + dataObject.device + ", " + dataObject.sensor);
            // This happens often when the dependency sends out lots of data which no one cares about.
            // No need to flood LogCat with this.
        }

    }

    @Override
    public boolean onUnbind(Intent intent) {
        // All clients have unbound with unbindService()
        //return mAllowRebind;
        Log.e(TAG, "Service unbind: " + startTimestamp);
        return false;
    }

    @Override
    public void onRebind(Intent intent) {
        // A client is binding to the service with bindService(),
        // after onUnbind() has already been called
    }

    @Override
    public void onDestroy() {
        // The service is no longer used and is being destroyed
        Log.e(TAG, "Service on destroy: " + startTimestamp);
    }

    public class LocalBinder extends Binder {
        public CLService getService() {
            // Return this instance of LocalService so clients can call public methods
            return CLService.this;
        }
    }
}
