/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package emed.tetra.ble.test;


import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;


import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    public BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    public BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    //added by csh
    AppCompatActivity mActivity;
    private String mConnectorPin;
    private static final int PAIRING_VARIANT_CONSENT = 3;

    private static final int GATT_CONNECTION_TIMEOUT = 0x08;
    private static final int GATT_REMOTE_DEVICE_ERROR = 0x13;
    private static final int GATT_LOCAL_HOST_ERROR = 0x16;
    private static final int GATT_INTERNAL_ERROR = 0x81;
    private static final int GATT_ERROR = 0x85;

    private static final int SERVICES_DISCOVERY_DELAY = 100; // мс

    public void showToast(final String msg)
    {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }


    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to GATT server.");
                    showToast("Connected to GATT server.");
//                    EventBus.getDefault().postSticky(new BleStateChangedEvent(BLE_DEVICE_CONNECTED));
                    if (gatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED) {
                        showToast("Bond State is BONDED.");
                        try { // добавляем задержку для более стабильного поиска сервисов
                            Thread.sleep(SERVICES_DISCOVERY_DELAY);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        intentAction = ACTION_GATT_CONNECTED;
                        mConnectionState = STATE_CONNECTED;
                        broadcastUpdate(intentAction);

                        mBluetoothGatt.discoverServices(); // запускаем поиск необходимых сервисов для обмена данными
                    } else {
                        Log.i(TAG, "Waiting for device pairing...");
                        showToast("Waiting for device pairing...");

                        pairDevice(gatt.getDevice());
                    }

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Device disconnected");
                    closeConnection(false);
                }

            } else if (status == GATT_CONNECTION_TIMEOUT) { // таймаут подключения
                Log.w(TAG, "Remote device not responding");
//                EventBus.getDefault().postSticky(new BleStateChangedEvent(BLE_DEVICE_NOT_RESPONDING));
                showToast("Connection error: remote device not responding");
                closeConnection(true);

            } else if (status == GATT_REMOTE_DEVICE_ERROR) { // удаленное устройство разорвало соединение
                Log.w(TAG, "Remote device terminate the connection");
//                EventBus.getDefault().postSticky(new BleStateChangedEvent(BLE_DEVICE_FAILURE));
                showToast("Communication error: remote device closed connection");
                // сбрасываем сохраненный пин, так как, возможно, он был введен неверно
                PreferencesUtils.setBlePin(gatt.getDevice().getAddress(), null);
                closeConnection(true);

            } else if (status == GATT_LOCAL_HOST_ERROR) { // ошибка на стороне смартфона
                Log.w(TAG, "Local host terminate the connection");
//                EventBus.getDefault().postSticky(new BleStateChangedEvent(BLE_LOCAL_HOST_FAILURE));
                showToast("Connection error: local host terminated connection");
                closeConnection(true);

            } else if (status == GATT_INTERNAL_ERROR || status == GATT_ERROR) { // недокументированные ошибки соединения
                Log.w(TAG, "Remote device not working");
//                EventBus.getDefault().postSticky(new BleStateChangedEvent(BLE_DEVICE_NOT_WORKING));
                showToast("Connection error: communication service not working");

                // запрещаем переподключение к устройству после получения этих ошибок при наличии связи
                if (gatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED)
                    PreferencesUtils.setBleBondSupported(false);
                closeConnection(true);
            }

//            if (newState == BluetoothProfile.STATE_CONNECTED) {
//                intentAction = ACTION_GATT_CONNECTED;
//                mConnectionState = STATE_CONNECTED;
//                broadcastUpdate(intentAction);
//                Log.i(TAG, "Connected to GATT server.");
//                // Attempts to discover services after successful connection.
//                Log.i(TAG, "Attempting to start service discovery:" +
//                        mBluetoothGatt.discoverServices());
//
//            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
//                intentAction = ACTION_GATT_DISCONNECTED;
//                mConnectionState = STATE_DISCONNECTED;
//                Log.i(TAG, "Disconnected from GATT server.");
//                broadcastUpdate(intentAction);
//            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
//                    gatt.requestMtu(247); // устанавливаем размер блока для записи и чтения данных (необходимо для некоторых моделей телефонов)
//                else
                    broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
                showToast("Connection error: communication service not supported");
                closeConnection(true);
            }
        }

        // подтверждение изменения размера блока для записи и чтения данных
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {

            Log.i(TAG, String.format("Device mtu changed, status %d, mtu %d", status, mtu));

            if (status != BluetoothGatt.GATT_SUCCESS) {
//                EventBus.getDefault().postSticky(new BleStateChangedEvent(BLE_DEVICE_NOT_SUPPORTED));
                showToast("Connection error: communication service not supported");
                closeConnection(true);
                return;
            }

            broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED); // настраиваем найденные сервисы
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        closeConnection(false);
        try {
            AppController.getContext().unregisterReceiver(mActionReceiver);
        }catch (Exception e){}
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize(AppCompatActivity activity) {
        mActivity = activity;

        setFilter(AppController.getContext());        //added by csh

        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }


    private final BroadcastReceiver mActionReceiver = new BroadcastReceiver() {

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void onReceive(Context context, Intent intent) {

            Log.i(TAG, String.format("Received system event: action %s", intent.getAction()));
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(intent.getAction())) {
                int var = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);
//                Log.i(TAG, String.format("Using system functionality to pair to device, variant: %d", var));
//                showToast(String.format("Using system functionality to pair to device, variant: %d", var));

                Log.i(TAG, String.format("Using app functionality to pair to device, variant: %d", var));
                showToast(String.format("Using app functionality to pair to device, variant: %d", var));
                if (var == BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION || var == PAIRING_VARIANT_CONSENT) {
                    Log.i(TAG, "Trying to confirm pairing...");
                    showToast("Trying to confirm pairing...");
                    try { // пробуем самостоятельно подтвердить попытку сопряжения
                        device.getClass().getMethod("setPairingConfirmation", boolean.class).invoke(device, true);
                        abortBroadcast();
                    } catch (Exception e) { // при провале системный запрос уйдёт пользователю
                        Log.w(TAG, "Pairing auto-confirmation failed");
                        e.printStackTrace();
//                        showToast("Pairing auto-confirmation failed");
                    }

                } else if (var == BluetoothDevice.PAIRING_VARIANT_PIN) {
                    Log.i(TAG, "Device PIN requested");
                    showToast("Device PIN requested");
                    String pin = PreferencesUtils.getBlePin(device.getAddress());
                    if (pin != null) { // передаем имеющийся пин ...
                        Log.i(TAG, "Saved PIN sent");
                        device.setPin(pin.getBytes());
                    } else { // ... или запрашиваем его у пользователя
                        Log.i(TAG, "No PIN saved, showing enter dialog...");
//                        EventBus.getDefault().postSticky(new BleDevicePairingEvent());
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                PairConnectorDialogFragment.createBuilder(mActivity.getSupportFragmentManager()).show();
                            }
                        });
                    }
                    abortBroadcast(); // системный запрос заменяется запросом от приложения
                }

            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                Log.i(TAG, String.format("Device bond state changed, new state: %d", state));
                showToast(String.format("Device bond state changed, new state: %d", state));
                if (state == BluetoothDevice.BOND_BONDED) {
                    if (mConnectorPin != null) { // сохраняем введённый пин, если разрешил пользователь
                        PreferencesUtils.setBlePin(device.getAddress(), mConnectorPin);
                        mConnectorPin = null;
                        Log.i(TAG, "Device PIN saved");
                    }
                    try { // добавляем задержку для более стабильного поиска сервисов
                        Thread.sleep(SERVICES_DISCOVERY_DELAY);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    String intentAction = ACTION_GATT_CONNECTED;
                    mConnectionState = STATE_CONNECTED;
                    broadcastUpdate(intentAction);

                    mBluetoothGatt.discoverServices(); // запускаем поиск необходимых сервисов для обмена данными
                }
            }
        }
    };

    private void setFilter(Context ctx) {
        IntentFilter filter = new IntentFilter();
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY - 1);
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        ctx.registerReceiver(mActionReceiver, filter);
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }


//        mBluetoothGatt = AppController.mBluetoothGatt;
//        mBluetoothDeviceAddress = PreferencesUtils.getString(this,Constants.DEVICE_MAC, null);
        // Previously connected device.  Try to reconnect.
//        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
//                && mBluetoothGatt != null) {
//            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
//            if (mBluetoothGatt.connect()) {
//                mConnectionState = STATE_CONNECTING;
//                return true;
//            } else {
//                return false;
//            }
//        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            showToast("Device not found.  Unable to connect.");
            return false;
        }

        //added by csh
        int bondState = device.getBondState();
        Log.i(TAG, String.format("Device bond state: %d", bondState));
        showToast(String.format("Device bond state: %d", bondState));

        if (!PreferencesUtils.isBleBondSupported() && bondState != BluetoothDevice.BOND_NONE) {
            unbindDevice(device);
        }

        Log.d(TAG, "Trying to create a new connection. "+address);
        showToast("Trying to create a new connection. "+address);
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
//            mBluetoothGatt = device.connectGatt(this, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
//        else
            mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;

        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {

        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void closeConnection(boolean unbind) {
        if (mBluetoothGatt == null) {
            return;
        }

        try {
            BluetoothDevice device = mBluetoothGatt.getDevice();
            if (!PreferencesUtils.isBleBondSupported() || unbind)
                unbindDevice(device);

            disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
            mBluetoothDeviceAddress = null;
        }catch (Exception e){}

        String intentAction = ACTION_GATT_DISCONNECTED;
        mConnectionState = STATE_DISCONNECTED;
        Log.i(TAG, "Disconnected from GATT server.");
        broadcastUpdate(intentAction);
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, String str) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        characteristic.setValue(str.getBytes());
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void setPin(String pin, boolean remember) {
        Log.i(TAG, "Device PIN entered");
        if (mBluetoothGatt != null) {
            mBluetoothGatt.getDevice().setPin(pin.getBytes());
            mConnectorPin = remember ? pin : null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void pairDevice(BluetoothDevice device) {
        showToast("Start Pairing... with: " + device.getName());
        try {
            Log.d(TAG, "Start Pairing... with: " + device.getName());
            device.createBond();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public void pairDevice1(BluetoothDevice device) {
        try {
            Method method = device.getClass().getMethod("createBond", (Class[]) null);
            method.invoke(device, (Object[]) null);
            //From API 19.
            //  device.createBond();
        } catch (Exception e) {
            e.printStackTrace();
        }
        showToast("Device bond created");
    }

    // разрыв связи с беспроводным устройством
    private void unbindDevice(BluetoothDevice device) {
        Log.i(TAG, "Removing device bond...");
        try { // используется недокументированный метод
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.i(TAG, "Device bond removed");
        showToast("Device bond removed");
    }

}
