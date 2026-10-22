import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export function inspectRom(data) {
  if (data.length < 16 || !data.subarray(0, 4).equals(Buffer.from([0x4e, 0x45, 0x53, 0x1a]))) throw Error('Cabeçalho inválido');
  const nes2 = (data[7] & 12) === 8;
  if (nes2 && (data[8] || data[9] || data[10] || data[11] || data[12] || data[13] || data[14] || data[15])) throw Error('NES 2.0 com extensões não suportadas');
  const mapper = (data[6] >> 4) | (data[7] & 0xf0);
  if (mapper !== 4) throw Error('Mapper MMC3 esperado');
  const prgOffset = 16 + ((data[6] & 4) ? 512 : 0);
  const prgSize = data[4] * 16384, chrSize = data[5] * 8192;
  const chrOffset = prgOffset + prgSize;
  if (prgSize < 8192 || data.length !== chrOffset + chrSize) throw Error('Tamanho incompatível com cabeçalho');
  const banks = Array.from({ length: prgSize / 8192 }, (_, i) => ({ index: i, fileOffset: prgOffset + i * 8192, size: 8192 }));
  return { format: nes2 ? 'NES 2.0' : 'iNES', mapper, prgOffset, prgSize, chrOffset, chrSize, banks,
    vectors: { nmi: data.readUInt16LE(chrOffset - 6), reset: data.readUInt16LE(chrOffset - 4), irq: data.readUInt16LE(chrOffset - 2) } };
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (!process.argv[2]) throw Error('Uso: node tools/rom.mjs <ROM.nes>');
  console.log(JSON.stringify(inspectRom(fs.readFileSync(process.argv[2])), null, 2));
}
