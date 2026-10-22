package org.mushroomrecompiled;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;

public final class AndroidReceivers {
    private AndroidReceivers() {}
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // The legacy overload is reachable only below API 33.
    public static void register(Context context,BroadcastReceiver receiver,IntentFilter filter,boolean exported){
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,exported?Context.RECEIVER_EXPORTED:Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver,filter);
    }
}
