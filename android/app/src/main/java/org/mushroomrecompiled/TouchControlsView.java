package org.mushroomrecompiled;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

final class TouchControlsView extends View {
    interface Listener {void menu();void edited(int selected);}
    private final Settings settings;
    private final Listener listener;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TouchState state=new TouchState();
    private final RectF area=new RectF();
    private final float[] cx=new float[6],cy=new float[6],r=new float[6];
    private boolean hidden,gameplay,editing;
    private int dragId=-1,selected=-1;
    private float offsetX,offsetY;
    TouchControlsView(Context context,Settings settings,Listener listener){
        super(context);this.settings=settings;this.listener=listener;setContentDescription("Controles NES táctiles");
        setFocusable(false);setKeepScreenOn(true);
    }
    void safeArea(int left,int top,int right,int bottom){
        area.set(left,top,Math.max(left+1,getWidth()-right),Math.max(top+1,getHeight()-bottom));layoutControls();
    }
    void mode(boolean playing,boolean controller){
        boolean hide=controller&&settings.hideWithController;
        if(gameplay!=playing||hidden!=hide)cancel();gameplay=playing;hidden=hide;invalidate();
    }
    void edit(boolean enabled){cancel();editing=enabled;selected=-1;invalidate();}
    int selected(){return selected;}
    void resizeSelected(float scale){if(selected>=0){settings.size[selected]=Settings.clamp(scale,.5f,2f);layoutControls();}}
    void changed(){layoutControls();}
    void cancel(){state.cancel();dragId=-1;NativeBridge.setTouch(0);invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){area.set(0,0,w,h);layoutControls();requestApplyInsets();}
    private void layoutControls(){
        float unit=Math.min(area.height()*.125f,area.width()*.065f);
        for(int i=0;i<6;i++){
            r[i]=unit*(i==0?1.5f:i<3?.66f:i<5?.57f:.38f)*settings.size[i];
            float margin=r[i];cx[i]=Math.max(area.left+margin,Math.min(area.right-margin,area.left+settings.x[i]*area.width()));
            cy[i]=Math.max(area.top+margin,Math.min(area.bottom-margin,area.top+settings.y[i]*area.height()));
        }invalidate();
    }
    private boolean hit(int i,float x,float y){return i==0?Math.abs(x-cx[i])<=r[i]&&Math.abs(y-cy[i])<=r[i]:Math.hypot(x-cx[i],y-cy[i])<=r[i]*1.1f;}
    private int hitIndex(float x,float y){for(int i=5;i>=0;i--)if(hit(i,x,y))return i;return -1;}
    private int sample(float x,float y){
        int p=TouchState.dpad(x-cx[0],y-cy[0],r[0]);
        if(hit(1,x,y))p|=TouchState.B;if(hit(2,x,y))p|=TouchState.A;
        if(hit(3,x,y))p|=TouchState.SELECT;if(hit(4,x,y))p|=TouchState.START;return p;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked(),index=event.getActionIndex();
        if(action==MotionEvent.ACTION_CANCEL){cancel();return true;}
        if(editing){
            if(action==MotionEvent.ACTION_DOWN){
                selected=hitIndex(event.getX(),event.getY());
                if(selected>=0){dragId=event.getPointerId(0);offsetX=event.getX()-cx[selected];offsetY=event.getY()-cy[selected];listener.edited(selected);}
            }
            if(action==MotionEvent.ACTION_MOVE&&dragId>=0&&selected>=0){int p=event.findPointerIndex(dragId);if(p>=0){
                settings.x[selected]=Settings.clamp((event.getX(p)-offsetX-area.left)/area.width(),.02f,.98f);
                settings.y[selected]=Settings.clamp((event.getY(p)-offsetY-area.top)/area.height(),.02f,.98f);layoutControls();
            }}
            if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)&&event.getPointerId(index)==dragId)dragId=-1;
            return true;
        }
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            if(hit(5,event.getX(index),event.getY(index))){cancel();listener.menu();return true;}
        }
        if(!gameplay||hidden){cancel();return true;}
        // Re-evaluate every surviving finger; handles direction changes and A+B.
        for(int p=0;p<event.getPointerCount();p++){
            int id=event.getPointerId(p);
            if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)&&p==index)state.release(id);
            else state.update(id,sample(event.getX(p),event.getY(p)));
        }
        if(action==MotionEvent.ACTION_UP)state.cancel();
        NativeBridge.setTouch(state.mask());invalidate();return true;
    }
    @Override protected void onDetachedFromWindow(){cancel();super.onDetachedFromWindow();}
    private void text(Canvas c,String label,float x,float y,float size){paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(size);c.drawText(label,x,y+size*.34f,paint);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(editing){canvas.drawColor(0x66101116);text(canvas,"Arrastra los controles · Ajusta tamaño · Atrás para guardar",getWidth()/2f,area.top+area.height()*.22f,area.height()*.034f);}
        int alpha=editing?210:Math.max(25,settings.opacity*255/100),pressed=state.mask();
        for(int i=0;i<6;i++){
            if(i<5&&(!gameplay||hidden)&&!editing)continue;
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb(alpha,i==1||i==2?188:36,i==1||i==2?35:37,i==1||i==2?46:43));
            if(i==0){
                float arm=r[0]*.36f;
                canvas.drawRoundRect(cx[0]-arm,cy[0]-r[0],cx[0]+arm,cy[0]+r[0],8,8,paint);
                canvas.drawRoundRect(cx[0]-r[0],cy[0]-arm,cx[0]+r[0],cy[0]+arm,8,8,paint);
                text(canvas,"▲",cx[0],cy[0]-r[0]*.64f,r[0]*.25f);text(canvas,"▼",cx[0],cy[0]+r[0]*.64f,r[0]*.25f);
                text(canvas,"◀",cx[0]-r[0]*.64f,cy[0],r[0]*.25f);text(canvas,"▶",cx[0]+r[0]*.64f,cy[0],r[0]*.25f);
            }else{
                int button=i==1?64:i==2?128:i==3?32:i==4?16:0;
                if((pressed&button)!=0)paint.setColor(Color.argb(alpha,235,80,85));
                canvas.drawCircle(cx[i],cy[i],r[i],paint);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(0x99e9e2d6);canvas.drawCircle(cx[i],cy[i],r[i],paint);
                text(canvas,i==1?"B":i==2?"A":i==3?"SELECT":i==4?"START":"☰",cx[i],cy[i],r[i]*(i==3||i==4?.40f:.8f));
            }
            if(editing&&selected==i){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setColor(0xff60ddc0);canvas.drawCircle(cx[i],cy[i],r[i]+6,paint);}
        }
    }
}
