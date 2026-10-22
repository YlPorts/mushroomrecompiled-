package org.mushroomrecompiled;

import java.util.HashMap;
import java.util.Map;

/** Platform-independent ownership by Android pointer ID, never pointer index. */
final class TouchState {
    static final int A=128,B=64,SELECT=32,START=16,UP=8,DOWN=4,LEFT=2,RIGHT=1;
    private final Map<Integer,Integer> pointers=new HashMap<>();
    void update(int id,int mask){pointers.put(id,mask&255);}
    void release(int id){pointers.remove(id);}
    void cancel(){pointers.clear();}
    int mask(){int mask=0;for(int value:pointers.values())mask|=value;return mask;}
    static int dpad(float dx,float dy,float radius){
        if(radius<=0||Math.abs(dx)>radius||Math.abs(dy)>radius)return 0;
        float dead=radius*.24f;
        int p=0;
        if(dx < -dead)p|=LEFT;else if(dx>dead)p|=RIGHT;
        if(dy < -dead)p|=UP;else if(dy>dead)p|=DOWN;
        return p;
    }
}
