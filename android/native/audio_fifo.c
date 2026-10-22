#include "audio_fifo.h"
#include <string.h>

void audio_fifo_reset(AudioFifo *f,unsigned channels) {
    f->read=f->write=0;f->channels=channels==1?1:2;
    f->underruns=f->overruns=0;f->last[0]=f->last[1]=0;
    f->fade_in=32;f->starved=0;
}
size_t audio_fifo_size(const AudioFifo *f) {return (uint32_t)(f->write-f->read);}
void audio_fifo_trim(AudioFifo *f,size_t maximum) {
    maximum-=maximum%f->channels;
    size_t size=audio_fifo_size(f);
    if(size>maximum){f->read+=(uint32_t)(size-maximum);f->overruns++;f->fade_in=32;}
}
void audio_fifo_write(AudioFifo *f,const int16_t *samples,size_t count) {
    count-=count%f->channels;
    size_t capacity=AUDIO_FIFO_CAPACITY-AUDIO_FIFO_CAPACITY%f->channels;
    if(count>capacity){samples+=count-capacity;count=capacity;}
    audio_fifo_trim(f,capacity-count);
    for(size_t i=0;i<count;i++)f->samples[f->write++&(AUDIO_FIFO_CAPACITY-1)]=samples[i];
}
void audio_fifo_read(AudioFifo *f,int16_t *out,size_t count) {
    size_t available=audio_fifo_size(f),real=available<count?available:count;
    real-=real%f->channels;
    for(size_t i=0;i<real;i++) {
        unsigned channel=(unsigned)i%f->channels;
        int sample=f->samples[f->read++&(AUDIO_FIFO_CAPACITY-1)];
        if(f->fade_in)sample=sample*(int)(32-f->fade_in)/32;
        out[i]=f->last[channel]=(int16_t)sample;
        if(channel+1==f->channels&&f->fade_in)f->fade_in--;
    }
    if(real<count) {
        if(!f->starved)f->underruns++;
        for(size_t i=real;i<count;i++) {
            size_t frame=(i-real)/f->channels;
            out[i]=frame<32?(int16_t)(f->last[i%f->channels]*(int)(31-frame)/32):0;
        }
        f->last[0]=f->last[1]=0;f->fade_in=32;f->starved=1;
    }else f->starved=0;
}
