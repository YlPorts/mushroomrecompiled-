#pragma once
#include <stddef.h>
#include <stdint.h>

/* The SDL device lock protects this preallocated FIFO. No allocation or core
 * access is permitted from the audio callback. Counts are interleaved samples. */
#define AUDIO_FIFO_CAPACITY 32768u
typedef struct {
    int16_t samples[AUDIO_FIFO_CAPACITY];
    uint32_t read,write;
    unsigned channels,underruns,overruns;
    int16_t last[2];
    unsigned fade_in;
    int starved;
} AudioFifo;
void audio_fifo_reset(AudioFifo *fifo,unsigned channels);
size_t audio_fifo_size(const AudioFifo *fifo);
void audio_fifo_write(AudioFifo *fifo,const int16_t *samples,size_t count);
void audio_fifo_trim(AudioFifo *fifo,size_t maximum);
void audio_fifo_read(AudioFifo *fifo,int16_t *samples,size_t count);
