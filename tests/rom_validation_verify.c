#include "rom_validation.h"
#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
static void verify(const char *s,const char *expected) {
    uint8_t h[32]; char hex[65];smb3_sha256((const uint8_t *)s,strlen(s),h);
    for(int i=0;i<32;i++)sprintf(hex+i*2,"%02x",h[i]);
    assert(!strcmp(hex,expected));
}
int main(void) {
    verify("","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    verify("abc","ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    verify("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq","248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1");
    assert(smb3_crc32((const uint8_t *)"123456789",9)==0xcbf43926);
    assert(!smb3_rom_validate(NULL,0));
    uint8_t *fake=calloc(1,SMB3_ROM_SIZE+1);assert(fake);
    memcpy(fake,"NES\032\020\020\100",7);
    assert(!smb3_rom_validate(fake,SMB3_ROM_SIZE));
    assert(!smb3_rom_validate(fake,SMB3_ROM_SIZE-1));
    assert(!smb3_rom_validate(fake,SMB3_ROM_SIZE+1));free(fake);
    puts("SHA-256 vectors, CRC32 and invalid ROM rejection passed");
}
