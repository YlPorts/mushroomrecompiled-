package org.mushroomrecompiled;

/** Platform-independent ownership by Android pointer ID, never pointer index. */
final class TouchState {
    static final int A=128,B=64,SELECT=32,START=16,UP=8,DOWN=4,LEFT=2,RIGHT=1;
    private final int[] ids=new int[32],masks=new int[32];
    private int count,combined;
    void update(int id,int mask){
        mask&=255;if(mask==0){release(id);return;}
        for(int i=0;i<count;i++)if(ids[i]==id){if(masks[i]!=mask){masks[i]=mask;combine();}return;}
        if(count<ids.length){ids[count]=id;masks[count++]=mask;combined|=mask;}
    }
    void release(int id){for(int i=0;i<count;i++)if(ids[i]==id){count--;ids[i]=ids[count];masks[i]=masks[count];combine();return;}}
    void cancel(){count=combined=0;}
    int mask(){return combined;}
    private void combine(){combined=0;for(int i=0;i<count;i++)combined|=masks[i];}
    static int dpad(float dx,float dy,float radius){
        if(radius<=0||Math.abs(dx)>radius||Math.abs(dy)>radius)return 0;
        float dead=radius*.24f;
        int p=0;
        if(dx < -dead)p|=LEFT;else if(dx>dead)p|=RIGHT;
        if(dy < -dead)p|=UP;else if(dy>dead)p|=DOWN;
        return p;
    }
}
