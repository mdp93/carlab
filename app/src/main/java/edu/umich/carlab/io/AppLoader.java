package edu.umich.carlab.io;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import edu.umich.carlab.CLDataProvider;
import edu.umich.carlab.Constants;
import edu.umich.carlab.loadable.App;
import edu.umich.carlab.loadable.IApp;
import edu.umich.carlab.clog.CLog;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * Created by arunganesan on 3/20/18.
 */

public class AppLoader {
    final static String TAG = "AppLoader";

    public static final AppLoader instance = new AppLoader();
    private List<Class<?>> loadedApps = new ArrayList<>();

    private AppLoader() {}

    public void loadApp(Class<?> cls) {
        loadedApps.add(cls);
    }

    public List<App> instantiateApps(CLDataProvider clDataProvider, Context context) {
        List<App> instantiatedApps = new ArrayList<>();

        for (Class<?> app : loadedApps) {
            try {
                Constructor<?> constructor = app.getConstructor(CLDataProvider.class, Context.class);
                App appInstance = (App) constructor.newInstance(clDataProvider, context);
                instantiatedApps.add(appInstance);
            } catch (Exception e) {
                Log.e(TAG, "Error creating alive app: " + e);
            }
        }

        return instantiatedApps;
    }
}
