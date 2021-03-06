package com.pvsagar.smartlockscreen.receivers;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.pvsagar.smartlockscreen.applogic_objects.environment_variables.BluetoothEnvironmentVariable;
import com.pvsagar.smartlockscreen.services.BaseService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by aravind on 5/9/14.
 * Receives an intent whenever a bluetooth device is connected/disconnected, and takes the
 * required actions
 */
public class BluetoothReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = BluetoothReceiver.class.getSimpleName();

    private static List<BluetoothEnvironmentVariable> currentlyConnectedBluetoothDevices =
            new ArrayList<BluetoothEnvironmentVariable>();

    @Override
    public void onReceive(Context context, Intent intent) {
        String mAction = intent.getAction();
        BluetoothDevice device;
        switch (mAction) {
            case BluetoothDevice.ACTION_ACL_CONNECTED:
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                addBluetoothDeviceToConnectedDevices(/*BluetoothEnvironmentVariable.
                    getBluetoothEnvironmentVariableFromDatabase(context,*/ new BluetoothEnvironmentVariable(device.getName(),
                        device.getAddress()));
                break;
            case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                removeBluetoothDeviceFromConnectedDevices(/*BluetoothEnvironmentVariable.
                    getBluetoothEnvironmentVariableFromDatabase(context,*/ new BluetoothEnvironmentVariable(device.getName(),
                        device.getAddress()));
                break;
            default:
                return;
        }
        context.startService(BaseService.getServiceIntent(context, null,
                BaseService.ACTION_DETECT_ENVIRONMENT));
    }

    public static void addBluetoothDeviceToConnectedDevices(BluetoothEnvironmentVariable newVariable){
        if(newVariable == null){
            Log.w(LOG_TAG, "Null pointer passed to addBluetoothDeviceToConnectedDevices.");
            return;
        }
        for(BluetoothEnvironmentVariable variable:currentlyConnectedBluetoothDevices){
            if(newVariable.equals(variable))
                return;
        }
        Log.d(LOG_TAG, "Device connected: " + newVariable.getDeviceName());
        currentlyConnectedBluetoothDevices.add(newVariable);
    }

    public static void removeBluetoothDeviceFromConnectedDevices(BluetoothEnvironmentVariable variable){
        if (variable == null){
            Log.w(LOG_TAG, "Null pointer passed to addBluetoothDeviceToConnectedDevices.");
            return;
        }
        for(int i=0; i<currentlyConnectedBluetoothDevices.size(); i++){
            BluetoothEnvironmentVariable v = currentlyConnectedBluetoothDevices.get(i);
            if(variable.equals(v)) {
                Log.d(LOG_TAG, "Device disconnected: " + variable.getDeviceName());
                currentlyConnectedBluetoothDevices.remove(i);
            }
        }
    }

    public static List<BluetoothEnvironmentVariable> getCurrentlyConnectedBluetoothDevices(){
        return currentlyConnectedBluetoothDevices;
    }
}
