#include "frame_mailbox.h"
#include <string.h>
int frame_mailbox_init(FrameMailbox *b){b->mutex=SDL_CreateMutex();b->width=b->height=0;SDL_AtomicSet(&b->serial,0);return b->mutex!=NULL;}
void frame_mailbox_close(FrameMailbox *b){if(b->mutex)SDL_DestroyMutex(b->mutex);b->mutex=NULL;}
int frame_mailbox_publish(FrameMailbox *b,const uint32_t *pixels,int w,int h){
    if(!b->mutex||!pixels||w<1||w>426||h<1||h>240)return 0;
    SDL_LockMutex(b->mutex);memcpy(b->pixels,pixels,(size_t)w*h*sizeof *pixels);b->width=w;b->height=h;
    SDL_AtomicAdd(&b->serial,1);SDL_UnlockMutex(b->mutex);return 1;
}
int frame_mailbox_read(FrameMailbox *b,uint32_t *pixels,size_t capacity,int *serial,int *w,int *h){
    if(!b->mutex||!pixels||SDL_AtomicGet(&b->serial)==*serial)return 0;
    SDL_LockMutex(b->mutex);
    if((size_t)b->width*b->height>capacity){SDL_UnlockMutex(b->mutex);return 0;}
    memcpy(pixels,b->pixels,(size_t)b->width*b->height*sizeof *pixels);
    *w=b->width;*h=b->height;*serial=SDL_AtomicGet(&b->serial);SDL_UnlockMutex(b->mutex);return 1;
}
