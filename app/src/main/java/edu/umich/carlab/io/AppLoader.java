package edu.umich.carlab.io;

import android.content.Context;
import android.util.Log;
import dalvik.system.DexClassLoader;
import edu.umich.carlab.CLDataProvider;
import edu.umich.carlab.apps.App;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by arunganesan on 3/20/18.
 */

public class AppLoader {
    final static String TAG = "AppLoader";


    private static App mortalityAgnosticWakening (String apkname, Context context, CLDataProvider provider) {
        List<Class<?>> classes = loadApk(context, apkname);
        if (classes.isEmpty()) return null;
        Class<?> cls = classes.get(0);

        try {
            Constructor<?> constructor = cls.getConstructor(CLDataProvider.class, Context.class);
            App appInstance = (App) constructor.newInstance(provider, context);
            return appInstance;
        } catch (Exception e) {
        }

        return null;
    }


    /**
     * Goes through the list of apps in the APK and looks for this classname
     * @param apkname
     * @param context
     * @return
     */
    public static App createCorpseApp(String apkname, Context context) {
        return mortalityAgnosticWakening(apkname, context, null);
    }


    /**
     * Goes through the list of apps in the APK and looks for this classname
     * @param apkname
     * @param clDataProvider
     * @param context
     * @return
     */
    public static App createAliveApp(String apkname, CLDataProvider clDataProvider, Context context) {
        return mortalityAgnosticWakening(apkname, context, clDataProvider);
    }

    private static List<Class<?>> loadApk(Context context, String apkname) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Log.v("loadDexClassses", "LoadDexClasses is only available for ICS or up");
        }
        List<Class<?>> classes = new ArrayList<>();
        File AppsDir = CLTripWriter.GetAppsDir(context);

        // XXX: Make sure this doesn't lead to arbitrary access to loading APKs within CarLab
        File [] files = AppsDir.listFiles();
        File appfile = new File(AppsDir, apkname);


        boolean file_not_found = false;
        if (!appfile.exists()) {
            //sensorsList.setText("App file not found. Please download first");
            //return null;

            // Try with the APK extension
            if (!apkname.contains(".apk")) {
                apkname = apkname + ".apk";
                appfile = new File(AppsDir, apkname);
                if (!appfile.exists()) {
                    Log.e(TAG, "App file " + apkname + " not found");
                    file_not_found = true;
                } else file_not_found = false;
            } else file_not_found = true;
        }

        if (!file_not_found) {
            try {
                final File tmpDir = new File(AppsDir,"optdexjars/");
                tmpDir.mkdir();


                final DexClassLoader classloader = new DexClassLoader(
                        appfile.getAbsolutePath(), tmpDir.getAbsolutePath(),
                        "data/local/tmp/natives/",
                        context.getClassLoader());


                Log.v("loadDexClasses", "Searching for class : "
                        + "com.registry.myapplication.Registry");

                Class<?> classToLoad = (Class<?>) classloader.loadClass("bootstrap.Registry");

                Field classesField = classToLoad.getDeclaredField("_classes");
                classes = (ArrayList<Class<?>>) classesField.get(null);

            } catch (Exception e) {
                Log.e("tag", "Exception: " + e.getMessage());
            }
        }

        return classes;
    }
}
