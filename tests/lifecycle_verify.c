#include "glue.h"
#include "cyc_core.h"
#include <assert.h>
#include <stdio.h>
int main(int argc,char **argv){
    assert(argc==2);uint64_t reference=0;
    for(int session=0;session<3;session++){
        assert(glue_load_rom(argv[1]));assert(glue_is_rom_loaded());assert(glue_frame_count()==0);
        for(int i=0;i<120;i++){glue_set_pad1(0);glue_step_frame();int16_t audio[4096];glue_get_audio_samples(audio,4096,1);}
        uint64_t state=cyc_mem_state_hash();if(session)assert(state==reference);reference=state;
        glue_set_pad1(NES_PAD_A|NES_PAD_B);glue_unload_rom();
        assert(!glue_is_rom_loaded()&&!glue_get_rom_crc32()&&!glue_frame_count()&&!glue_get_pad1());
        assert(!glue_save_state_utf8("unloaded-must-not-save.state"));
    }
    puts("Three native load/unload/reload sessions: deterministic boot, released input and no cached ROM authorization passed.");
}
