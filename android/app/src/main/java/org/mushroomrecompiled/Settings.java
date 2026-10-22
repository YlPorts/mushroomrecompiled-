package org.mushroomrecompiled;

import android.content.SharedPreferences;

final class Settings {
    final int[] videoAudio={0,0,0,100,48000,1,24,100,0,0};
    final int[] buttons={-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1};
    int deadzone=25,slot=1,opacity=65;
    boolean showStats=true;
    boolean hideWithController=true;
    boolean reserveControls=true;
    String device="";
    static final float[] DEFAULT_X={.12f,.84f,.94f,.44f,.56f,.95f};
    static final float[] DEFAULT_Y={.73f,.81f,.64f,.94f,.94f,.08f};
    final float[] x=DEFAULT_X.clone(),y=DEFAULT_Y.clone();
    final float[] size={1f,1f,1f,1f,1f,1f};
    private final SharedPreferences prefs;
    Settings(SharedPreferences prefs) {
        this.prefs=prefs;
        for(int i=0;i<videoAudio.length;i++)videoAudio[i]=prefs.getInt("av"+i,videoAudio[i]);
        for(int i=0;i<buttons.length;i++)buttons[i]=prefs.getInt("button"+i,-1);
        for(int i=0;i<x.length;i++){
            x[i]=clamp(prefs.getFloat("touchX"+i,x[i]),.02f,.98f);
            y[i]=clamp(prefs.getFloat("touchY"+i,y[i]),.02f,.98f);
            size[i]=clamp(prefs.getFloat("touchSize"+i,size[i]),.5f,2f);
        }
        deadzone=prefs.getInt("deadzone",25);slot=prefs.getInt("slot",1);
        opacity=prefs.getInt("opacity",65);hideWithController=prefs.getBoolean("hideWithController",true);
        showStats=prefs.getBoolean("showStats",true);
        reserveControls=prefs.getBoolean("reserveControls",true);
        device=prefs.getString("controller","");
        // Earlier default buffers can be reduced once; explicit larger buffers remain.
        if(!prefs.getBoolean("audioLatencyV2",false)){
            if(videoAudio[6]==40)videoAudio[6]=24;
            prefs.edit().putBoolean("audioLatencyV2",true).apply();
        }
        videoAudio[0]=videoAudio[0]==1?1:0;videoAudio[1]=Math.max(0,Math.min(2,videoAudio[1]));
        slot=Math.max(1,Math.min(5,slot));opacity=Math.max(10,Math.min(100,opacity));
    }
    static float clamp(float n,float lo,float hi){return Math.max(lo,Math.min(hi,n));}
    void save() {
        SharedPreferences.Editor e=prefs.edit();
        for(int i=0;i<videoAudio.length;i++)e.putInt("av"+i,videoAudio[i]);
        for(int i=0;i<buttons.length;i++)e.putInt("button"+i,buttons[i]);
        for(int i=0;i<x.length;i++){e.putFloat("touchX"+i,x[i]);e.putFloat("touchY"+i,y[i]);e.putFloat("touchSize"+i,size[i]);}
        e.putInt("deadzone",deadzone).putInt("slot",slot).putInt("opacity",opacity)
            .putBoolean("hideWithController",hideWithController).putBoolean("reserveControls",reserveControls).putBoolean("showStats",showStats).putString("controller",device).apply();
    }
    void apply(){save();NativeBridge.configure(videoAudio,buttons,deadzone,device);}
}
