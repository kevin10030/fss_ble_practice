package emed.tetra.ble.test;

import android.app.Application;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;

public class AppController extends Application {

    public static Context mContext;
    private static AppController mInstance;

    public static BluetoothGattCharacteristic characteristic = null;
    public static BluetoothGatt mBluetoothGatt = null;
    public static BluetoothLeService mBluetoothLeService = null;
    public static boolean connected = false;

    @Override
    public void onCreate() {
        mContext = getApplicationContext();
        super.onCreate();
        mInstance = this;
    }

    public static synchronized AppController getInstance() {
        return mInstance;
    }

    public static Context getContext(){
        return mContext;
    }
}
