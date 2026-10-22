#pragma once
#include <SDL.h>
#include <stddef.h>
#include <stdint.h>
#define FRAME_PIXELS (426*240)
typedef struct {
    SDL_mutex *mutex;
    SDL_atomic_t serial;
    int width,height;
    uint32_t pixels[FRAME_PIXELS];
} FrameMailbox;
int frame_mailbox_init(FrameMailbox *box);
void frame_mailbox_close(FrameMailbox *box);
int frame_mailbox_publish(FrameMailbox *box,const uint32_t *pixels,int width,int height);
int frame_mailbox_read(FrameMailbox *box,uint32_t *pixels,size_t capacity,int *serial,int *width,int *height);
