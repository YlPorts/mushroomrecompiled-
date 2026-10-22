#pragma once
typedef struct { int x,y,w,h; } AndroidRect;
/* aspect: 0 = original 4:3, 1 = original wide compositor. scale: fit, integer, square pixels. */
AndroidRect android_video_rect(int w,int h,int source_w,int aspect,int scale);
