#include "audio_fifo.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
static AudioFifo fifo;
int main(void) {
    int16_t input[4096],out[4096];
    for(int i=0;i<4096;i++)input[i]=(int16_t)(1000+i);
    audio_fifo_reset(&fifo,2);fifo.fade_in=0;
    for(int n=0;n<100;n++) {
        audio_fifo_write(&fifo,input,4096);audio_fifo_read(&fifo,out,256);
        assert(!memcmp(input,out,sizeof(int16_t)*256));
        audio_fifo_read(&fifo,out,3840);assert(!memcmp(input+256,out,sizeof(int16_t)*3840));
        assert(!audio_fifo_size(&fifo));
    }
    /* One starvation event, soft silence, then a soft restart. */
    audio_fifo_read(&fifo,out,256);assert(fifo.underruns==1);
    assert(out[0]>0&&out[64]==0&&out[255]==0);
    audio_fifo_read(&fifo,out,256);assert(fifo.underruns==1);
    audio_fifo_write(&fifo,input,4096);audio_fifo_read(&fifo,out,256);
    assert(out[0]==0&&out[1]==0&&out[64]==input[64]);
    assert(fifo.underruns==1);
    /* Bounded backlog and frame-aligned trimming retain the newest audio. */
    audio_fifo_reset(&fifo,1);fifo.fade_in=0;
    audio_fifo_write(&fifo,input,4096);audio_fifo_trim(&fifo,1024);
    assert(audio_fifo_size(&fifo)==1024&&fifo.overruns==1);
    fifo.fade_in=0;audio_fifo_read(&fifo,out,1024);assert(out[0]==input[3072]);
    audio_fifo_reset(&fifo,2);
    for(int i=0;i<20;i++)audio_fifo_write(&fifo,input,4096);
    assert(audio_fifo_size(&fifo)==AUDIO_FIFO_CAPACITY&&fifo.overruns>0);
    fifo.read=fifo.write=UINT32_MAX-63;fifo.fade_in=0;
    audio_fifo_write(&fifo,input,256);audio_fifo_read(&fifo,out,256);
    assert(!memcmp(input,out,256*sizeof(int16_t))&&!audio_fifo_size(&fifo));
    puts("Audio FIFO: wrap, stereo/mono, starvation, fades, bounded backlog and restart passed.");
    return 0;
}
