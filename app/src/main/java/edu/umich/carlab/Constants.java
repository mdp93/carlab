package edu.umich.carlab;

import android.app.Activity;

/**
 * Created by arunganesan on 1/21/18.
 */

public class Constants {
    public static final String INTENT_APP_STATE_UPDATE = "edu.umich.carlab.APP_STATE_UPDATE";
    public static final String BLUETOOTH_CONN_FAILED = "BluetoothConnFailed";

    public static final int TAG_CODE_PERMISSION_LOCATION = 777;

    // TODO This needs to be read from some place else. Maybe from shared preferences? Where is this stored?
    // This was most most likely solved already. Not sure what the solution was though - 10/12/18
    public static String UID = "";
    public static String tripid = "";


    public final static int GPS_INTERVAL = 500;

    // Sample OXC at 10 Hz.
    public final static int OXC_PERIOD = 100;

    /** Upload URL */
    public static final String BASE_URL = "http://barca.eecs.umich.edu:9000/";
    public static final String SigninURL = BASE_URL + "createaccount";
    public static final String TripEndURL = BASE_URL + "endedtrip";
    public static final String DEFAULT_UPLOAD_URL = BASE_URL + "/upload";
    public static final String MEDIA_UPLOAD_URL = BASE_URL + "/uploadMedia";
    public static final String SURVEY_UPLOAD_URL = BASE_URL + "/uploadSurvey";
    public static final String LIST_UPLOADED_URL = BASE_URL + "/uploaded";
    public static final String LATEST_TRIP_URL = BASE_URL + "/latestTrip";

    public static final String GET_LATEST_LOG_URL = BASE_URL + "/clog/latest";
    public static final String UPLOAD_LOG_URL = BASE_URL + "/clog/upload";

    public static final String VERSION_URL = BASE_URL + "/experiment/version";
    public static final int CARLAB_NOTIFICATION_ID = 0x818;
    public static long RemainingDataCount = 0L;


    /* Display parameters */
    public static final int ROW_HEIGHT = 350;
    public static final String Notification_Channel = "carlab_channel_01";

    /* Shared preferences keys */
    public static final String BLUETOOTH_LIST_KEY = "BluetoothListKey";
    public static final String SELECTED_BLUETOOTH_KEY = "Bluetooth Selected Device";
    public static final String AUTOSTART_PREF_KEY = "Autostart pref key";
    public static final String MAX_DATA_STORAGE_KEY = "max storage key";
    public static final String BLACKLIST_APPS_KEY = "blacklist apps keys";
    public static final String STATIC_APPS = "static apps";
    public static String UID_key = "uid";
    public static String Profile_key = "profilePhotoUrl";
    public static String Profile_Image = "profilePhotoData";
    public static final String Privacy_Level_Key = "privacy level key";
    public static final String Privacy_Sensors_Allowed_Key = "privacy sensors allowed key";
    public static final String Privacy_Sensors_Forbidden_Key = "privacy sensors forbidden key";
    public static final String Previous_Custom_Build_Key = "custom privacy settings";
    public static final String Current_App_Task_Mode = "previous app task mode";
    public static final String Set_of_Installed_Apps = "set of installed apps";
    public static final String Main_Activity = "this main activity";

    // Trigger-related shared preferences
    public static final String Last_Activity_Update = "last activity update";
    public static final String Last_Time_In_Vehicle = "last time in vehicle";

    public static final String Session_State_Key = "session state key";
    public static final String Last_Check_Time_Key = "last check time";
    public static final int wakeupCheckPeriod = 30*1000; //10*1000;
    public static final int sleepCheckPeriod = 5*1000; //5*1000;

    public static final String Static_Apps = "static apps";

    public static final String Trip_Id_Offset = "trip id offset";

    // Experiment details
    public static final String Experiment_Id = "experiment id";
    public static final String Experiment_Shortname = "experiment shortname";
    public static final String Experiment_Version_Number = "experiment version";
    public static final String Experiment_New_Version_Detected = "experiment update needed";
    public static final String Experiment_New_Version_Last_Checked = "experiment last checked";


    /** Return value codes **/
    public final static float GENERAL_POLL_ERROR = -1;
    public final static float NO_GPS_PERMISSION_ERROR = -2;
    public final static float GENERAL_ERROR = -3;
    public final static float GPS_STARTED_STATUS = 2;
    public final static float STARTING_STATUS = 3;
    public final static float GENERAL_STATUS = 4;

    public static final String PreviousPageKey = "previousPage";

    /* Intents flying around */
    public static final String MASTER_SWITCH_ON = "MasterSwitchON";
    public static final String MASTER_SWITCH_OFF = "MasterSwitchOFF";
    public static final String DONE_INITIALIZING_CL = "CanEnableMSNow";
    public static final String TRIGGER_BT_SEARCH = "TriggerBTSearch";
    public static final String CLSERVICE_STOPPED = "CLSERVICE_STOPPED";
    public static final String STATUS_CHANGED = "StatusChanged";
    public static final String USER_SUBMITTED_SURVEY = "UserSubmittedSurvey";
    public static final String PAUSE_CL = "CLPaused";
    public static final String RESUME_CL= "CLResumed";
    public static final String BT_FAILED = "bluetooth failed";
    public static final String UPDATE_VERSION = "update version";


    public static final String TASKS_APK_NAME = "tasks.apk";

}
