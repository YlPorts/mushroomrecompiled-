package org.mushroomrecompiled;

import android.os.SystemClock;
import android.view.MotionEvent;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;

/** Requires a real ARM64 device. Sends actual Android pointer events to the control view. */
@RunWith(AndroidJUnit4.class)
public final class TouchInstrumentationTest {
    @Rule public ActivityScenarioRule<GameActivity> scenario=new ActivityScenarioRule<>(GameActivity.class);
    private MotionEvent event(int action,int[] ids,float[][] points){
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coordinates=new MotionEvent.PointerCoords[ids.length];
        for(int i=0;i<ids.length;i++){
            properties[i]=new MotionEvent.PointerProperties();properties[i].id=ids[i];properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i]=new MotionEvent.PointerCoords();coordinates[i].x=points[i][0];coordinates[i].y=points[i][1];coordinates[i].pressure=1;coordinates[i].size=1;
        }
        long now=SystemClock.uptimeMillis();return MotionEvent.obtain(now,now,action,ids.length,properties,coordinates,0,0,1,1,0,0,0x1002,0);
    }
    private void send(TouchControlsView view,int action,int[] ids,float[][] points){MotionEvent e=event(action,ids,points);view.onTouchEvent(e);e.recycle();}
    @Test public void pointerReleaseAndCancel() {
        scenario.getScenario().onActivity(activity->{
            Settings settings=new Settings(activity.getSharedPreferences("touch-test",0));settings.hideWithController=false;
            TouchControlsView view=new TouchControlsView(activity,settings,new TouchControlsView.Listener(){public void menu(){}public void edited(int n){}});
            view.layout(0,0,1000,500);view.safeArea(0,0,0,0);view.mode(true,false);
            send(view,MotionEvent.ACTION_DOWN,new int[]{7},new float[][]{{940,315}});assertEquals(128,NativeBridge.touchMask());
            send(view,MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{7,31},new float[][]{{940,315},{860,395}});assertEquals(192,NativeBridge.touchMask());
            send(view,MotionEvent.ACTION_POINTER_UP,new int[]{7,31},new float[][]{{940,315},{860,395}});assertEquals(64,NativeBridge.touchMask());
            send(view,MotionEvent.ACTION_CANCEL,new int[]{31},new float[][]{{860,395}});assertEquals(0,NativeBridge.touchMask());
            send(view,MotionEvent.ACTION_DOWN,new int[]{21},new float[][]{{180,305}});assertEquals(9,NativeBridge.touchMask());
            send(view,MotionEvent.ACTION_MOVE,new int[]{21},new float[][]{{60,425}});assertEquals(6,NativeBridge.touchMask());
            send(view,MotionEvent.ACTION_UP,new int[]{21},new float[][]{{60,425}});assertEquals(0,NativeBridge.touchMask());
            view.cancel();
        });
    }
}
