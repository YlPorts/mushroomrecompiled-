/* Offline measurement of the real game, without a display or a frame limiter. */
#include "glue.h"
#include "cyc_core.h"
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

static double seconds(void) {
    struct timespec t;
    timespec_get(&t,TIME_UTC);
    return t.tv_sec+t.tv_nsec/1e9;
}
static unsigned buttons(unsigned f) {
    if((f>=180&&f<190)||(f>=360&&f<370))return NES_PAD_START;
    if(f>=720&&f<730)return NES_PAD_RIGHT;
    if(f>=840&&f<850)return NES_PAD_UP;
    if(f>=960&&f<970)return NES_PAD_A;
    if(f>=1200)return NES_PAD_RIGHT|NES_PAD_B|((f>=1230&&(f-1230)%90<55)?NES_PAD_A:0);
    return 0;
}
int main(int argc,char **argv) {
    if(argc<2){fprintf(stderr,"Usage: %s <private ROM> [widescreen: 0/1] [frames: 1800]\n",argv[0]);return 2;}
    int wide=argc>2?atoi(argv[2]):0;
    unsigned frames=argc>3?(unsigned)atoi(argv[3]):1800;
    if(frames<1201||frames>100000||!glue_load_rom(argv[1]))return 3;
    glue_set_widescreen(wide!=0);
    double core=0,video=0,start=seconds();
    uint64_t audio_hash=14695981039346656037ULL,image_hash=audio_hash;
    for(unsigned f=0;f<frames;f++) {
        glue_set_pad1((uint8_t)buttons(f));
        double t=seconds();glue_step_frame();double after=seconds();
        int w,h;const uint32_t *pixels=glue_get_present_framebuffer(&w,&h);
        double end=seconds();
        if(f>=1200){core+=after-t;video+=end-after;}
        int16_t samples[4096];size_t n=glue_get_audio_samples(samples,4096,1);
        for(size_t i=0;i<n;i++){audio_hash^=(uint16_t)samples[i];audio_hash*=1099511628211ULL;}
        if(f+1==frames)for(int i=0;i<w*h;i++){image_hash^=pixels[i];image_hash*=1099511628211ULL;}
    }
    unsigned measured=frames-1200;
    printf("%s: %u gameplay frames; core %.3f ms/frame; compositor %.3f ms/frame; offline %.2f FPS; total %.2f s\n",
        wide?"16:9":"4:3",measured,1000*core/measured,1000*video/measured,measured/(core+video),seconds()-start);
    printf("state %016" PRIx64 " image %016" PRIx64 " audio %016" PRIx64 "\n",cyc_mem_state_hash(),image_hash,audio_hash);
    return 0;
}
