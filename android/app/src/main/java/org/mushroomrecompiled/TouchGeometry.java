package org.mushroomrecompiled;

/** Shared hit geometry, independent of Android and tested on the build host. */
final class TouchGeometry {
    final float[] x=new float[6],y=new float[6],r=new float[6];
    boolean hit(int i,float px,float py){
        float dx=Math.abs(px-x[i]),dy=Math.abs(py-y[i]),radius=r[i];
        if(radius<=0)return false;
        if(i==0)return dx<=radius&&dy<=radius;
        if(i==3||i==4)return dx<=radius*1.65f&&dy<=radius*.95f;
        if(i==5)return dx<=radius*1.35f&&dy<=radius*1.35f;
        return dx*dx+dy*dy<=radius*radius*1.35f;
    }
    int pick(float px,float py,boolean all){
        if(all&&hit(5,px,py))return 5;
        int selected=-1;float distance=Float.MAX_VALUE;
        for(int i=all?0:1;i<5;i++)if(hit(i,px,py)){
            float dx=(px-x[i])/r[i],dy=(py-y[i])/r[i],score=dx*dx+dy*dy;
            if(score<distance){distance=score;selected=i;}
        }
        return selected;
    }
    int sample(float px,float py,int owner){
        if(owner==0){
            float dx=px-x[0],dy=py-y[0],radius=r[0];
            if(Math.abs(dx)>radius*1.55f||Math.abs(dy)>radius*1.55f)return 0;
            return TouchState.dpad(Math.max(-radius,Math.min(radius,dx)),Math.max(-radius,Math.min(radius,dy)),radius);
        }
        switch(pick(px,py,false)){
            case 1:return TouchState.B;case 2:return TouchState.A;
            case 3:return TouchState.SELECT;case 4:return TouchState.START;default:return 0;
        }
    }
}
