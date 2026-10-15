# VEON code provenance (v0.3.0)

## Scope and source

This is **one community profile**, `Veon_2445 / xm_1_4375`, not a claim that
all VEON TVs or Walton WRC-03 sets share these codes. No physical TV was available
for validation. `verified` remains false.

Reference: [Veon_2445_xm_1_4375.ir at revision 627d75a](https://github.com/adnansiddiq/mi_ir_infinite/blob/627d75a1f4681a0bd8ea74b01e3a7140fcd5126b/Codes/1_Veon_2445/Veon_2445_xm_1_4375.ir).
The reference credits [Ysard's Mi Remote database](https://github.com/ysard/mi_remote_database),
Copyright (C) 2021–2023 Ysard, AGPL-3.0. This repository does not redistribute its raw
capture file or conversion software; the table below documents the decoded IR
address/command facts used by our existing NEC encoder. Preserve this attribution
when updating the profile.

## Decoding

For each reference record, take the first NEC frame: leader, then 32 mark/space
pairs. Decode a space over 1,000 µs as 1, otherwise 0; assemble each byte **LSB-first**.
Every referenced frame has address bytes `08 F7`, with inverse command bytes.
Thus the profile uses **STANDARD address `0x08`**, NOT extended address `0x08F7`.
For example, power is `08 F7 D7 28` on the wire.

The capture reports 38,095 Hz; we use the existing nominal 38,000 Hz NEC carrier
(0.25% difference) and standard NEC timings, not raw waveform replay. The capture's
trailing repeat frames are not part of its 32-bit payload.

| App command | Reference label | NEC command |
| --- | --- | --- |
| POWER | power | D7 |
| MUTE | mute | DF |
| SOURCE | tv_av | C3 |
| UP / DOWN | up / down | 92 / D8 |
| LEFT / RIGHT | left / right | 97 / 9F |
| OK | ok (also enter) | 9B |
| VOLUME_UP / VOLUME_DOWN | vol+ / vol- | 83 / 86 |
| CHANNEL_UP / CHANNEL_DOWN | ch+ / ch- | 80 / 8E |
| DIGIT_0 | 0 | D9 |
| DIGIT_1 / DIGIT_2 / DIGIT_3 | 1 / 2 / 3 | 95 / 99 / 9D |
| DIGIT_4 / DIGIT_5 / DIGIT_6 | 4 / 5 / 6 | D6 / DA / DE |
| DIGIT_7 / DIGIT_8 / DIGIT_9 | 7 / 8 / 9 | 96 / 9A / 9E |
| PLAY_PAUSE | play | 8F |
| STOP | stop | CA |
| REWIND / FAST_FORWARD | REW / FF | 8B / 93 |

`DOCUMENTED` means present in that source, **not hardware-tested**. In particular:

- `play` is documented; whether it toggles pause is unknown and stated in the UI.
- `previous` shares `DD` with `back`; its media meaning is ambiguous. PREVIOUS and
  NEXT are therefore **unmapped**, displayed with `—`, rather than assigned Walton
  placeholders. They can be mapped by the scanner.
- The source aliases REW/FF/play with green/blue/yellow. Their action may depend on
  the TV's current mode; confirm in the intended media mode.
- Menu/exit and other reference buttons outside the current app keypad are not
  silently remapped onto unrelated buttons.

## Scanner

Selecting VEON opens the dedicated VEON preset by default. It tries the selected
button's documented byte first, then other documented VEON bytes, then remaining
bytes of `00..FF`: **256 distinct frames** at address `08 F7`. If that address does
not work, use the profile/common-address presets. This is transmit-and-observe,
not receiving/learning IR and not automatic TV detection. The phone needs an IR
emitter, and the user must confirm the **intended action**, not just any response.
