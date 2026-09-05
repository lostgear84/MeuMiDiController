Estado final validado
O Android detecta o Roland JUNO-D via USB‑MIDI.

O app abre a porta MIDI e se recupera de desconexões como EPIPE.

Cada Música aciona sua USER Scene pelo Bank Select + Program Change.

Cada Preset controla individualmente o estado ON/OFF das oito Parts.

O mapeamento validado usa o bloco de Zone da Temporary Scene:

text
Part 1 → 01 00 21 00
Part 2 → 01 00 22 00
Part 3 → 01 00 23 00
Part 4 → 01 00 24 00
Part 5 → 01 00 25 00
Part 6 → 01 00 26 00
Part 7 → 01 00 27 00
Part 8 → 01 00 28 00
Os estados são enviados em Roland SysEx DT1, com Model ID 01 05 0A, Device ID 17 e checksum calculado.

O monitor de retorno MIDI continua guardado no código, mas comentado, para facilitar debug futuro.

Soft Thru e USB MIDI Thru permanecem OFF; não são necessários para essa topologia direta celular → JUNO.
