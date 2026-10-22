package org.mushroomrecompiled;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

final class RomValidator {
    static final int SIZE=393232;
    static final String SHA256="4377a7f5e6eb50bdd2ac6f249bf1a7085500aca8eb41f38545c3a2731c51a579";
    private RomValidator() {}
    static byte[] read(InputStream stream) throws IOException {
        if(stream==null)throw new IOException("No se puede abrir la ROM. Selecciónala de nuevo.");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(SIZE);
        byte[] chunk=new byte[8192];int n;
        while((n=stream.read(chunk))!=-1) {
            if(bytes.size()+n>SIZE)throw new IOException("La ROM debe tener exactamente 393232 bytes.");
            bytes.write(chunk,0,n);
        }
        byte[] rom=bytes.toByteArray();validate(rom);return rom;
    }
    static void validate(byte[] rom) throws IOException {
        if(rom.length!=SIZE)throw new IOException("La ROM debe tener exactamente 393232 bytes.");
        if(rom[0]!='N'||rom[1]!='E'||rom[2]!='S'||rom[3]!=26||rom[4]!=16||rom[5]!=16||
            (rom[6]&4)!=0||(((rom[6]&255)>>>4)|(rom[7]&240))!=4)throw new IOException("Se necesita la ROM iNES MMC3 USA Rev 1 sin modificar.");
        CRC32 crc=new CRC32();crc.update(rom,16,rom.length-16);
        if(crc.getValue()!=0x2e6301edL)throw new IOException("CRC32 incompatible. Se esperaba 2E6301ED.");
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(rom);
            StringBuilder hex=new StringBuilder();
            for(byte b:hash)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!SHA256.contentEquals(hex))throw new IOException("SHA-256 incompatible. Usa Super Mario Bros. 3 (USA) (Rev 1).nes de la compilación.");
        }catch(NoSuchAlgorithmException e){throw new IOException("SHA-256 no disponible",e);}
    }
}
