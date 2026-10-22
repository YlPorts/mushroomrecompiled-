/* The actual frontend/core, with SDL's headless video/audio test drivers. */
#define SDL_MAIN_HANDLED
#define main frontend_main
#include "../android/native/main.c"
#undef main
#include <assert.h>
#include <sys/stat.h>

/* Android's optional scheduling API has no equivalent in this Linux test. */
void performance_hint_open(int64_t budget){(void)budget;}
void performance_hint_report(int64_t work){(void)work;}
void performance_hint_close(void){}
static const char *private_rom;
static void wait_for(SDL_atomic_t *value,int minimum){
    Uint32 start=SDL_GetTicks();while(SDL_AtomicGet(value)<minimum){assert(SDL_GetTicks()-start<10000);SDL_Delay(5);}
}
static void wait_feedback(const char *expected){
    Uint32 start=SDL_GetTicks();for(;;){
        int found=0;SDL_AtomicLock(&bridge_lock);
        for(unsigned i=feedback_read;i<feedback_write;i++)if(!strcmp(feedback[i%8],expected))found=1;
        SDL_AtomicUnlock(&bridge_lock);if(found)return;
        assert(SDL_GetTicks()-start<10000);SDL_Delay(5);
    }
}
static int client(void *unused){
    (void)unused;Command config={0};config.operation=CONFIG;config.settings[4]=48000;config.settings[5]=1;config.settings[6]=24;config.settings[3]=config.settings[7]=100;config.deadzone=25;
    for(int i=0;i<11;i++)config.bindings[i]=-1;
    assert(enqueue(&config));Command load={0};load.operation=LOAD_ROM;SDL_strlcpy(load.path,private_rom,sizeof load.path);assert(enqueue(&load));
    wait_for(&flags,3);Java_org_mushroomrecompiled_NativeBridge_setPaused(NULL,NULL,JNI_FALSE);
    wait_for(&host_present_blocked,1);int before=SDL_AtomicGet(&host_frame_progress);SDL_Delay(200);
    assert(SDL_AtomicGet(&host_frame_progress)>=before+4);
    Java_org_mushroomrecompiled_NativeBridge_setPaused(NULL,NULL,JNI_TRUE);SDL_Delay(60);
    before=SDL_AtomicGet(&host_frame_progress);SDL_Delay(70);assert(SDL_AtomicGet(&host_frame_progress)==before);
    for(int slot=1;slot<=5;slot++){Command c={0};c.operation=SAVE;c.slot=slot;SDL_snprintf(c.path,sizeof c.path,"frontend-slot-%d.state",slot);unlink(c.path);assert(enqueue(&c));}
    for(int slot=1;slot<=5;slot++){
        char path[64];SDL_snprintf(path,sizeof path,"frontend-slot-%d.state",slot);struct stat st;Uint32 start=SDL_GetTicks();
        while(stat(path,&st)||st.st_size<=0){assert(SDL_GetTicks()-start<10000);SDL_Delay(5);}
    }
    Command restore={0};restore.operation=LOAD_STATE;restore.slot=3;SDL_strlcpy(restore.path,"frontend-slot-3.state",sizeof restore.path);assert(enqueue(&restore));
    Command reset={0};reset.operation=RESET;assert(enqueue(&reset));config.settings[0]=1;assert(enqueue(&config));
    wait_feedback("Estado cargado.");wait_feedback("Juego reiniciado.");
    Uint32 start=SDL_GetTicks();int width=0;
    while(width!=426){SDL_LockMutex(mailbox.mutex);width=mailbox.width;SDL_UnlockMutex(mailbox.mutex);assert(SDL_GetTicks()-start<10000);SDL_Delay(5);}
    before=SDL_AtomicGet(&host_frame_progress);Java_org_mushroomrecompiled_NativeBridge_setPaused(NULL,NULL,JNI_FALSE);
    wait_for(&host_frame_progress,before+5);
    Java_org_mushroomrecompiled_NativeBridge_setTouch(NULL,NULL,NES_PAD_A|NES_PAD_B);
    Java_org_mushroomrecompiled_NativeBridge_setPaused(NULL,NULL,JNI_TRUE);assert(!SDL_AtomicGet(&touch));
    Command quit={0};quit.operation=QUIT;assert(enqueue(&quit));return 0;
}
int main(int argc,char **argv){
    assert(argc==2);private_rom=argv[1];SDL_setenv("SDL_VIDEODRIVER","dummy",1);
    for(int round=0;round<2;round++){
        assert(SDL_Init(SDL_INIT_TIMER)==0);Java_org_mushroomrecompiled_NativeBridge_beginSession(NULL,NULL);
        SDL_Thread *controller=SDL_CreateThread(client,"test-client",NULL);assert(controller);
        int result=frontend_main(0,NULL);SDL_WaitThread(controller,NULL);assert(result==0);
        assert(!glue_is_rom_loaded()&&!SDL_AtomicGet(&accept_commands));
    }
    puts("Real frontend: two sessions; core advances during blocked presentation; pause, 5 slots, restore, reset, format change and clean shutdown passed (SDL headless, Linux).");return 0;
}
