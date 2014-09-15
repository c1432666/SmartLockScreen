package com.pvsagar.smartlockscreen.services;

import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationClient.OnAddGeofencesResultListener;
import com.google.android.gms.location.LocationClient.OnRemoveGeofencesResultListener;
import com.google.android.gms.location.LocationStatusCodes;
import com.pvsagar.smartlockscreen.applogic.EnvironmentDetector;
import com.pvsagar.smartlockscreen.applogic_objects.BluetoothEnvironmentVariable;
import com.pvsagar.smartlockscreen.applogic_objects.Environment;
import com.pvsagar.smartlockscreen.applogic_objects.LocationEnvironmentVariable;
import com.pvsagar.smartlockscreen.applogic_objects.User;
import com.pvsagar.smartlockscreen.applogic_objects.WiFiEnvironmentVariable;
import com.pvsagar.smartlockscreen.backend_helpers.Utility;
import com.pvsagar.smartlockscreen.baseclasses.Passphrase;
import com.pvsagar.smartlockscreen.environmentdb.EnvironmentDbHelper;
import com.pvsagar.smartlockscreen.frontend_helpers.NotificationHelper;
import com.pvsagar.smartlockscreen.receivers.AdminActions;
import com.pvsagar.smartlockscreen.receivers.BluetoothReceiver;
import com.pvsagar.smartlockscreen.receivers.ScreenReceiver;
import com.pvsagar.smartlockscreen.receivers.WifiReceiver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BaseService extends Service implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener,
        OnAddGeofencesResultListener,
        OnRemoveGeofencesResultListener,
        EnvironmentDetector.EnvironmentDetectedCallback{
    private static final String LOG_TAG = BaseService.class.getSimpleName();

    public static final int ONGOING_NOTIFICATION_ID = 1;
    public static final int GEOFENCE_SERVICE_REQUEST_CODE = 2;

    private static final String PACKAGE_NAME = "com.pvsagar.smartlockscreen.services";
    public static final String ACTION_DETECT_ENVIRONMENT = PACKAGE_NAME + ".DETECT_ENVIRONMENT";
    public static final String ACTION_ADD_GEOFENCES = PACKAGE_NAME + ".ADD_GEOFENCES";
    public static final String ACTION_REMOVE_GEOFENCES = PACKAGE_NAME + ".REMOVE_GEOFENCES";
    public static final String ACTION_DETECT_WIFI = PACKAGE_NAME + ".DETECT_WIFI";

    public static final String EXTRA_GEOFENCE_IDS_TO_REMOVE = PACKAGE_NAME + ".EXTRA_GEOFENCE_IDS_TO_REMOVE";

    private LocationClient mLocationClient;
    // Defines the allowable request types.
    public enum REQUEST_TYPE {ADD_GEOFENCES, REMOVE_GEOFENCES}
    private REQUEST_TYPE mRequestType;
    // Flag that indicates if a request is underway.
    private boolean mInProgress;
    //List of geofence ids to delete when calling removeGeofences
    private List<String> mGeofencesToRemove;

    public static Intent getServiceIntent(Context context, String extraText, String action){
        Intent serviceIntent  = new Intent();
        serviceIntent.setClass(context, com.pvsagar.smartlockscreen.services.BaseService.class);
        if(extraText != null && !extraText.isEmpty()){
            serviceIntent.setData(Uri.parse(extraText));
        }
        serviceIntent.setAction(action);
        return serviceIntent;
    }

    public BaseService() {
    }

    @Override
    public void onCreate() {
        EnvironmentDbHelper.insertDefaultUser(this);
        startForeground(ONGOING_NOTIFICATION_ID, NotificationHelper.getAppNotification(this, null));
        AdminActions.initializeAdminObjects(this);
        mInProgress = false;
        mLocationClient = new LocationClient(this, this, this);
        requestAddGeofences();
        WiFiEnvironmentVariable currentWifi = getConnectedWifiNetwork();
        if(currentWifi != null){
            WifiReceiver.setCurrentWifiNetwork(currentWifi);
        }
        new BluetoothDeviceSearch().execute();
        ScreenReceiver.registerScreenReceiver(this);
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action;
        if(intent != null) {
            Uri uri = intent.getData();

            if (uri != null) {
                Log.d(LOG_TAG, "Uri: " + uri);
                startForeground(ONGOING_NOTIFICATION_ID, NotificationHelper.getAppNotification(this,
                        uri.toString()));
            }
            action = intent.getAction();
            if(action != null && !action.isEmpty()) {
                if (action.equals(ACTION_DETECT_ENVIRONMENT)) {
                    new EnvironmentDetector().detectCurrentEnvironment(this, this);
                } else if(action.equals(ACTION_ADD_GEOFENCES)) {
                    requestAddGeofences();
                } else if(action.equals(ACTION_REMOVE_GEOFENCES)){
                    try {
                        mGeofencesToRemove = (List<String>) intent.
                                getSerializableExtra(EXTRA_GEOFENCE_IDS_TO_REMOVE);
                        if(mGeofencesToRemove == null || mGeofencesToRemove.isEmpty()){
                            throw new IllegalArgumentException();
                        }
                        requestRemoveGeofences();
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new IllegalArgumentException("Intent should specify geofences to remove," +
                                "if action is ACTION_REMOVE_GEOFENCES.");
                    }
                } else if(action.equals(ACTION_DETECT_WIFI)){
                    WifiReceiver.setCurrentWifiNetwork(getConnectedWifiNetwork());
                }
                //Additional action handling to be done here when more actions are added
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConnected(Bundle bundle) {
        switch (mRequestType){
            case ADD_GEOFENCES:
                PendingIntent geofenceIntent = PendingIntent.getService(this,
                        GEOFENCE_SERVICE_REQUEST_CODE, GeoFenceIntentService.getIntent(this), 0);
                List<Geofence> geofenceList = LocationEnvironmentVariable.getAndroidGeofences(this);
                if(geofenceList != null && !geofenceList.isEmpty()) {
                    mLocationClient.addGeofences(geofenceList, geofenceIntent, this);
                } else {
                    mInProgress = false;
                    mLocationClient.disconnect();
                }
                break;

            case REMOVE_GEOFENCES:
                mLocationClient.removeGeofences(mGeofencesToRemove, this);
        }
    }

    @Override
    public void onDisconnected() {

    }

    @Override
    public void onAddGeofencesResult(int i, String[] strings) {
        switch (i){
            case LocationStatusCodes.SUCCESS:
                String geofences = "";
                for (String string : strings) {
                    geofences += string + ",";
                }
                Toast.makeText(this, "Geofences added: " + geofences, Toast.LENGTH_SHORT).show();
                break;
            default:
                Log.e(LOG_TAG, "Error adding Geofences. Status code: " + i);
                Toast.makeText(this, "Error adding geofences.", Toast.LENGTH_SHORT).show();
        }

        mInProgress = false;
        mLocationClient.disconnect();

    }

    @Override
    public void onRemoveGeofencesByRequestIdsResult(int i, String[] strings) {
        switch (i){
            case LocationStatusCodes.SUCCESS:
                String geofences = "";
                for (String string : strings) {
                    geofences += string + ",";
                }
                Toast.makeText(this, "Geofences removed: " + geofences, Toast.LENGTH_SHORT).show();
                break;
            default:
                Log.e(LOG_TAG, "Error removing Geofences. Status code: " + i);
                Toast.makeText(this, "Error removing geofences.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRemoveGeofencesByPendingIntentResult(int i, PendingIntent pendingIntent) {
        return;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    private void requestAddGeofences(){
        mRequestType = REQUEST_TYPE.ADD_GEOFENCES;
        if(!mInProgress){
            mInProgress = true;
            mLocationClient.connect();
        } else {
            Log.w(LOG_TAG, "A request already in progress.");
            startLocationRequestReset();
            requestAddGeofences();
        }
    }

    private void requestRemoveGeofences(){
        mRequestType = REQUEST_TYPE.REMOVE_GEOFENCES;
        if(!mInProgress){
            mInProgress = true;
            mLocationClient.connect();
        } else {
            Log.w(LOG_TAG, "A request already in progress.");
            startLocationRequestReset();
            requestRemoveGeofences();
        }
    }

    private WiFiEnvironmentVariable getConnectedWifiNetwork(){
        WifiManager wifiManager = (WifiManager)this.getSystemService(Context.WIFI_SERVICE);

        if(wifiManager == null){
            Log.e(LOG_TAG,"WiFi Hardware not available");
            return null;
        }
        if(!wifiManager.isWifiEnabled()){
            return null;
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if(wifiInfo != null) {
            List<WifiConfiguration> wifiConfigurations = WiFiEnvironmentVariable.getConfiguredWiFiConnections(this);
            for(WifiConfiguration configuration: wifiConfigurations){
                if(configuration.SSID.equals(wifiInfo.getSSID())) {
                    String wifiEncryptionType = WiFiEnvironmentVariable.getSecurity(configuration);
                    return WiFiEnvironmentVariable.getWifiEnvironmentVariableFromDatabase(
                            this, wifiInfo.getSSID(), wifiEncryptionType);
                }
            }
        }
        return null;
    }

    private class BluetoothDeviceSearch extends AsyncTask<Void, Void, List<BluetoothEnvironmentVariable>>{
        @Override
        protected List<BluetoothEnvironmentVariable> doInBackground(Void... params) {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if(mBluetoothAdapter.isEnabled()){
                Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
                List<BluetoothEnvironmentVariable> connectedDevices = new ArrayList<BluetoothEnvironmentVariable>();
                for(BluetoothDevice device: bondedDevices){
                    try {
                        Method method = device.getClass().getMethod("getUuids"); /// get all services
                        ParcelUuid[] parcelUuids = (ParcelUuid[]) method.invoke(device); /// get all services

                        BluetoothSocket socket = device.createInsecureRfcommSocketToServiceRecord
                                (parcelUuids[0].getUuid()); ///pick one at random

                        socket.connect();
                        socket.close();
                        connectedDevices.add(new BluetoothEnvironmentVariable(device.getName(),
                                device.getAddress()));
                        Log.d(LOG_TAG, device.getName() + " added.");
                    } catch (Exception e) {
                        Log.d("BluetoothPlugin", device.getName() + "Device is not in range");
                    }
                }
                return connectedDevices;
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<BluetoothEnvironmentVariable> variables) {
            if(Utility.checkForNullAndWarn(variables, LOG_TAG))
                return;
            for (BluetoothEnvironmentVariable device : variables) {
                BluetoothReceiver.addBluetoothDeviceToConnectedDevices(device);
            }
            startService(BaseService.getServiceIntent(getBaseContext(), null, ACTION_DETECT_ENVIRONMENT));
            super.onPostExecute(variables);
        }
    }

    private void startLocationRequestReset(){
        mLocationClient.disconnect();
        mInProgress = false;
    }

    @Override
    public void onEnvironmentDetected(Environment current) {
        if (current == null) {
            startForeground(ONGOING_NOTIFICATION_ID, NotificationHelper.getAppNotification(this,
                    "Unknown Environment"));
            String masterPassword = ""; //TODO: change this to master password
            if(!AdminActions.changePassword(masterPassword)){
                AdminActions.initializeAdminObjects(this);
                if(!AdminActions.changePassword(masterPassword)){
                    startService(BaseService.getServiceIntent(this,
                            "Please enable administrator for the app.", null));
                }
            }
        } else {
            User user = User.getCurrentUser(this);
            if(user != null) {
                Passphrase passphrase = user.getPassphraseForEnvironment(this, current);
                if(passphrase != null){
                    if(!passphrase.setAsCurrentPassword()){
                        AdminActions.initializeAdminObjects(this);
                        if(!passphrase.setAsCurrentPassword()){
                            startService(BaseService.getServiceIntent(this,
                                    "Please enable administrator for the app.", null));
                        }
                    }
                } else {
                    Log.w(LOG_TAG, "Passphrase null for current user in current environment.");
                }
            } else {
                Log.e(LOG_TAG, "Current user null!");
            }
            startForeground(ONGOING_NOTIFICATION_ID, NotificationHelper.getAppNotification(this,
                    "Environment: " + current.getName()));
        }
    }
}
