#include "video_layout.h"
#include <assert.h>
#include <stdio.h>
int main(void){
    AndroidRect r=android_video_rect(2340,1080,256,0,0);
    assert(r.w==1440&&r.h==1080&&r.x==450&&r.y==0);
    r=android_video_rect(2340,1080,426,1,0);
    assert(r.w==1917&&r.h==1080&&r.x==211);
    r=android_video_rect(2340,1080,426,1,1);
    assert(r.w==1704&&r.h==960);
    r=android_video_rect(2340,1080,256,0,2);assert(r.w==1024&&r.h==960);
    for(int w=100;w<2400;w+=89)for(int h=80;h<1200;h+=91)for(int a=0;a<2;a++)for(int s=0;s<3;s++){
        r=android_video_rect(w,h,a?426:256,a,s);assert(r.w<=w&&r.h<=h&&r.w>0&&r.h>0&&r.x>=0&&r.y>=0);
    }
    r=android_video_rect(0,0,256,0,0);assert(!r.w&&!r.h);
    puts("4:3, 16:9, integer and pixel-perfect viewport checks passed.");
}
