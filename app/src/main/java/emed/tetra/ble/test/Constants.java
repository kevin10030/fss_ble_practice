package emed.tetra.ble.test;


import android.os.ParcelUuid;

public class Constants {
    public final static String DEVICE_NAME = "deviceName";
    public final static String DEVICE_UUID = "deviceUUID";
    public final static String DEVICE_MAC = "deviceMAC";


    public final static String BLE_BOND_SUPPORTED = "ble_bond_supported";
    public final static String PREF_BLE_DEVICE_PIN = "BleDevicePinPreference";

    /**
     * UUID identified with this app - set as Service UUID for BLE Advertisements.
     *
     * Bluetooth requires a certain format for UUIDs associated with Services.
     * The official specification can be found here:
     * {@link https://www.bluetooth.org/en-us/specification/assigned-numbers/service-discovery}
     */
    public static final ParcelUuid Service_UUID = ParcelUuid
            .fromString("0000b81d-0000-1000-8000-00805f9b34fb");
}
