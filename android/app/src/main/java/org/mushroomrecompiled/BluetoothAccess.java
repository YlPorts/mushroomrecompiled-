package org.mushroomrecompiled;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.libsdl.app.SDL;

/** SDL's asynchronous BLE callbacks can outlive a revocable Nearby Devices grant. */
@SuppressLint("MissingPermission") // Every protected call goes through checked(), including revocation races.
public final class BluetoothAccess {
    private BluetoothAccess() {}
    private static <T> T checked(Supplier<T> operation,T unavailable){
        Context context=SDL.getContext();
        if(context==null)return unavailable;
        String permission=Build.VERSION.SDK_INT>=31?android.Manifest.permission.BLUETOOTH_CONNECT:android.Manifest.permission.BLUETOOTH;
        if(context.checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED)return unavailable;
        try{return operation.get();}catch(SecurityException revoked){return unavailable;}
    }
    public static boolean read(BluetoothGatt g,BluetoothGattCharacteristic c){return checked(()->g!=null&&g.readCharacteristic(c),false);}
    public static boolean write(BluetoothGatt g,BluetoothGattCharacteristic c){return checked(()->g!=null&&g.writeCharacteristic(c),false);}
    public static boolean notify(BluetoothGatt g,BluetoothGattCharacteristic c,boolean on){return checked(()->g!=null&&g.setCharacteristicNotification(c,on),false);}
    public static boolean descriptor(BluetoothGatt g,BluetoothGattDescriptor d){return checked(()->g!=null&&g.writeDescriptor(d),false);}
    public static BluetoothGatt connect(BluetoothDevice d,Context c,boolean managed,BluetoothGattCallback callback,int transport){return checked(()->d.connectGatt(c,managed,callback,transport),null);}
    public static BluetoothGatt connectLegacy(BluetoothDevice d,Context c,boolean managed,BluetoothGattCallback callback){return checked(()->d.connectGatt(c,managed,callback),null);}
    public static int connectionState(BluetoothManager manager,BluetoothDevice d,int profile){return checked(()->manager.getConnectionState(d,profile),BluetoothProfile.STATE_DISCONNECTED);}
    public static List<BluetoothDevice> connected(BluetoothManager manager,int profile){return checked(()->manager.getConnectedDevices(profile),Collections.emptyList());}
    public static Set<BluetoothDevice> bonded(BluetoothAdapter adapter){return checked(adapter::getBondedDevices,Collections.emptySet());}
    public static String name(BluetoothDevice device){return checked(device::getName,null);}
    public static int type(BluetoothDevice device){return checked(device::getType,0);}
    public static void disconnect(BluetoothGatt g){if(g!=null)checked(()->{g.disconnect();return true;},false);}
    public static void close(BluetoothGatt g){if(g!=null)checked(()->{g.close();return true;},false);}
    public static boolean discover(BluetoothGatt g){return checked(()->g!=null&&g.discoverServices(),false);}
    public static List<BluetoothGattService> services(BluetoothGatt g){return checked(()->g==null?Collections.emptyList():g.getServices(),Collections.emptyList());}
    public static BluetoothGattService service(BluetoothGatt g,UUID uuid){return checked(()->g==null?null:g.getService(uuid),null);}
}
