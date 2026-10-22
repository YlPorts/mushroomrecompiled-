#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifndef _WIN32
#include <unistd.h>
#endif
#include "glue.h"
#include "cyc_core.h"
#include "cyc_run.h"
#include "cyc_state.h"
#include "widescreen.h"
#include "wide_enemies.h"
#include "rom_validation.h"
static bool widescreen;

static bool loaded;
static uint64_t frames;
static uint8_t pad1;
static int sample_rate = 48000;
static uint32_t rom_crc;
static FILE *open_wide(const wchar_t *path, bool write) {
    if (!path) return NULL;
#ifdef _WIN32
    return _wfopen(path,write?L"wb":L"rb");
#else
    /* Only used by the existing host verifiers; Android uses UTF-8 directly. */
    size_t n=wcstombs(NULL,path,0);
    if (n==(size_t)-1) return NULL;
    char *utf8=malloc(n+1); if (!utf8) return NULL;
    wcstombs(utf8,path,n+1);
    FILE *f=fopen(utf8,write?"wb":"rb"); free(utf8); return f;
#endif
}
static size_t save_host(uint8_t *out, size_t cap) {
    if (out && cap >= sizeof(frames)) memcpy(out, &frames, sizeof(frames));
    return sizeof(frames);
}
static bool load_host(const uint8_t *in, size_t n, bool apply) {
    if (n != sizeof(frames)) return false;
    if (apply) memcpy(&frames, in, n);
    return true;
}
static bool load_file(FILE *f) {
    if (!f) return false;
    if (fseek(f,0,SEEK_END)) { fclose(f); return false; }
    long n=ftell(f);
    if (n != 393232 || fseek(f,0,SEEK_SET)) { fclose(f); return false; }
    uint8_t *image=malloc((size_t)n);
    if (!image) { fclose(f); return false; }
    bool ok=fread(image,1,(size_t)n,f)==(size_t)n;
    fclose(f);
    ok=ok && smb3_rom_validate(image,(size_t)n) && smb3_wide_load(image,(size_t)n) && cyc_load_ines(image,(size_t)n);
    free(image);
    if (!ok) return false;
    loaded=true; rom_crc=SMB3_ROM_CRC32;
    static const CycStateHost host={save_host,load_host};
    cyc_state_set_host(&host);
    glue_reset();
    return true;
}
bool glue_load_rom(const char *p) { return p && load_file(fopen(p,"rb")); }
bool glue_load_rom_wide(const wchar_t *p) { return p && load_file(open_wide(p,false)); }
bool glue_is_rom_loaded(void) { return loaded; }
void glue_unload_rom(void) {
    loaded=false;frames=0;rom_crc=0;pad1=0;
    cyc_set_controller(0,0);cyc_set_controller(1,0);
    cyc_run_observer=NULL;smb3_wide_reset();smb3_enemies_reset();
    /* cyc_load_ines releases/replaces the previous cartridge allocation on
     * the next load. Never expose that retained memory as an approved ROM. */
}
uint32_t glue_get_rom_crc32(void) { return rom_crc; }
void glue_init(void) { glue_reset(); }
void glue_reset(void) {
    if (!loaded) return;
    cyc_set_region(CYC_REGION_NTSC);
    cyc_power_on(0); cyc_run_power_on(); cyc_audio_enable(sample_rate);
    frames=0; pad1=0; cyc_set_controller(0,0); cyc_set_controller(1,0);
    smb3_wide_reset();
    smb3_enemies_reset();
    hw_observe_line=widescreen?8:-1;
    cyc_run_observer=widescreen?smb3_wide_capture:NULL;
}
void glue_step_frame(void) { if (loaded) { smb3_enemies_tick();cyc_run_frame(); frames++; } }
void glue_set_pad1(uint8_t p) { pad1=p; cyc_set_controller(0,p); }
void glue_set_pad2(uint8_t p) { cyc_set_controller(1,p); }
uint8_t glue_get_pad1(void) { return pad1; }
const uint32_t *glue_get_framebuffer(void) { return cyc_frame_argb(); }
void glue_set_widescreen(bool enabled) {
    if(widescreen!=enabled)smb3_wide_reset();
    widescreen=enabled;smb3_enemies_enable(enabled);
    hw_observe_line=enabled?8:-1;
    cyc_run_observer=enabled?smb3_wide_capture:NULL;
}
const uint32_t *glue_get_present_framebuffer(int *width,int *height) {
    *width=widescreen?SMB3_WIDE_WIDTH:256;*height=240;
    return widescreen?smb3_wide_present(cyc_frame_argb()):cyc_frame_argb();
}
size_t glue_get_audio_samples(int16_t *out,size_t capacity,int stereo) {
    (void)stereo;
    if (!loaded || !out) return 0;
    int16_t mono[2048];
    size_t n=cyc_audio_read(mono,capacity/2 < 2048 ? capacity/2 : 2048);
    for (size_t i=0;i<n;i++) out[i*2]=out[i*2+1]=mono[i];
    return n*2;
}
void glue_set_audio_sample_rate(uint32_t rate) {
    if (rate!=44100 && rate!=48000 && rate!=96000) return;
    sample_rate=(int)rate;
    if (loaded) cyc_audio_enable(sample_rate);
}
uint32_t glue_audio_sample_rate(void) { return (uint32_t)sample_rate; }
double glue_frame_rate(void) { return 1.0/cyc_frame_seconds(); }
uint64_t glue_frame_count(void) { return frames; }
static bool save_state_file(FILE *f) {
    if (!f) return false;
    if (!loaded) { fclose(f); return false; }
    uint8_t *data=NULL; size_t len=0; char error[256];
    if (!cyc_state_save(&data,&len,error,sizeof error)) { fclose(f); return false; }
    bool ok=f && fwrite(data,1,len,f)==len;
    if (ok && fflush(f)) ok=false;
#ifndef _WIN32
    if (ok && fsync(fileno(f))) ok=false;
#endif
    if (f && fclose(f)) ok=false;
    free(data); return ok;
}
static bool load_state_file(FILE *f) {
    if (!f) return false;
    if (!loaded) { fclose(f); return false; }
    if (fseek(f,0,SEEK_END)) { fclose(f); return false; }
    long n=ftell(f);
    if (n<=0 || n>64*1024*1024 || fseek(f,0,SEEK_SET)) { fclose(f); return false; }
    uint8_t *data=malloc((size_t)n);
    if (!data) { fclose(f); return false; }
    bool ok=fread(data,1,(size_t)n,f)==(size_t)n; fclose(f);
    char error[256];
    if (ok) ok=cyc_state_load(data,(size_t)n,error,sizeof error);
    if (ok) {smb3_wide_reset();smb3_enemies_reset();glue_set_pad1(0);glue_set_pad2(0);cyc_audio_enable(sample_rate);}
    free(data); return ok;
}
bool glue_save_state(const wchar_t *path) { return loaded && path && save_state_file(open_wide(path,true)); }
bool glue_load_state(const wchar_t *path) { return loaded && path && load_state_file(open_wide(path,false)); }
bool glue_save_state_utf8(const char *path) { return loaded && path && save_state_file(fopen(path,"wb")); }
bool glue_load_state_utf8(const char *path) { return loaded && path && load_state_file(fopen(path,"rb")); }
