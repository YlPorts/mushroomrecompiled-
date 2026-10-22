#define SDL_MAIN_HANDLED
#include "frame_mailbox.h"
#include <assert.h>
#include <stdio.h>
static FrameMailbox box;
static SDL_atomic_t done;
static int writer(void *unused){
    (void)unused;static uint32_t frame[FRAME_PIXELS];
    for(unsigned n=1;n<=2000;n++){
        int w=n&1?256:426;for(int i=0;i<w*240;i++)frame[i]=n;
        assert(frame_mailbox_publish(&box,frame,w,240));
        if(n%20==0)SDL_Delay(1);
    }
    SDL_AtomicSet(&done,1);return 0;
}
int main(void){
    assert(SDL_Init(SDL_INIT_TIMER)==0);assert(frame_mailbox_init(&box));
    SDL_Thread *thread=SDL_CreateThread(writer,"frame-producer",NULL);assert(thread);
    static uint32_t frame[FRAME_PIXELS];int serial=0,w=0,h=0,reads=0;
    while(!SDL_AtomicGet(&done)||SDL_AtomicGet(&box.serial)!=serial){
        if(!frame_mailbox_read(&box,frame,FRAME_PIXELS,&serial,&w,&h)){SDL_Delay(1);continue;}
        assert((w==256||w==426)&&h==240&&frame[0]>0);
        for(int i=1;i<w*h;i++)assert(frame[i]==frame[0]);
        assert(w==(frame[0]&1?256:426));reads++;SDL_Delay(2); // Deliberately slow presentation.
    }
    SDL_WaitThread(thread,NULL);assert(reads>0&&reads<2000&&frame[0]==2000);
    assert(!frame_mailbox_publish(&box,frame,427,240));
    frame_mailbox_close(&box);SDL_Quit();
    puts("Independent producer/presenter: bounded latest frame, dimensions, no torn pixels and shutdown passed.");return 0;
}
