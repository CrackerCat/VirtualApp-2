package com.lody.virtual.client.ipc;

import android.app.PendingIntent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.lody.virtual.client.VClientImpl;
import com.lody.virtual.client.hook.proxies.location.GpsStatusGenerate;
import com.lody.virtual.client.hook.proxies.location.MockLocationHelper;
import com.lody.virtual.client.hook.utils.MethodParameterUtils;
import com.lody.virtual.helper.utils.Reflect;
import com.lody.virtual.os.VUserHandle;
import com.lody.virtual.remote.vloc.VLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @see android.location.LocationManager
 * <p>
 * 实现代码多，资源回收不及时：拦截gps状态，定位请求，并且交给虚拟定位服务，虚拟服务根据一样的条件，再次向系统定位服务请求
 * LocationManager.addgpslistener
 * LocationManager.request
 * <p>
 * 实现代码少：GpsStatusListenerTransport、ListenerTransport这2个对象，hook里面的方法，修改参数，都是binder
 */
public class VLocationManager {
    private static final boolean DEBUG = false;
    private Handler mWorkHandler;
    private HandlerThread mHandlerThread;
    private final List<Object> mGpsListeners = new ArrayList<>();
    private LocationManager mLocationManager;
    private static VLocationManager sVLocationManager = new VLocationManager();

    private VLocationManager() {

    }

    public static VLocationManager get() {
        return sVLocationManager;
    }

    public void setLocationManager(LocationManager locationManager) {
        mLocationManager = locationManager;
        GpsStatusGenerate.fakeGpsStatus(locationManager);
    }

    private void checkWork() {
        if (mHandlerThread == null) {
            synchronized (this) {
                if (mHandlerThread == null) {
                    mHandlerThread = new HandlerThread("loc_thread");
                    mHandlerThread.start();
                }
            }
        }
        if (mWorkHandler == null) {
            synchronized (this) {
                if (mWorkHandler == null) {
                    mWorkHandler = new Handler(mHandlerThread.getLooper());
                }
            }
        }
    }

    private void stopGpsTask() {
        if (mWorkHandler != null) {
            mWorkHandler.removeCallbacks(mUpdateGpsStatusTask);
        }
    }

    private void startGpsTask() {
        checkWork();
        stopGpsTask();
        mWorkHandler.postDelayed(mUpdateGpsStatusTask, 5000);
    }

    private Runnable mUpdateGpsStatusTask = new Runnable() {
        @Override
        public void run() {
            synchronized (mGpsListeners) {
                for (Object listener : mGpsListeners) {
                    notifyGpsStatus(listener);
                }
            }
            mWorkHandler.postDelayed(mUpdateGpsStatusTask, 8000);
        }
    };


    public boolean hasVirtualLocation(String packageName, int userId) {
        try {
            return VirtualLocationManager.get().getMode(userId, packageName) != VirtualLocationManager.MODE_CLOSE;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean isProviderEnabled(String provider) {
        return LocationManager.GPS_PROVIDER.equals(provider);
    }

    public VLocation getLocation(String packageName, int userId) {
        return getVirtualLocation(packageName, null, userId);
    }

    public VLocation getCurAppLocation() {
        return getVirtualLocation(VClientImpl.get().getCurrentPackage(), null, VUserHandle.myUserId());
    }

    public VLocation getVirtualLocation(String packageName, Location loc, int userId) {
        try {
            if (VirtualLocationManager.get().getMode(userId, packageName) == VirtualLocationManager.MODE_USE_GLOBAL) {
                return VirtualLocationManager.get().getGlobalLocation();
            } else {
                return VirtualLocationManager.get().getLocation(userId, packageName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getPackageName() {
        return VClientImpl.get().getCurrentPackage();
    }

    public void removeGpsStatusListener(final Object[] args) {
        if (args[0] instanceof PendingIntent) {
            return;
        }
        boolean needStop;
        synchronized (mGpsListeners) {
            mGpsListeners.remove(args[0]);
            needStop = mGpsListeners.size() == 0;
        }
        if (needStop) {
            stopGpsTask();
        }
    }


    public void addGpsStatusListener(final Object[] args) {
        final Object GpsStatusListenerTransport = args[0];
        GpsStatusGenerate.fakeGpsStatus(GpsStatusListenerTransport);
        if (GpsStatusListenerTransport != null) {
            synchronized (mGpsListeners) {
                mGpsListeners.add(GpsStatusListenerTransport);
            }
        }
        checkWork();
        notifyGpsStatus(GpsStatusListenerTransport);
        startGpsTask();
    }

    private void notifyGpsStatus(final Object transport) {
        if (transport == null) {
            return;
        }
//        checkWork();
        mWorkHandler.post(new Runnable() {
            @Override
            public void run() {
//                GpsStatusGenerate.fakeGpsStatus(transport);
                MockLocationHelper.invokeSvStatusChanged(transport);
                MockLocationHelper.invokeNmeaReceived(transport);
            }
        });
    }

    public void removeUpdates(final Object[] args) {
        if (args[0] != null) {
            UpdateLocationTask task = getTask(args[0]);
            if (task != null) {
                task.stop();
            }
        }
    }

    public void requestLocationUpdates(Object[] args) {
        if (DEBUG) {
            Log.i("tmap", "requestLocationUpdates:start");
        }
        //15-16 last
        final int index;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            index = 1;
        } else {
            index = args.length - 1;
        }
        final Object listenerTransport = args[index];
        if (listenerTransport == null) {
            if (DEBUG) {
                Log.e("tmap", "ListenerTransport:null");
            }
        } else {
            //mInterval
            long mInterval;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                try {
                    mInterval = Reflect.on(args[0]).get("mInterval");
                } catch (Throwable e) {
                    mInterval = 60 * 1000;
                }
            } else {
                mInterval = MethodParameterUtils.getFirstParam(args, Long.class);
            }
            VLocation location = getCurAppLocation();
            checkWork();
            notifyLocation(listenerTransport, location.toSysLocation(), true);
            UpdateLocationTask task = getTask(listenerTransport);
            if (task == null) {
                synchronized (mLocationTaskMap) {
                    task = new UpdateLocationTask(listenerTransport, mInterval);
                    mLocationTaskMap.put(listenerTransport, task);
                }
            }
            task.start();
        }
    }

    private void notifyLocation(final Object ListenerTransport, final Location location, boolean post) {
        if (ListenerTransport == null) {
            return;
        }
        if (!post) {
            try {
                mirror.android.location.LocationManager.ListenerTransport.onLocationChanged.call(ListenerTransport, location);
            } catch (Throwable e) {
                Log.e("location_vmap", "notify loc " + ListenerTransport.getClass().getName(), e);
            }
            return;
        }
        mWorkHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mirror.android.location.LocationManager.ListenerTransport.onLocationChanged.call(ListenerTransport, location);
                } catch (Throwable e) {
                    Log.e("location_vmap", "notify loc " + ListenerTransport.getClass().getName(), e);
                }
            }
        });
    }

    private final Map<Object, UpdateLocationTask> mLocationTaskMap = new HashMap<>();

    private UpdateLocationTask getTask(Object locationListener) {
        UpdateLocationTask task;
        synchronized (mLocationTaskMap) {
            task = mLocationTaskMap.get(locationListener);
        }
        return task;
    }

    private class UpdateLocationTask implements Runnable {
        private Object mListenerTransport;
        private long mTime;
        private volatile boolean mRunning;

        private UpdateLocationTask(Object ListenerTransport, long time) {
            mListenerTransport = ListenerTransport;
            mTime = time;
        }

        @Override
        public void run() {
            if (mRunning) {
                VLocation location = getCurAppLocation();
                if (location != null) {
                    notifyLocation(mListenerTransport, location.toSysLocation(), false);
                    start();
                }
            }
        }

        public void start() {
            mRunning = true;
            mWorkHandler.removeCallbacks(this);
            if (mTime > 0) {
                mWorkHandler.postDelayed(this, mTime);
            } else {
                mWorkHandler.post(this);
            }
        }

        public void stop() {
            mRunning = false;
            mWorkHandler.removeCallbacks(this);
        }
    }
}