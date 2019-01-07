package edu.umich.carlab.io;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import dalvik.system.DexClassLoader;
import edu.umich.carlab.CLDataProvider;
import edu.umich.carlab.Constants;
import edu.umich.carlab.apps.App;
import edu.umich.carlab.apps.IApp;
import edu.umich.carlab.clog.CLog;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by arunganesan on 3/20/18.
 */

public class TaskLoader {
    final static String TAG = "TaskLoader";

    public static List<IApp> createCorpseApps(Context context) {
        List<Class<?>> classes = loadApk(context);
        List<IApp> apps = new ArrayList<>();

        CLDataProvider clProvider = (CLDataProvider)null;

        for (Class<?> cls : classes) {
            try {
                Log.v("loadDexClasses", "Class loaded " + cls.getName());
                Constructor<?> constructor = cls.getConstructor(CLDataProvider.class, Context.class);
                IApp appInstance = (IApp)constructor.newInstance(clProvider, context);
                apps.add(appInstance);
            } catch (Exception e) {
            }
        }

        return apps;
    }


    /**
     * Goes through the list of apps in the APK and looks for this classname
     * @param classname
     * @param context
     * @return
     */
    public static App createCorpseApp(String classname, Context context) {
        List<Class<?>> classes = loadApk(context);
        CLDataProvider clDataProvider = (CLDataProvider)null;
        for (Class<?> cls : classes) {
            if (cls.getName().equals(classname)) {
                try {
                    Constructor<?> constructor = cls.getConstructor(CLDataProvider.class, Context.class);
                    App appInstance = (App) constructor.newInstance(clDataProvider, context);
                    return appInstance;
                } catch (Exception e) {
                }

                break;
            }
        }
        return null;
    }


    /**
     * Goes through the list of apps in the APK and looks for this classname
     * @param classname
     * @param clDataProvider
     * @param context
     * @return
     */
    public static App createAliveApp(String classname, CLDataProvider clDataProvider, Context context) {
        List<Class<?>> classes = loadApk(context);
        for (Class<?> cls : classes) {
            if (cls.getName().equals(classname)) {
                try {
                    Constructor<?> constructor = cls.getConstructor(CLDataProvider.class, Context.class);
                    App appInstance = (App) constructor.newInstance(clDataProvider, context);
                    return appInstance;
                } catch (Exception e) {
                    Log.e(TAG, "Error creating alive app: " + e);
                }

                break;
            }
        }
        return null;
    }

    private static List<Class<?>> loadApk(Context context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Log.v("loadDexClassses", "LoadDexClasses is only available for ICS or up");
        }
        List<Class<?>> classes = new ArrayList<>();

        File AppsDir = CLTripWriter.GetAppsDir(context);
        File appfile = new File(AppsDir, Constants.TASKS_APK_NAME);
        if (!appfile.exists()) {
            //sensorsList.setText("App file not found. Please download first");
            //return null;
        } else {
            try {
                final File tmpDir = new File(AppsDir, "optdexjars/");
                tmpDir.mkdir();


                final DexClassLoader classloader = new DexClassLoader(
                        appfile.getAbsolutePath(), tmpDir.getAbsolutePath(),
                        "data/local/tmp/natives/",
                        context.getClassLoader());


                Log.v("loadDexClasses", "Searching for class : "
                        + "bootstrap.Registry");

                Class<?> classToLoad = (Class<?>) classloader.loadClass("bootstrap.Registry");

                Field classesField = classToLoad.getDeclaredField("_classes");
                classes = (ArrayList<Class<?>>) classesField.get(null);

            } catch (Exception e) {
                Log.e(TAG, "Error loading class: " + e.getLocalizedMessage());
            }
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> staticClasses = prefs.getStringSet(Constants.Static_Apps, null);
        if (staticClasses != null) {
            for (String className : staticClasses) {
                try {
                    Class<?> clazz = Class.forName(className);
                    classes.add(clazz);
                } catch (Exception e) {
                    CLog.e(TAG, "Couldn't load class: " + className);
                }
            }
        }


        return classes;
    }
}
