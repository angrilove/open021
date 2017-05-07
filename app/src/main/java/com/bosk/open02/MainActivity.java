package com.bosk.open02;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private Context mContext;

    private AlertDialog mAlertDialog = null;

    private ArrayAdapter<String> deviceArrayAdapter;

    private List<String> deviceTokens = new ArrayList<>();

    private Set<BluetoothDevice> devices = new HashSet<>(16);

    private Button btnOpen;

    private TextView tvDevices;

    private Activity mActivity;

    private boolean mScanning;

    private String logTag = "OPEN02";

    private boolean shouldSlant;

    private boolean slanted = false; // 已经倾斜

    private boolean shouldShakes;

    private boolean shouldVibrator;

    private int sensorValue;

    private String password;

    private boolean unlocking;

    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothLeService mBluetoothLeService;

    private SensorManager mSensorManager;

    private Vibrator mVibrator;

    private FloatingActionButton fab;

    private MediaPlayer mMediaPlayer;

    public ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {

            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.supported()) {
                // TODO: 2016/11/17 应当给于友好提示
                // mActivity.finish();
                Log.e(MainActivity.class.getSimpleName(), "不支持 Ble");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
            Log.i(MainActivity.class.getName(), "和 Ble 设备失去连接");
        }
    };

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

            devices.add(device);

            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 扫描到设备
                    if (device.getName() != null && device.getName().contains("bledoor")) {
                        // 目前只能开一个门
                        for (String token : deviceTokens) {
                            if (token.contains(device.getAddress())) {
                                return;
                            }
                        }
                        deviceTokens.add(device.getName() + "\n" + device.getAddress());
                        if (mAlertDialog.isShowing()) {
                            deviceArrayAdapter.notifyDataSetChanged();
                        } else {
                            if (mScanning) {
                                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                                mScanning = false;
                            }
                            mBluetoothLeService.connect(device.getAddress());
                            deviceTokens.clear();
                        }
                    }
                }
            });
        }

    };

    private SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            // 传感器信息改变时执行
            float[] values = event.values;
            float x = values[0];
            float y = values[1];
            float z = values[2];

            if (shouldSlant) {
                if (Math.abs(x) < 1) {
                    slanted = false;
                } else if (!slanted && x < -9) { // x:R/L,
                    unlock();
                }
            }
            if (shouldShakes) {
                // TODO: 兼容三星？ see http://blog.csdn.net/catoop/article/details/8051835
                if (Math.abs(x) > sensorValue || Math.abs(y) > sensorValue || Math.abs(z) > sensorValue) {
                    unlock();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // TODO: 2016/11/15 当精度发生变化
            Log.d(logTag, accuracy + "");
        }
    };

    public void scanLeDevice(final boolean enable) {
        if (!mBluetoothLeService.supported()) {
            return;
        }
        if (enable) {
            deviceTokens.clear();
            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);

            // 扫面蓝牙设备，10秒后停止扫描
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mScanning) {
                        mScanning = false;
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    }
                }
            }, 10000);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent mIntent = new Intent(mContext, SettingsActivity.class);
            startActivity(mIntent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mActivity = this;
        getPreferences();

        Toolbar toolbar = (Toolbar) findViewById(R.id.tool_bar);
        fab = (FloatingActionButton) findViewById(R.id.fab);
        btnOpen = (Button) findViewById(R.id.btn_open_door);
        tvDevices = (TextView) findViewById(R.id.tv_devices);

        setSupportActionBar(toolbar);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mActivity.finish();
//                Snackbar.make(view, null, Snackbar.LENGTH_LONG)
//                        .setAction("Action", new OnClickListener() {
//                            @Override
//                            public void onClick(View view) {
//                                startActivity(new Intent(mContext, SettingsActivity.class));
//                            }
//                        }).show();
            }
        });

        btnOpen.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                unlock();
                StringBuilder sb = new StringBuilder();
                for (BluetoothDevice d : devices) {
                    sb.append("name: ").append(d.getName()).append(",")
                            .append("addr:").append(d.getAddress()).append("\n");
                }
                tvDevices.setText(sb.toString());
            }
        });

        // TODO: 2016/11/14 去掉解锁后的确认
        // TODO: 2016/11/14 增加解锁动态效果
        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
//        bluetoothManager.getAdapter().getBluetoothLeScanner();

        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }
        mContext = getApplicationContext();
        Intent mIntent = new Intent(mContext, BluetoothLeService.class);
        if (mContext.bindService(mIntent, mServiceConnection, BIND_AUTO_CREATE)) {
            Log.d(logTag, "绑定服务成功");
        } else {
            Log.d(logTag, "绑定服务失败");
        }
        deviceArrayAdapter = new ArrayAdapter<>(mActivity,
                android.R.layout.simple_list_item_single_choice, deviceTokens);

        DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String[] token = deviceTokens.get(which).split("\n");
                scanLeDevice(false);
                mBluetoothLeService.connect(token[1]);
            }
        };

        mAlertDialog = new AlertDialog.Builder(mActivity)
                .setAdapter(deviceArrayAdapter, onClickListener)
                .setNegativeButton(R.string.message_close, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAlertDialog.cancel();
                    }
                }).create();

    }

    @Override
    protected void onResume() {
        super.onResume();
        getPreferences();

        if (mSensorManager != null) {
            mSensorManager.registerListener(mSensorEventListener,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mSensorManager != null) {
            mSensorManager.unregisterListener(mSensorEventListener);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothLeService = null;
        getApplicationContext().unbindService(mServiceConnection);
    }

    protected void unlock() {
        if (unlocking) {
            return;
        }
        unlocking = true;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                unlocking = false;
            }
        }, 1000);

        if (!mBluetoothLeService.supported()) {
            AlertDialog alertDialog = new AlertDialog.Builder(mActivity)
                    .setMessage(R.string.message_ble_not_found)
                    .setNegativeButton(R.string.message_close, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mAlertDialog.cancel();
                        }
                    }).create();
            alertDialog.show();
            return;
        }
        if (mScanning) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
        }
        deviceTokens.clear();
        mAlertDialog.show();
        scanLeDevice(true);

        if (shouldVibrator) {
            mVibrator.vibrate(200);
        }
        if (mMediaPlayer != null) {
            // TODO: 2016/11/20 应当以多线程方式，自动结束
            mMediaPlayer.start();
        }
    }

    private void getPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        shouldSlant = prefs.getBoolean("unlock_way_slant", true);
        shouldShakes = prefs.getBoolean("unlock_way_shakes", true);
        sensorValue = Integer.parseInt(prefs.getString("shakes_sensitivity", "19"));
        password = prefs.getString("default_password", BluetoothLeService.DEFAULT_OPEN_DOOR_PWD);
        shouldVibrator = prefs.getBoolean("notifications_vibrate", true);
        String soundPath = prefs.getString("notifications_ringtone", null);
        if (soundPath != null) {
            mMediaPlayer = MediaPlayer.create(this, Uri.parse(soundPath));
        }
    }

}
