#include "glue.h"
#include "cyc_core.h"
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>

static unsigned buttons(unsigned f) {
    if((f>=180&&f<190)||(f>=360&&f<370))return NES_PAD_START;
    if(f>=720&&f<730)return NES_PAD_RIGHT;
    if(f>=840&&f<850)return NES_PAD_UP;
    if(f>=960&&f<970)return NES_PAD_A;
    if(f>=1200)return NES_PAD_RIGHT|NES_PAD_B|((f>=1230&&(f-1230)%90<55)?NES_PAD_A:0);
    return 0;
}
static uint64_t digest(void) {
    uint64_t hash=cyc_mem_state_hash()^cyc_hw_state_hash();
    int w,h;const uint32_t *p=glue_get_present_framebuffer(&w,&h);
    for(int i=0;i<w*h;i++){hash^=p[i];hash*=1099511628211ULL;}
    int16_t audio[4096];size_t n=glue_get_audio_samples(audio,4096,1);
    for(size_t i=0;i<n;i++){hash^=(uint16_t)audio[i];hash*=1099511628211ULL;}
    return hash;
}
int main(int argc,char **argv) {
    if(argc!=2||!glue_load_rom(argv[1]))return 2;
    uint64_t reference[1800];
    for(int wide=0;wide<2;wide++) {
        glue_set_widescreen(wide!=0);
        for(int fast=0;fast<2;fast++) {
            cyc_set_ppu_render_spans(fast!=0);glue_reset();
            for(unsigned f=0;f<1800;f++) {
                glue_set_pad1((uint8_t)buttons(f));glue_step_frame();
                uint64_t hash=digest();
                if(!fast)reference[f]=hash;
                else if(reference[f]!=hash) {
                    fprintf(stderr,"PPU span divergence: wide=%d frame=%u expected=%016" PRIx64 " actual=%016" PRIx64 "\n",wide,f,reference[f],hash);
                    cyc_hw_state_dump(stderr);return 1;
                }
            }
        }
        printf("PPU LLE general/span: 1800 full hardware, memory, video and audio hashes match (%s).\n",wide?"16:9":"4:3");
    }
    return 0;
}
