#include "video_layout.h"
AndroidRect android_video_rect(int w,int h,int source_w,int aspect,int scale) {
    AndroidRect r={0,0,0,0};
    if(w<=0||h<=0)return r;
    int design_w=aspect==1?426:320,design_h=240;
    if(scale==2)design_w=source_w;
    int step=w/design_w;
    if(h/design_h<step)step=h/design_h;
    if(scale && step>=1) {r.w=design_w*step;r.h=design_h*step;}
    else if((long long)w*design_h<=(long long)h*design_w) {r.w=w;r.h=(int)((long long)w*design_h/design_w);}
    else {r.h=h;r.w=(int)((long long)h*design_w/design_h);}
    r.x=(w-r.w)/2;r.y=(h-r.h)/2;return r;
}
