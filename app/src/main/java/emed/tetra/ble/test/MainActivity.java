package emed.tetra.ble.test;


import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;


public class MainActivity extends AppCompatActivity {

    private final static String TAG = MainActivity.class.getSimpleName();

    DiscoveryFragmentGatt discoveryFragment;
    FragmentManager fragmentManager;

    private Handler mHandler;
    private Runnable timeoutRunnable;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    public void init() {
        fragmentManager = getSupportFragmentManager();
        discoveryFragment= new DiscoveryFragmentGatt();
        gotoFragment();

    }



    public void gotoFragment()
    {
        fragmentManager.beginTransaction().replace(R.id.frameContainer,discoveryFragment).commit();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void onClick(View v)
    {
        switch (v.getId())
        {
            case R.id.txt_scan_stop:
                discoveryFragment.performBleCheck();
                break;
            case R.id.txt_buttons:
//                Intent i = new Intent(this, ButtonsActivity.class);
//                startActivity(i);
                PairConnectorDialogFragment.createBuilder(getSupportFragmentManager()).show();

                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if(discoveryFragment.bReceiver != null) unregisterReceiver(discoveryFragment.bReceiver);
        }catch (Exception e){}

        try {
            AppController.connected = false;
            PreferencesUtils.putString(MainActivity.this, Constants.DEVICE_MAC, null);
            unbindService(discoveryFragment.mServiceConnection);
            AppController.mBluetoothLeService = null;
        }catch (Exception e){}
    }

}
