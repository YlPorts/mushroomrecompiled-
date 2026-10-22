package org.mushroomrecompiled;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import java.util.Arrays;

final class TouchControlsView extends View {
    interface Listener {void menu();void edited(int selected);}
    private final Settings settings;
    private final Listener listener;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TouchState state=new TouchState();
    private final TouchGeometry geometry=new TouchGeometry();
    private final RectF area=new RectF();
    private final Path cross=new Path(),arrow=new Path();
    private final int[] owners=new int[32];
    private final float[] cx=geometry.x,cy=geometry.y,r=geometry.r;
    private boolean hidden,gameplay,editing,testing;
    private int dragId=-1,selected=-1;
    private float offsetX,offsetY;
    TouchControlsView(Context context,Settings settings,Listener listener){
        super(context);this.settings=settings;this.listener=listener;Arrays.fill(owners,-1);
        setContentDescription("Cruceta NES, B, A, SELECT, START y menú");setFocusable(false);
        paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
    }
    void safeArea(int left,int top,int right,int bottom){
        area.set(left,top,Math.max(left+1,getWidth()-right),Math.max(top+1,getHeight()-bottom));layoutControls();
    }
    void mode(boolean playing,boolean controller){
        boolean hide=controller&&settings.hideWithController;
        if(gameplay==playing&&hidden==hide)return;
        cancel();gameplay=playing;hidden=hide;invalidate();
    }
    void test(boolean enabled){cancel();testing=enabled;invalidate();}
    void edit(boolean enabled){cancel();editing=enabled;selected=-1;invalidate();}
    int selected(){return selected;}
    float controlX(int i){return cx[i];}float controlY(int i){return cy[i];}
    void resizeSelected(float scale){if(selected>=0){settings.size[selected]=Settings.clamp(scale,.5f,2f);layoutControls();}}
    void changed(){layoutControls();}
    void cancel(){state.cancel();Arrays.fill(owners,-1);dragId=-1;NativeBridge.setTouch(0);invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){cancel();area.set(0,0,w,h);layoutControls();requestApplyInsets();}
    private void layoutControls(){
        float unit=Math.min(area.height()*.125f,area.width()*.065f);
        for(int i=0;i<6;i++){
            r[i]=unit*(i==0?1.45f:i<3?.75f:i<5?.47f:.36f)*settings.size[i];
            float mx=r[i]*(i==3||i==4?1.65f:1.1f),my=r[i]*(i==3||i==4?.95f:1.1f);
            cx[i]=Math.max(area.left+mx,Math.min(area.right-mx,area.left+settings.x[i]*area.width()));
            cy[i]=Math.max(area.top+my,Math.min(area.bottom-my,area.top+settings.y[i]*area.height()));
        }
        float x=cx[0],y=cy[0],z=r[0],a=z*.35f;
        cross.reset();cross.moveTo(x-a,y-z);cross.lineTo(x+a,y-z);cross.lineTo(x+a,y-a);
        cross.lineTo(x+z,y-a);cross.lineTo(x+z,y+a);cross.lineTo(x+a,y+a);
        cross.lineTo(x+a,y+z);cross.lineTo(x-a,y+z);cross.lineTo(x-a,y+a);
        cross.lineTo(x-z,y+a);cross.lineTo(x-z,y-a);cross.lineTo(x-a,y-a);cross.close();invalidate();
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked(),index=event.getActionIndex();
        if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_OUTSIDE){cancel();return true;}
        if(action==MotionEvent.ACTION_DOWN){cancel();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}
        if(editing){
            if(action==MotionEvent.ACTION_DOWN){
                selected=geometry.pick(event.getX(),event.getY(),true);
                if(selected>=0){dragId=event.getPointerId(0);offsetX=event.getX()-cx[selected];offsetY=event.getY()-cy[selected];listener.edited(selected);invalidate();}
            }
            if(action==MotionEvent.ACTION_MOVE&&dragId>=0&&selected>=0){int p=event.findPointerIndex(dragId);if(p>=0){
                settings.x[selected]=Settings.clamp((event.getX(p)-offsetX-area.left)/area.width(),.02f,.98f);
                settings.y[selected]=Settings.clamp((event.getY(p)-offsetY-area.top)/area.height(),.02f,.98f);layoutControls();
            }else dragId=-1;}
            if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)&&event.getPointerId(index)==dragId)dragId=-1;
            return true;
        }
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            int pick=geometry.pick(event.getX(index),event.getY(index),true);
            if(pick==5){cancel();listener.menu();performClick();return true;}
            int id=event.getPointerId(index);if(id>=0&&id<owners.length)owners[id]=pick;
        }
        if(!gameplay||hidden){cancel();return true;}
        int previous=state.mask();
        for(int p=0;p<event.getPointerCount();p++){
            int id=event.getPointerId(p);
            if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)&&p==index){state.release(id);if(id>=0&&id<owners.length)owners[id]=-1;}
            else state.update(id,geometry.sample(event.getX(p),event.getY(p),id>=0&&id<owners.length?owners[id]:-1));
        }
        if(action==MotionEvent.ACTION_UP){state.cancel();Arrays.fill(owners,-1);performClick();}
        if(state.mask()!=previous){NativeBridge.setTouch(state.mask());invalidate();}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus)cancel();}
    @Override protected void onDetachedFromWindow(){cancel();super.onDetachedFromWindow();}
    private void fill(int color,int alpha){paint.setStyle(Paint.Style.FILL);paint.setColor((color&0xffffff)|(Math.max(0,Math.min(255,alpha))<<24));}
    private void stroke(int color,int alpha,float width){fill(color,alpha);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);}
    private void text(Canvas c,String label,float x,float y,float size,int alpha){fill(0xf2eee7,alpha);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(size);c.drawText(label,x,y+size*.34f,paint);}
    private void triangle(Canvas c,float x,float y,float size,int direction,int alpha){
        c.save();c.rotate(direction,x,y);arrow.reset();arrow.moveTo(x,y-size*.55f);arrow.lineTo(x+size*.5f,y+size*.35f);arrow.lineTo(x-size*.5f,y+size*.35f);arrow.close();fill(0xe8e4db,alpha);c.drawPath(arrow,paint);c.restore();
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(editing||testing){c.drawColor(0xe6111318);text(c,editing?"ARRASTRA UN CONTROL PARA AJUSTARLO":"PRUEBA DE BOTONES",getWidth()/2f,area.top+area.height()*.23f,area.height()*.038f,245);}
        if(testing){
            StringBuilder active=new StringBuilder();int mask=state.mask();
            String[] names={"A","B","SELECT","START","ARRIBA","ABAJO","IZQ.","DER."};int[] bits={128,64,32,16,8,4,2,1};
            for(int i=0;i<bits.length;i++)if((mask&bits[i])!=0){if(active.length()>0)active.append(" + ");active.append(names[i]);}
            text(c,active.length()==0?"Pulsa A+B, prueba diagonales y suelta los dedos":active.toString(),getWidth()/2f,area.top+area.height()*.36f,area.height()*.034f,235);
        }
        int alpha=editing||testing?225:Math.max(25,settings.opacity*255/100),pressed=state.mask();
        for(int i=0;i<6;i++){
            if(i<5&&(!gameplay||hidden)&&!editing)continue;
            float x=cx[i],y=cy[i],z=r[i];
            if(i==0){
                fill(0x101115,alpha/3);c.drawCircle(x,y,z*1.12f,paint);
                c.save();c.translate(0,z*.055f);fill(0x050608,alpha);c.drawPath(cross,paint);c.restore();
                fill(0x292c32,alpha);c.drawPath(cross,paint);stroke(0xc4c2b9,(int)(alpha*.6f),Math.max(1,z*.018f));c.drawPath(cross,paint);
                float a=z*.30f;fill(0xe45151,alpha);
                if((pressed&8)!=0)c.drawRoundRect(x-a,y-z*.94f,x+a,y-z*.4f,z*.06f,z*.06f,paint);
                if((pressed&4)!=0)c.drawRoundRect(x-a,y+z*.4f,x+a,y+z*.94f,z*.06f,z*.06f,paint);
                if((pressed&2)!=0)c.drawRoundRect(x-z*.94f,y-a,x-z*.4f,y+a,z*.06f,z*.06f,paint);
                if((pressed&1)!=0)c.drawRoundRect(x+z*.4f,y-a,x+z*.94f,y+a,z*.06f,z*.06f,paint);
                triangle(c,x,y-z*.68f,z*.18f,0,alpha);triangle(c,x+z*.68f,y,z*.18f,90,alpha);
                triangle(c,x,y+z*.68f,z*.18f,180,alpha);triangle(c,x-z*.68f,y,z*.18f,270,alpha);
                fill(0x15171b,alpha);c.drawCircle(x,y,z*.18f,paint);
            }else if(i<3){
                boolean down=(pressed&(i==1?64:128))!=0;float shift=down?z*.05f:0;
                fill(0x0a0b0e,(int)(alpha*.8f));c.drawCircle(x,y+z*.05f,z*1.11f,paint);
                stroke(0xe3ded2,(int)(alpha*.65f),Math.max(1,z*.035f));c.drawCircle(x,y,z*1.02f,paint);
                fill(down?0xf66a61:0xbb303c,alpha);c.drawCircle(x,y+shift,z*.89f,paint);
                stroke(0xffb3a5,(int)(alpha*.55f),z*.025f);c.drawArc(x-z*.72f,y-z*.72f+shift,x+z*.72f,y+z*.72f+shift,205,130,false,paint);
                text(c,i==1?"B":"A",x,y+shift,z*.88f,Math.min(255,alpha+60));
            }else if(i<5){
                boolean down=(pressed&(i==3?32:16))!=0;
                fill(0x08090c,alpha);c.drawRoundRect(x-z*1.55f,y-z*.56f+3,x+z*1.55f,y+z*.56f+3,z*.55f,z*.55f,paint);
                fill(down?0xa8464e:0x353940,alpha);c.drawRoundRect(x-z*1.55f,y-z*.56f,x+z*1.55f,y+z*.56f,z*.55f,z*.55f,paint);
                stroke(0xc3c4c1,(int)(alpha*.65f),Math.max(1,z*.035f));c.drawRoundRect(x-z*1.55f,y-z*.56f,x+z*1.55f,y+z*.56f,z*.55f,z*.55f,paint);
                text(c,i==3?"SELECT":"START",x,y,z*.48f,Math.min(255,alpha+60));
            }else{
                fill(0x20242b,Math.min(230,alpha+35));c.drawRoundRect(x-z*1.1f,y-z*.85f,x+z*1.1f,y+z*.85f,z*.3f,z*.3f,paint);
                stroke(0xdedcd4,Math.min(240,alpha+60),z*.1f);
                for(int line=-1;line<=1;line++)c.drawLine(x-z*.5f,y+line*z*.3f,x+z*.5f,y+line*z*.3f,paint);
            }
            if(editing&&selected==i){stroke(0xffd28a,255,3);c.drawCircle(x,y,z*1.18f,paint);}
        }
    }
}
