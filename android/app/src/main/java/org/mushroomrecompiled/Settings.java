package org.mushroomrecompiled;

import android.content.SharedPreferences;

final class Settings {
    final int[] videoAudio={1,0,0,100,48000,1,40,100,0,0};
    final int[] buttons={-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1};
    int deadzone=25,slot=1,opacity=50;
    boolean hideWithController=true;
    boolean reserveControls=true;
    String device="";
    final float[] x={.12f,.86f,.94f,.46f,.54f,.95f};
    final float[] y={.73f,.79f,.63f,.96f,.96f,.08f};
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
        opacity=prefs.getInt("opacity",50);hideWithController=prefs.getBoolean("hideWithController",true);
        reserveControls=prefs.getBoolean("reserveControls",true);
        device=prefs.getString("controller","");
    }
    static float clamp(float n,float lo,float hi){return Math.max(lo,Math.min(hi,n));}
    void save() {
        SharedPreferences.Editor e=prefs.edit();
        for(int i=0;i<videoAudio.length;i++)e.putInt("av"+i,videoAudio[i]);
        for(int i=0;i<buttons.length;i++)e.putInt("button"+i,buttons[i]);
        for(int i=0;i<x.length;i++){e.putFloat("touchX"+i,x[i]);e.putFloat("touchY"+i,y[i]);e.putFloat("touchSize"+i,size[i]);}
        e.putInt("deadzone",deadzone).putInt("slot",slot).putInt("opacity",opacity)
            .putBoolean("hideWithController",hideWithController).putBoolean("reserveControls",reserveControls).putString("controller",device).apply();
    }
    void apply(){save();NativeBridge.configure(videoAudio,buttons,deadzone,device);}
}
