package emed.tetra.ble.test;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class DiscoveryFragmentGatt extends Fragment {

    private static final String TAG = DiscoveryFragmentGatt.class.getSimpleName();
    View view;

    private ListView listView;
    ProgressBar progressBar;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;


    private boolean pendingRequestEnableBt = false;
    public BroadcastReceiver bReceiver;

    public boolean needFirstConnect = false;

    @RequiresApi(api = Build.VERSION_CODES.M)
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_home, container, false);

        mHandler = new Handler();
        listView = view.findViewById(R.id.list_device);
        progressBar = view.findViewById(R.id.progressBar);
        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        listView.setAdapter(mLeDeviceListAdapter);

        checkBluetoothAvailability();

        requestEnableBluetooth();

        try {
            // Make sure we have access coarse location enabled, if not, prompt the user to enable it
            if (getActivity().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
            } else {

            }
        }catch (Exception e){}

        String mBluetoothDeviceAddress = PreferencesUtils.getString(getActivity(), Constants.DEVICE_MAC, null);
        if(mBluetoothDeviceAddress != null)     needFirstConnect = true;

        Intent gattServiceIntent = new Intent(getActivity(), BluetoothLeService.class);
        getActivity().bindService(gattServiceIntent, mServiceConnection, getActivity().BIND_AUTO_CREATE);
        return view;
    }

    // Code to manage Service lifecycle.
    public final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            AppController.mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!AppController.mBluetoothLeService.initialize((MainActivity)getActivity())) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                Toast.makeText(getActivity(), "Unable to initialize Bluetooth", Toast.LENGTH_SHORT).show();
                //finish();
                return;
            }
            performBleCheck();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            AppController.mBluetoothLeService = null;
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    System.out.println("coarse location permission granted");
                } else {
                }
                return;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!checkBluetoothAvailability()) return;
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

//        scanLeDevice(true);
        getActivity().registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!checkBluetoothAvailability()) return;
//        scanLeDevice(false);
//        mLeDeviceListAdapter.clear();
        getActivity().unregisterReceiver(mGattUpdateReceiver);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if(requestCode ==  REQUEST_ENABLE_BT) pendingRequestEnableBt = false;
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public boolean checkBluetoothAvailability() {
        boolean result = false;
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
//            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            return result;
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
 //           Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
             return result;
        }

        return !result;
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<Device> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<Device>();
            mInflator = getLayoutInflater();
        }

        public void addDevice(Device device) {
            if (device.bleDevice.getName() != null && device.bleDevice.getName().length() > 0)
            {
            } else return;

            for(Device db:mLeDevices) {
                if(device.bleDevice.getAddress().equals(db.bleDevice.getAddress())) return;
            }
             mLeDevices.add(device);
        }

        public Device getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.device_item, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceName = (TextView) view.findViewById(R.id.tvName);
                viewHolder.btnPair = view.findViewById(R.id.btnPair);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            final Device device = mLeDevices.get(i);
            String deviceName = device.bleDevice.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName+"\n"+device.bleDevice.getAddress());
            else
                viewHolder.deviceName.setText(getResources().getString(R.string.unknown_device)+"\n"+device.bleDevice.getAddress());

            if(device.connectStatus == true) {
                viewHolder.btnPair.setText(getResources().getString(R.string.unpair));
                viewHolder.btnPair.setBackgroundColor(getResources().getColor(R.color.btn_unpair));
            } else {
                viewHolder.btnPair.setText(getResources().getString(R.string.pair));
                viewHolder.btnPair.setBackgroundColor(getResources().getColor(R.color.btn_pair));
            }

            viewHolder.btnPair.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    if (device == null) return;
//                    if (mScanning) {
//                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
//                        mScanning = false;
//                        progressBar.setVisibility(View.GONE);
//                    }

                    progressBar.setVisibility(View.VISIBLE);

                    if(device.connectStatus == true) {
                        PreferencesUtils.putString(getActivity(),Constants.DEVICE_MAC,null);
//                        AppController.mBluetoothLeService.disconnect();
                        AppController.mBluetoothLeService.closeConnection(false);
                    } else {
                        PreferencesUtils.putString(getActivity(),Constants.DEVICE_MAC,device.bleDevice.getAddress());
                        AppController.mBluetoothLeService.connect(device.bleDevice.getAddress());
                    }
                }
            });
            return view;
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            //Log.d(TAG, " Discovered Device [Name:  " + device.getName() + ", MAC Address: " + device.getAddress());
                            Device deviceModel = new Device();
                            deviceModel.bleDevice = device;
                            String mBluetoothDeviceAddress = PreferencesUtils.getString(getActivity(),Constants.DEVICE_MAC, null);
                            if(mBluetoothDeviceAddress!=null && device.getAddress().equals(mBluetoothDeviceAddress)) {
                                deviceModel.connectStatus = true;
                            }
                            mLeDeviceListAdapter.addDevice(deviceModel);
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            };

    static class ViewHolder {
        TextView deviceName;
//        TextView deviceAddress;
        Button btnPair;
    }


    private void updateConnectionState(final int resourceId) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                String mBluetoothDeviceAddress = PreferencesUtils.getString(getActivity(),Constants.DEVICE_MAC, null);
                Log.d(TAG, "connected device address:"+mBluetoothDeviceAddress);
                for(int i=0;i<mLeDeviceListAdapter.getCount();i++) {
                    Log.d(TAG, "device:"+i+" address:"+mLeDeviceListAdapter.getDevice(i).bleDevice.getAddress());
                    if(mBluetoothDeviceAddress!=null && mLeDeviceListAdapter.getDevice(i).bleDevice.getAddress().equals(mBluetoothDeviceAddress)) {
                        mLeDeviceListAdapter.getDevice(i).connectStatus = true;
                        Log.d(TAG, "connected device:"+i+" address:"+mBluetoothDeviceAddress);
                    } else mLeDeviceListAdapter.getDevice(i).connectStatus = false;
                }
                mLeDeviceListAdapter.notifyDataSetChanged();

                progressBar.setVisibility(View.GONE);
                if(resourceId == 0){
                    Toast.makeText(getActivity(),"Device Paired Successfully",Toast.LENGTH_SHORT).show();
//                    Intent i = new Intent(getActivity(), ButtonsActivity.class);
//                    getActivity().startActivity(i);
                } else {
                    Toast.makeText(getActivity(),getResources().getString(R.string.device_unpaired_successfully),Toast.LENGTH_SHORT).show();
                }

                //ivConnectedStatus.setBackgroundColor(getResources().getColor(resourceId));
                //mConnectionState.setText(resourceId);
            }
        });
    }

    public final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                AppController.connected = true;
                updateConnectionState(0);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                AppController.connected = false;
                updateConnectionState(1);
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
               // displayGattServices(AppController.mBluetoothLeService.getSupportedGattServices());
                progressBar.setVisibility(View.GONE);
                AppController.characteristic = null;
                List<BluetoothGattService> gattServices = AppController.mBluetoothLeService.getSupportedGattServices();
                if (gattServices == null) return;
                for (BluetoothGattService gattService : gattServices) {
                    List<BluetoothGattCharacteristic> gattCharacteristics =
                            gattService.getCharacteristics();
                    for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {

                        final int charaProp = gattCharacteristic.getProperties();
                        if (((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) |
                                (charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) {
                            AppController.characteristic = gattCharacteristic;
                            try {
//                                Toast.makeText(getActivity(), "UUID:"+gattCharacteristic.getUuid().toString(), Toast.LENGTH_SHORT).show();
//                                AppController.characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
//                                AppController.mBluetoothLeService.writeCharacteristic(AppController.characteristic, "C052101302");
                            }catch (Exception e){}
                        }
                    }
//                    if(getBluetoothAdapter().isDiscovering())
//                        getBluetoothAdapter().cancelDiscovery();
//                    getActivity().runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            Intent i = new Intent(getActivity(), ButtonsActivity.class);
//                            getActivity().startActivity(i);
//                        }
//                    });
                }

            }
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }


    private void requestEnableBluetooth() {

        if (BluetoothAdapter.getDefaultAdapter()!=null && !isBluetoothAdapterDiscovering() && !pendingRequestEnableBt) {
            pendingRequestEnableBt = true;

            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivityForResult(discoverableIntent, REQUEST_ENABLE_BT);
        }
    }

    private boolean isBluetoothAdapterDiscovering() {
        return getBluetoothAdapter().isDiscovering();
    }

    private BluetoothAdapter getBluetoothAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }

    public void performBleCheck() {
        if (!checkBluetoothAvailability()) {
            Toast.makeText(getActivity(), "Please turn bluetooth on. Bluetooth is not available", Toast.LENGTH_SHORT).show();
            return;
        }
        mLeDeviceListAdapter.clear();
        mLeDeviceListAdapter.notifyDataSetChanged();

        //if there is already connected device,
        if(AppController.connected) {
            Device device = new Device();
            String mBluetoothDeviceAddress = PreferencesUtils.getString(getActivity(), Constants.DEVICE_MAC, null);
            device.bleDevice= mBluetoothAdapter.getRemoteDevice(mBluetoothDeviceAddress);
            try {
                if(needFirstConnect) {
                    // Automatically connects to the device upon successful start-up initialization.
//                                String mBluetoothDeviceAddress = PreferencesUtils.getString(getActivity(), Constants.DEVICE_MAC, null);
                    Log.e("MainActivity", "Address:" + mBluetoothDeviceAddress);
                    if (mBluetoothDeviceAddress != null && AppController.mBluetoothLeService != null) {
//                                progressBar.setVisibility(View.VISIBLE);
                        AppController.mBluetoothLeService.connect(mBluetoothDeviceAddress);
                        needFirstConnect = false;
                        Log.e("MainActivity", "first connect(service connected):" + mBluetoothDeviceAddress);
                    }
                } else device.connectStatus = true;
            }catch (Exception e){ }

            mLeDeviceListAdapter.addDevice(device);
            mLeDeviceListAdapter.notifyDataSetChanged();
        }


        bReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    Device deviceModel = new Device();
                    deviceModel.bleDevice = device;
                    String mBluetoothDeviceAddress = PreferencesUtils.getString(getActivity(),Constants.DEVICE_MAC, null);
                    if(mBluetoothDeviceAddress!=null && device.getAddress().equals(mBluetoothDeviceAddress)) {
                        try {
                            if(needFirstConnect) {
                                // Automatically connects to the device upon successful start-up initialization.
//                                String mBluetoothDeviceAddress = PreferencesUtils.getString(getActivity(), Constants.DEVICE_MAC, null);
                                Log.e("MainActivity", "Address:" + mBluetoothDeviceAddress);
                                if (mBluetoothDeviceAddress != null && AppController.mBluetoothLeService != null) {
//                                progressBar.setVisibility(View.VISIBLE);
                                    AppController.mBluetoothLeService.connect(mBluetoothDeviceAddress);
                                    needFirstConnect = false;
                                    Log.e("MainActivity", "first connect(service connected):" + mBluetoothDeviceAddress);
                                }
                            } else {
                                deviceModel.connectStatus = true;
                            }
                        }catch (Exception e){ }
                    }

                    Log.d(TAG, " Discovered Device [Name:  " + device.getName() + ", MAC Address: " + device.getAddress());
                    progressBar.setVisibility(View.GONE);
                    mLeDeviceListAdapter.addDevice(deviceModel);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                } else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction()))
                {
                    progressBar.setVisibility(View.GONE);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        getActivity().registerReceiver(bReceiver, intentFilter);


        Log.d(TAG, "Looking up (Discovering) for more bluetooth devices.. ");
        if(getBluetoothAdapter().isDiscovering())
            getBluetoothAdapter().cancelDiscovery();

        /** Gearing up to discover for more search-able bluetooth devices **/
        getBluetoothAdapter().startDiscovery();

        progressBar.setVisibility(View.VISIBLE);
    }
}
