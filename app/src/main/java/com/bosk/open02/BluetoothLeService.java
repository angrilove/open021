package com.bosk.open02;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;

import java.security.PublicKey;
import java.util.Timer;
import java.util.TimerTask;


public class BluetoothLeService extends Service {

    public final static String ACTION_GATT_CONNECTED = "com.traptime.bluetooth.le.ACTION_GATT_CONNECTED";

    public final static String ACTION_GATT_DISCONNECTED = "com.traptime.bluetooth.le.ACTION_GATT_DISCONNECTED";

    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.traptime.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";

    public final static String ACTION_DATA_AVAILABLE = "com.example.traptime.le.ACTION_DATA_AVAILABLE";

    public final static String EXTRA_DATA = "com.traptime.bluetooth.le.EXTRA_DATA";

    // 写通道、主服务
    public final static String DEFAULT_UUID1 = "ffe5";

    public final static String DEFAULT_UUID2 = "ffe9";

    // 读通道、主服务
    public final static String DEFAULT_UUID3 = "ffe0";

    public final static String DEFAULT_UUID4 = "ffe4";

    // 开门的id，3个字节,6个16进制字符，产生新用户时，服务器自增1分配给用户
    public final  static String DEFAULT_OPEN_DOOR_ID = "000001";

    // 开门的密码，暂时默认6个F
    public final static  String DEFAULT_OPEN_DOOR_PWD = "FFFFFF";

    private final IBinder mBinder = new LocalBinder();

    private BluetoothManager mBluetoothManager;

    private BluetoothAdapter mBluetoothAdapter;

    private String mBluetoothDeviceAddress;

    private BluetoothGatt mBluetoothGatt;

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                broadcastUpdate(intentAction);
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                TimerTask task = new TimerTask() {
                    public void run() {
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        String openDoorId = preferences.getString("text_open_door_id", DEFAULT_OPEN_DOOR_ID);
                        String openDoorPwd = preferences.getString("text_open_door_pwd", DEFAULT_OPEN_DOOR_PWD);
                        String uuid1 = preferences.getString("text_uuid1", DEFAULT_UUID1);
                        String uuid2 = preferences.getString("text_uuid2", DEFAULT_UUID2);

                        String id = String.format("BLE:%s%s\r\n", openDoorId, openDoorPwd);
                        writeValue(uuid1, uuid2, id.getBytes());
                    }
                };
                Timer timer = new Timer();
                timer.schedule(task, 200);

                TimerTask task4 = new TimerTask() {
                    public void run() {
                        //关闭通道连接
                        disconnect();
                    }
                };
                Timer timer4 = new Timer();
                timer4.schedule(task4, 2000);

                TimerTask task5 = new TimerTask() {
                    public void run() {
                        //释放
                        close();
                    }
                };
                Timer timer5 = new Timer();
                timer5.schedule(task5, 2500);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA, characteristic.getValue());
        sendBroadcast(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        disconnect();
        close();
        return super.onUnbind(intent);
    }

    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.disconnect();
    }

    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public boolean supported() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();

        return mBluetoothAdapter != null;
    }

    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            return false;
        }
        if (mBluetoothDeviceAddress != null
                && mBluetoothDeviceAddress.equals(address)
                && mBluetoothGatt != null) {
            return mBluetoothGatt.connect();
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            return false;
        }
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        mBluetoothDeviceAddress = address;
        return true;
    }

    public boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return false;
        }
        return mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

    public void writeValue(String serviceUUID, String characteristicUUID, byte[] value) {
        for (BluetoothGattService bluetoothGattService : mBluetoothGatt.getServices()) {

            String gattServiceUUID = Long.toHexString(bluetoothGattService.getUuid().getMostSignificantBits()).substring(0, 4);
            for (BluetoothGattCharacteristic bluetoothGattCharacteristic : bluetoothGattService.getCharacteristics()) {

                String characterUUID = Long.toHexString(bluetoothGattCharacteristic.getUuid().getMostSignificantBits()).substring(0, 4);

                if (gattServiceUUID.equals(serviceUUID) && characteristicUUID.equals(characterUUID)) {
                    bluetoothGattCharacteristic.setValue(value);
                    mBluetoothGatt.writeCharacteristic(bluetoothGattCharacteristic);
                    return;
                }
            }
        }
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }
}
