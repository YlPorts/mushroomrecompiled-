/* Android frontend to the original NESRecomp runtime. All core calls stay on SDL's thread. */
#include <SDL.h>
#include <SDL_system.h>
#include <jni.h>
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "glue.h"
#include "input.h"
#include "cyc_run.h"
#include "video_layout.h"

enum { LOAD_ROM=1, RESET, SAVE, LOAD_STATE, QUIT, CONFIG, CAPTURE };
typedef struct {
    int operation,slot;
    int settings[10],bindings[11],deadzone;
    char path[4096];
} Command;
static Command commands[32];
static unsigned read_index,write_index;
static SDL_SpinLock bridge_lock;
static SDL_atomic_t touch,menu_pause,flags,control_gutter,accept_commands;
static char feedback[8][512];
static unsigned feedback_read,feedback_write;
static char diagnostics[512]="Iniciando SDL2";
static char controller_list[49152];
static int controller_list_dirty=1;
static int safe[6];
static SDL_Window *window;
static SDL_Renderer *renderer;
static SDL_Texture *texture;
static SDL_AudioDeviceID audio;
static int texture_width,quit_requested;
static int settings[10]={1,0,0,100,48000,1,40,100,0,0};
static unsigned underruns,overruns;
static int priming=1;
static unsigned audio_channels=2;
static int capture_action=-1,capture_armed;

static void report(const char *message) {
    __android_log_print(ANDROID_LOG_INFO,"MushroomRecompiled","%s",message);
    SDL_AtomicLock(&bridge_lock);
    if(feedback_write-feedback_read>=8)feedback_read++;
    SDL_strlcpy(feedback[feedback_write++%8],message,512);
    SDL_AtomicUnlock(&bridge_lock);
}
static int enqueue(const Command *c) {
    SDL_AtomicLock(&bridge_lock);
    int ok=SDL_AtomicGet(&accept_commands)&&write_index-read_index<32;
    if(ok)commands[write_index++%32]=*c;
    SDL_AtomicUnlock(&bridge_lock);return ok;
}
JNIEXPORT void JNICALL Java_org_mushroomrecompiled_NativeBridge_beginSession(JNIEnv *env,jclass cls) {
    (void)env;(void)cls;
    SDL_AtomicLock(&bridge_lock);
    read_index=write_index=feedback_read=feedback_write=0;controller_list[0]=0;
    SDL_strlcpy(diagnostics,"Iniciando SDL2",sizeof diagnostics);
    SDL_AtomicSet(&touch,0);SDL_AtomicSet(&flags,0);SDL_AtomicSet(&menu_pause,1);SDL_AtomicSet(&control_gutter,0);SDL_AtomicSet(&accept_commands,1);
    SDL_AtomicUnlock(&bridge_lock);
}
static void stop_session(void) {
    SDL_AtomicLock(&bridge_lock);SDL_AtomicSet(&accept_commands,0);read_index=write_index;controller_list[0]=0;SDL_AtomicUnlock(&bridge_lock);
    SDL_AtomicSet(&flags,0);SDL_AtomicSet(&touch,0);glue_unload_rom();
}
static int dequeue(Command *c) {
    SDL_AtomicLock(&bridge_lock);
    int ok=read_index!=write_index;
    if(ok)*c=commands[read_index++%32];
    SDL_AtomicUnlock(&bridge_lock);return ok;
}
JNIEXPORT jboolean JNICALL Java_org_mushroomrecompiled_NativeBridge_command(JNIEnv *env,jclass cls,jint op,jstring path,jint slot) {
    (void)cls;Command c={0};c.operation=op;c.slot=slot;
    if(op<LOAD_ROM || op>CAPTURE || op==CONFIG)return JNI_FALSE;
    if(path) {
        const char *s=(*env)->GetStringUTFChars(env,path,NULL);if(!s)return JNI_FALSE;
        size_t n=strlen(s);if(n>=sizeof c.path){(*env)->ReleaseStringUTFChars(env,path,s);return JNI_FALSE;}
        SDL_strlcpy(c.path,s,sizeof c.path);(*env)->ReleaseStringUTFChars(env,path,s);
    }
    return enqueue(&c)?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT void JNICALL Java_org_mushroomrecompiled_NativeBridge_setTouch(JNIEnv *e,jclass c,jint mask) {(void)e;(void)c;SDL_AtomicSet(&touch,mask&255);}
JNIEXPORT jint JNICALL Java_org_mushroomrecompiled_NativeBridge_touchMask(JNIEnv *e,jclass c) {(void)e;(void)c;return SDL_AtomicGet(&touch);}
JNIEXPORT void JNICALL Java_org_mushroomrecompiled_NativeBridge_setControlGutter(JNIEnv *e,jclass c,jint percent) {(void)e;(void)c;SDL_AtomicSet(&control_gutter,SDL_clamp(percent,0,25));}
JNIEXPORT void JNICALL Java_org_mushroomrecompiled_NativeBridge_setPaused(JNIEnv *e,jclass c,jboolean paused) {(void)e;(void)c;SDL_AtomicSet(&menu_pause,paused);if(paused)SDL_AtomicSet(&touch,0);}
JNIEXPORT jint JNICALL Java_org_mushroomrecompiled_NativeBridge_state(JNIEnv *e,jclass c) {(void)e;(void)c;return SDL_AtomicGet(&flags);}
JNIEXPORT jstring JNICALL Java_org_mushroomrecompiled_NativeBridge_result(JNIEnv *env,jclass cls) {
    (void)cls;char message[512]="";SDL_AtomicLock(&bridge_lock);
    if(feedback_read!=feedback_write)SDL_strlcpy(message,feedback[feedback_read++%8],sizeof message);
    SDL_AtomicUnlock(&bridge_lock);return (*env)->NewStringUTF(env,message);
}
JNIEXPORT jstring JNICALL Java_org_mushroomrecompiled_NativeBridge_diagnostics(JNIEnv *env,jclass cls) {
    (void)cls;char out[512];SDL_AtomicLock(&bridge_lock);SDL_strlcpy(out,diagnostics,sizeof out);SDL_AtomicUnlock(&bridge_lock);return (*env)->NewStringUTF(env,out);
}
JNIEXPORT jstring JNICALL Java_org_mushroomrecompiled_NativeBridge_controllers(JNIEnv *env,jclass cls) {
    (void)cls;char *list=SDL_malloc(sizeof controller_list);if(!list)return NULL;
    SDL_AtomicLock(&bridge_lock);SDL_strlcpy(list,controller_list,sizeof controller_list);SDL_AtomicUnlock(&bridge_lock);
    char *utf16=SDL_iconv_string("UTF-16LE","UTF-8",list,strlen(list)+1);SDL_free(list);
    if(!utf16)return (*env)->NewStringUTF(env,"");
    size_t n=0;while(((Uint16 *)utf16)[n])n++;
    jstring result=(*env)->NewString(env,(jchar *)utf16,(jsize)n);SDL_free(utf16);return result;
}
static void update_controller_list(void) {
    if(!controller_list_dirty)return;
    InputDevice devices[32];int n=input_devices(devices,32);char list[49152]="";
    for(int i=0;i<n;i++) {
        if(strchr(devices[i].key,'\t')||strchr(devices[i].key,'\n'))continue;
        char *name=SDL_iconv_string("UTF-8","WCHAR_T",(const char *)devices[i].name,(wcslen(devices[i].name)+1)*sizeof(wchar_t));
        if(name)for(char *p=name;*p;p++)if(*p=='\t'||*p=='\n'||*p=='\r')*p=' ';
        SDL_strlcat(list,devices[i].key,sizeof list);SDL_strlcat(list,"\t",sizeof list);
        SDL_strlcat(list,name?name:"Controller",sizeof list);SDL_strlcat(list,"\n",sizeof list);SDL_free(name);
    }
    SDL_AtomicLock(&bridge_lock);SDL_strlcpy(controller_list,list,sizeof controller_list);SDL_AtomicUnlock(&bridge_lock);controller_list_dirty=0;
}
JNIEXPORT void JNICALL Java_org_mushroomrecompiled_NativeBridge_configure(JNIEnv *env,jclass cls,jintArray options,jintArray bindings,jint dz,jstring device) {
    (void)cls;if(!options||!bindings||(*env)->GetArrayLength(env,options)!=10||(*env)->GetArrayLength(env,bindings)!=11)return;
    Command c={0};c.operation=CONFIG;c.deadzone=dz;
    (*env)->GetIntArrayRegion(env,options,0,10,c.settings);(*env)->GetIntArrayRegion(env,bindings,0,11,c.bindings);
    if(device) {const char *s=(*env)->GetStringUTFChars(env,device,NULL);if(s){SDL_strlcpy(c.path,s,sizeof c.path);(*env)->ReleaseStringUTFChars(env,device,s);}}
    if(!enqueue(&c))report("Demasiadas operaciones pendientes; vuelve a aplicar la configuración.");
}
JNIEXPORT void JNICALL Java_org_mushroomrecompiled_NativeBridge_safeArea(JNIEnv *e,jclass c,jint l,jint t,jint r,jint b,jint w,jint h) {
    (void)e;(void)c;SDL_AtomicLock(&bridge_lock);safe[0]=l;safe[1]=t;safe[2]=r;safe[3]=b;safe[4]=w;safe[5]=h;SDL_AtomicUnlock(&bridge_lock);
}
static void audio_pause(void) {
    if(audio){SDL_PauseAudioDevice(audio,1);SDL_ClearQueuedAudio(audio);}
    priming=1;
}
static void audio_open(void) {
    if(audio)SDL_CloseAudioDevice(audio);
    SDL_AudioSpec want={0},have={0};want.freq=settings[4];want.format=AUDIO_S16SYS;want.channels=settings[5]?2:1;want.samples=512;
    audio=SDL_OpenAudioDevice(NULL,0,&want,&have,SDL_AUDIO_ALLOW_SAMPLES_CHANGE);
    if(!audio){report("No se pudo abrir el audio SDL. Revisa la salida de audio y vuelve a aplicar los ajustes.");return;}
    audio_channels=have.channels;glue_set_audio_sample_rate((uint32_t)have.freq);audio_pause();
}
static void reset_texture(void) {if(texture)SDL_DestroyTexture(texture);texture=NULL;texture_width=0;}
static void configure(const Command *c) {
    int old_rate=settings[4],old_stereo=settings[5];memcpy(settings,c->settings,sizeof settings);
    settings[0]=settings[0]==1;settings[1]=SDL_clamp(settings[1],0,2);settings[2]=settings[2]!=0;
    settings[3]=SDL_clamp(settings[3],0,100);
    if(settings[4]!=44100&&settings[4]!=48000&&settings[4]!=96000)settings[4]=48000;
    settings[5]=settings[5]!=0;settings[6]=SDL_clamp(settings[6],20,80);settings[7]=SDL_clamp(settings[7],50,150);settings[8]=SDL_clamp(settings[8],0,75);
    glue_set_widescreen(settings[0]);input_configure(c->bindings);input_set_deadzone(c->deadzone);
    if(strcmp(input_preferred(),c->path))input_select(c->path);
    reset_texture();if(!audio||old_rate!=settings[4]||old_stereo!=settings[5])audio_open();else audio_pause();
}
static int process_commands(void) {
    Command c;int changed=0;
    while(dequeue(&c)) {
        changed=1;
        switch(c.operation) {
        case LOAD_ROM:
            audio_pause();SDL_AtomicSet(&touch,0);glue_set_pad1(0);
            if(glue_load_rom(c.path)) {report("ROM cargada: núcleo recompilado listo.");reset_texture();}
            else report("No se pudo cargar la ROM. Selecciona la copia exacta compatible.");
            break;
        case RESET: glue_reset();audio_pause();report("Juego reiniciado.");break;
        case SAVE: {
            char temp[4120];SDL_snprintf(temp,sizeof temp,"%s.tmp",c.path);
            int ok=c.slot>=1&&c.slot<=5&&glue_save_state_utf8(temp);
            if(ok)ok=rename(temp,c.path)==0;
            if(!ok)unlink(temp);
            report(ok?"Estado guardado.":"No se pudo guardar el estado.");break;
        }
        case LOAD_STATE:
            audio_pause();SDL_AtomicSet(&touch,0);
            report(c.slot>=1&&c.slot<=5&&glue_load_state_utf8(c.path)?"Estado cargado.":"No se pudo cargar este slot.");break;
        case QUIT:quit_requested=1;break;
        case CONFIG:configure(&c);break;
        case CAPTURE:capture_action=c.slot>=0&&c.slot<11?c.slot:-1;capture_armed=0;break;
        }
    }
    return changed;
}
static uint8_t pad_from_input(InputState s) {
    uint8_t p=0;
    if(s.pad&INPUT_UP)p|=NES_PAD_UP;if(s.pad&INPUT_DOWN)p|=NES_PAD_DOWN;
    if(s.pad&INPUT_LEFT)p|=NES_PAD_LEFT;if(s.pad&INPUT_RIGHT)p|=NES_PAD_RIGHT;
    if(s.pad&INPUT_A)p|=NES_PAD_A;if(s.pad&INPUT_B)p|=NES_PAD_B;
    if(s.pad&INPUT_START)p|=NES_PAD_START;if(s.pad&INPUT_SELECT)p|=NES_PAD_SELECT;
    if((glue_frame_count()/3)&1){if(s.pad&INPUT_TURBO_A)p|=NES_PAD_A;if(s.pad&INPUT_TURBO_B)p|=NES_PAD_B;}
    const Uint8 *keys=SDL_GetKeyboardState(NULL);
    if(keys[SDL_SCANCODE_UP])p|=NES_PAD_UP;if(keys[SDL_SCANCODE_DOWN])p|=NES_PAD_DOWN;
    if(keys[SDL_SCANCODE_LEFT])p|=NES_PAD_LEFT;if(keys[SDL_SCANCODE_RIGHT])p|=NES_PAD_RIGHT;
    if(keys[SDL_SCANCODE_X])p|=NES_PAD_A;if(keys[SDL_SCANCODE_Z])p|=NES_PAD_B;
    if(keys[SDL_SCANCODE_RETURN])p|=NES_PAD_START;if(keys[SDL_SCANCODE_RSHIFT]||keys[SDL_SCANCODE_LSHIFT])p|=NES_PAD_SELECT;
    return p|(uint8_t)SDL_AtomicGet(&touch);
}
static void submit_audio(void) {
    int16_t samples[4096];size_t n=glue_get_audio_samples(samples,4096,settings[5]);
    if(!audio)return;
    if(audio_channels==1){for(size_t i=0;i<n/2;i++)samples[i]=(int16_t)(((int)samples[2*i]+samples[2*i+1])/2);n/=2;}
    int volume=settings[9]?0:settings[3];
    for(size_t i=0;i<n;i++)samples[i]=(int16_t)((int)samples[i]*volume/100);
    Uint32 queued=SDL_GetQueuedAudioSize(audio);
    if(!priming&&!queued)underruns++;
    if(queued>(Uint32)(settings[4]*audio_channels*2/4)){overruns++;audio_pause();}
    if(SDL_QueueAudio(audio,samples,(Uint32)(n*sizeof *samples))<0){report("Error al enviar audio a SDL.");return;}
    if(priming && SDL_GetQueuedAudioSize(audio)>=(Uint32)(settings[4]*audio_channels*2*settings[6]/1000)){SDL_PauseAudioDevice(audio,0);priming=0;}
}
static void present(void) {
    int w,h;const uint32_t *pixels=glue_get_present_framebuffer(&w,&h);
    if(!texture||texture_width!=w) {
        reset_texture();SDL_SetHint(SDL_HINT_RENDER_SCALE_QUALITY,settings[2]?"linear":"nearest");
        texture=SDL_CreateTexture(renderer,SDL_PIXELFORMAT_ARGB8888,SDL_TEXTUREACCESS_STREAMING,w,h);texture_width=w;
        if(!texture){report(SDL_GetError());quit_requested=1;return;}
    }
    if(SDL_UpdateTexture(texture,NULL,pixels,w*4)<0){report(SDL_GetError());return;}
    int rw,rh,insets[6];SDL_GetRendererOutputSize(renderer,&rw,&rh);
    SDL_AtomicLock(&bridge_lock);memcpy(insets,safe,sizeof safe);SDL_AtomicUnlock(&bridge_lock);
    int l=0,t=0,r=0,b=0;
    if(insets[4]>0&&insets[5]>0){l=insets[0]*rw/insets[4];r=insets[2]*rw/insets[4];t=insets[1]*rh/insets[5];b=insets[3]*rh/insets[5];}
    b+=(rh-t-b)*SDL_AtomicGet(&control_gutter)/100;
    AndroidRect fit=android_video_rect(rw-l-r,rh-t-b,w,settings[0],settings[1]);
    SDL_Rect dst={fit.x+l,fit.y+t,fit.w,fit.h};
    SDL_SetRenderDrawColor(renderer,0,0,0,255);SDL_RenderClear(renderer);
    int bright=SDL_min(settings[7],100)*255/100;SDL_SetTextureColorMod(texture,bright,bright,bright);
    SDL_RenderCopy(renderer,texture,NULL,&dst);
    if(settings[7]>100){SDL_SetTextureBlendMode(texture,SDL_BLENDMODE_ADD);int extra=(settings[7]-100)*255/100;SDL_SetTextureColorMod(texture,extra,extra,extra);SDL_RenderCopy(renderer,texture,NULL,&dst);SDL_SetTextureBlendMode(texture,SDL_BLENDMODE_NONE);}
    if(settings[8]){SDL_SetRenderDrawBlendMode(renderer,SDL_BLENDMODE_BLEND);SDL_SetRenderDrawColor(renderer,0,0,0,settings[8]*255/100);for(int row=1;row<240;row+=2){int y=dst.y+row*dst.h/240;SDL_RenderDrawLine(renderer,dst.x,y,dst.x+dst.w-1,y);}}
    SDL_RenderPresent(renderer);
}
int main(int argc,char **argv) {
    (void)argc;(void)argv;
    /* Activity recreation can reuse this process and its loaded libraries. */
    quit_requested=0;capture_action=-1;capture_armed=0;underruns=overruns=0;
    controller_list_dirty=1;audio=0;window=NULL;renderer=NULL;texture=NULL;texture_width=0;
    glue_unload_rom();cyc_run_native=true;SDL_SetMainReady();
    SDL_SetHint(SDL_HINT_RENDER_DRIVER,"opengles2");SDL_SetHint(SDL_HINT_ANDROID_TRAP_BACK_BUTTON,"1");
    SDL_SetHint(SDL_HINT_ANDROID_BLOCK_ON_PAUSE,"0");SDL_SetHint(SDL_HINT_ANDROID_BLOCK_ON_PAUSE_PAUSEAUDIO,"1");
    if(SDL_Init(SDL_INIT_VIDEO|SDL_INIT_AUDIO|SDL_INIT_GAMECONTROLLER|SDL_INIT_EVENTS)<0){report(SDL_GetError());stop_session();SDL_Quit();return 1;}
    window=SDL_CreateWindow("Mushroom Recompiled",SDL_WINDOWPOS_CENTERED,SDL_WINDOWPOS_CENTERED,1280,720,SDL_WINDOW_FULLSCREEN_DESKTOP|SDL_WINDOW_ALLOW_HIGHDPI);
    renderer=window?SDL_CreateRenderer(window,-1,SDL_RENDERER_ACCELERATED):NULL;
    if(!renderer){report(SDL_GetError());stop_session();SDL_Quit();return 2;}
    SDL_SetRenderDrawColor(renderer,0,0,0,255);SDL_RenderClear(renderer);SDL_RenderPresent(renderer);
    input_init();SDL_GameControllerAddMappingsFromFile("gamecontrollerdb.txt");audio_open();SDL_AtomicSet(&flags,1);
    int background=0,previous_paused=1,previous_menu=0;
    double frequency=(double)SDL_GetPerformanceFrequency(),deadline=SDL_GetPerformanceCounter()/frequency;
    double stats_time=deadline;unsigned stats_frames=0,stats_draws=0;
    while(!quit_requested) {
        SDL_Event event;
        while(SDL_PollEvent(&event)) {
            if(event.type==SDL_QUIT)quit_requested=1;
            if(event.type==SDL_APP_WILLENTERBACKGROUND||event.type==SDL_APP_DIDENTERBACKGROUND||
                (event.type==SDL_WINDOWEVENT&&event.window.event==SDL_WINDOWEVENT_FOCUS_LOST)){background=1;SDL_AtomicSet(&touch,0);glue_set_pad1(0);audio_pause();}
            if(event.type==SDL_APP_DIDENTERFOREGROUND||(event.type==SDL_WINDOWEVENT&&event.window.event==SDL_WINDOWEVENT_FOCUS_GAINED))background=0;
            if(event.type==SDL_RENDER_DEVICE_RESET||event.type==SDL_RENDER_TARGETS_RESET)reset_texture();
            if(event.type==SDL_JOYDEVICEADDED||event.type==SDL_JOYDEVICEREMOVED)controller_list_dirty=1;
            if(event.type==SDL_KEYDOWN&&!event.key.repeat&&(event.key.keysym.sym==SDLK_AC_BACK||event.key.keysym.sym==SDLK_ESCAPE)){SDL_AtomicSet(&menu_pause,1);SDL_AtomicSet(&touch,0);report("@menu");}
        }
        int changed=process_commands();input_refresh();update_controller_list();InputState s=input_read();
        if(capture_action>=0){int button=input_first_pressed();if(button<0)capture_armed=1;else if(capture_armed){char message[64];SDL_snprintf(message,sizeof message,"@binding:%d:%d",capture_action,button);report(message);capture_action=-1;}}
        else if(s.menu&&!previous_menu){SDL_AtomicSet(&menu_pause,1);SDL_AtomicSet(&touch,0);report("@menu");}previous_menu=s.menu;
        SDL_AtomicSet(&flags,1|(glue_is_rom_loaded()?2:0)|(s.connected?4:0));
        int paused=background||SDL_AtomicGet(&menu_pause)||!glue_is_rom_loaded();
        double now=SDL_GetPerformanceCounter()/frequency;
        if(paused!=previous_paused||changed){audio_pause();glue_set_pad1(0);deadline=now;stats_time=now;stats_frames=0;stats_draws=0;previous_paused=paused;}
        if(paused){SDL_Delay(10);continue;}
        double period=1.0/glue_frame_rate();
        if(now<deadline){double remaining=deadline-now;SDL_Delay(remaining>0.002?(Uint32)(remaining*1000)-1:1);continue;}
        /* Original NTSC cadence; no game-speed adjustment to compensate audio. */
        if(now-deadline>0.25)deadline=now;
        /* A 60 Hz compositor must not slow the 60.0988 Hz NES clock or drain
         * its audio. Catch up at most three complete machine frames and only
         * present the latest. Every frame still produces its original audio. */
        unsigned caught_up=0;
        do {
            glue_set_pad1(pad_from_input(s));glue_step_frame();submit_audio();stats_frames++;deadline+=period;
            now=SDL_GetPerformanceCounter()/frequency;
        }while(now>=deadline&&++caught_up<3&&!SDL_AtomicGet(&menu_pause));
        present();stats_draws++;now=SDL_GetPerformanceCounter()/frequency;
        if(now-stats_time>=1.0){
            SDL_AtomicLock(&bridge_lock);
            SDL_snprintf(diagnostics,sizeof diagnostics,"%.2f FPS game / %.2f FPS presented | frame %llu | native %llu | fallback ROM %llu / RAM %llu | audio underruns %u, recoveries %u",stats_frames/(now-stats_time),stats_draws/(now-stats_time),(unsigned long long)glue_frame_count(),(unsigned long long)cyc_run_native_cycles,(unsigned long long)cyc_run_interp_rom_cycles,(unsigned long long)cyc_run_interp_ram_cycles,underruns,overruns);
            SDL_AtomicUnlock(&bridge_lock);stats_frames=0;stats_draws=0;stats_time=now;
        }
    }
    stop_session();audio_pause();if(audio)SDL_CloseAudioDevice(audio);audio=0;input_close();reset_texture();SDL_DestroyRenderer(renderer);renderer=NULL;SDL_DestroyWindow(window);window=NULL;SDL_Quit();return 0;
}
