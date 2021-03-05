package emed.tetra.ble.test;


import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Build;
import android.os.Bundle;

import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;


public class ButtonsActivity extends AppCompatActivity {

    private final static String TAG = ButtonsActivity.class.getSimpleName();
    Button btnFast, btnSlow, btnOn, btnOff;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buttons);
        btnFast = findViewById(R.id.btFast);
        btnSlow = findViewById(R.id.btSlow);
        btnOn = findViewById(R.id.btOn);
        btnOff = findViewById(R.id.btOff);

        btnFast.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN ) {
                    sendData("fast");
                    return false;
                }
                return false;
            }
        });

        btnSlow.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN ) {
                    sendData("slow");
                    return false;
                }
                return false;
            }
        });

        btnOn.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN ) {
                    sendData("on");
                    return false;
                }
                return false;
            }
        });

        btnOff.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN ) {
                    sendData("off");
                    return false;
                }
                return false;
            }
        });
    }

    public void sendData(String dat)
    {
        try {
//            Toast.makeText(getActivity(), "UUID:"+gattCharacteristic.getUuid().toString(), Toast.LENGTH_SHORT).show();
            AppController.characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            AppController.mBluetoothLeService.writeCharacteristic(AppController.characteristic, dat);
        }catch (Exception e){}
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void onClick(View v)
    {
        switch (v.getId())
        {
            case R.id.btFast:case R.id.btSlow:case R.id.btOn:case R.id.btOff:
                sendData("S");
                break;
            case R.id.txtBack:
                finish();
                break;
        }
    }
}
