#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Independent reference model for the 6122-family (NEC) IR frames this app transmits.

Why this file exists
--------------------
The Android app encodes NEC frames in Kotlin (``NecEncoder``). The numbers used by the
Kotlin unit tests (the "expected vectors") were not hand-typed: they were produced and
cross-checked with this standalone script, which implements the protocol from the
published NEC / uPD6122 timing spec:

  * 38 kHz carrier, pulse-distance encoded bits
  * 9 ms leading burst + 4.5 ms space (leader)
  * every bit = 562.5 us burst, followed by a 562.5 us space (logical 0)
    or a ~1.69 ms space (logical 1)
  * 32 bits, each byte transmitted least-significant-bit first
  * byte order: custom/address code (16 bit) then data code (16 bit)
  * repeat frame: 9 ms burst + 2.25 ms space + 562.5 us burst, every 108 ms

Running it has no dependencies and no Kotlin/Android toolchain:

    python3 tools/verify_nec_reference.py            # verify + decode round trip
    python3 tools/verify_nec_reference.py --emit     # print Kotlin fixtures

Exit code 0 = every vector is self-consistent (encode == expected and
decode(expected) == original address/command). The CI workflow runs it too, so a
mistake in the committed expected vectors fails the build.
"""

from __future__ import annotations

import argparse
import sys

# --- NEC (6122 family) timing constants, in microseconds ---------------------
CARRIER_HZ = 38_000
LEADER_MARK_US = 9_000
LEADER_SPACE_US = 4_500
BIT_MARK_US = 560
ZERO_SPACE_US = 560
ONE_SPACE_US = 1_690
TRAILING_MARK_US = 560
REPEAT_SPACE_US = 2_250
REPEAT_PERIOD_US = 108_000

STANDARD = "STANDARD"  # 8 bit address + inverted address + command + inverted command
EXTENDED = "EXTENDED"  # 16 bit address (low byte first) + command + inverted command


def _bits_of(byte: int):
    """8 bits of ``byte``, least significant bit first."""
    return [(byte >> i) & 1 for i in range(8)]


def frame_bytes(address: int, command: int, mode: str):
    if not 0 <= command <= 0xFF:
        raise ValueError(f"command must be 0x00..0xFF, got 0x{command:X}")
    if mode == STANDARD:
        if not 0 <= address <= 0xFF:
            raise ValueError(f"STANDARD address must be 0x00..0xFF, got 0x{address:X}")
        return address & 0xFF, (~address) & 0xFF, command & 0xFF, (~command) & 0xFF
    if mode == EXTENDED:
        if not 0 <= address <= 0xFFFF:
            raise ValueError(f"EXTENDED address must be 0x0000..0xFFFF, got 0x{address:X}")
        return address & 0xFF, (address >> 8) & 0xFF, command & 0xFF, (~command) & 0xFF
    raise ValueError(f"unknown address mode: {mode}")


def encode_frame(address: int, command: int, mode: str = STANDARD):
    """Encode one NEC frame as the alternating on/off pattern ConsumerIrManager expects."""
    pattern = [LEADER_MARK_US, LEADER_SPACE_US]
    for byte in frame_bytes(address, command, mode):
        for bit in _bits_of(byte):
            pattern.append(BIT_MARK_US)
            pattern.append(ONE_SPACE_US if bit else ZERO_SPACE_US)
    pattern.append(TRAILING_MARK_US)
    return pattern


def encode_repeat():
    """Encode the NEC repeat frame sent while a key is held down."""
    return [LEADER_MARK_US, REPEAT_SPACE_US, TRAILING_MARK_US]


def decode_frame(pattern):
    """Decode a pattern back to the four transmitted bytes, LSB-first, independently.

    Note: the wire format cannot tell STANDARD from EXTENDED apart -- an extended
    address of 0x00FF puts 0xFF,0x00 on the wire, which is exactly what a standard
    address of 0xFF sends. The mode is metadata supplied by the profile, so the
    decoder returns raw bytes and ``decode_address`` re-reads them per mode.
    """
    if len(pattern) != 67:
        raise ValueError(f"a NEC frame has 67 entries, got {len(pattern)}")
    if pattern[0] != LEADER_MARK_US or pattern[1] != LEADER_SPACE_US:
        raise ValueError("bad leader code")
    if pattern[-1] != TRAILING_MARK_US:
        raise ValueError("bad trailing burst")

    bits = []
    index = 2
    while index < len(pattern) - 1:
        mark, space = pattern[index], pattern[index + 1]
        if mark != BIT_MARK_US:
            raise ValueError(f"bad bit burst at index {index}: {mark}")
        if space == ZERO_SPACE_US:
            bits.append(0)
        elif space == ONE_SPACE_US:
            bits.append(1)
        else:
            raise ValueError(f"bad bit space at index {index + 1}: {space}")
        index += 2
    if len(bits) != 32:
        raise ValueError(f"expected 32 bits, decoded {len(bits)}")

    bytes_out = []
    for byte_index in range(4):
        value = 0
        for bit_index, bit in enumerate(bits[byte_index * 8 : byte_index * 8 + 8]):
            value |= bit << bit_index  # LSB first
        bytes_out.append(value)

    if bytes_out[2] ^ 0xFF != bytes_out[3]:
        raise ValueError("command byte and its inverse do not match")
    return tuple(bytes_out)


def decode_address(wire_bytes, mode):
    """Re-read the address from decoded wire bytes, given the profile's address mode."""
    b0, b1 = wire_bytes[0], wire_bytes[1]
    if mode == STANDARD:
        if b0 ^ 0xFF != b1:
            raise ValueError(f"STANDARD address byte {b0:#04x} is not followed by its inverse")
        return b0
    if mode == EXTENDED:
        return (b1 << 8) | b0
    raise ValueError(f"unknown address mode: {mode}")


def frame_duration_us(pattern):
    return sum(pattern)


# --- Expected vectors --------------------------------------------------------
# These are the fixtures asserted by the Kotlin unit tests (NecEncoderTest) and by
# this script. Keep the two in sync: if you change a constant above, regenerate
# with `python3 tools/verify_nec_reference.py --emit`.
VECTORS = [
        (
            "power_standard_addr00",
            0x0000,
            0x45,
            STANDARD,
            [
                9000, 4500,
                560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560,  # 0x00
                560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690,  # 0xFF
                560, 1690, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 1690, 560, 560,  # 0x45
                560, 560, 560, 1690, 560, 560, 560, 1690, 560, 1690, 560, 1690, 560, 560, 560, 1690,  # 0xBA
                560,
            ],
        ),
        (
            "digit_1_standard_addr00",
            0x0000,
            0x0C,
            STANDARD,
            [
                9000, 4500,
                560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560,  # 0x00
                560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690,  # 0xFF
                560, 560, 560, 560, 560, 1690, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560,  # 0x0C
                560, 1690, 560, 1690, 560, 560, 560, 560, 560, 1690, 560, 1690, 560, 1690, 560, 1690,  # 0xF3
                560,
            ],
        ),
        (
            "mute_extended_addr_00ff",
            0x00FF,
            0x47,
            EXTENDED,
            [
                9000, 4500,
                560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690,  # 0xFF
                560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560,  # 0x00
                560, 1690, 560, 1690, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 1690, 560, 560,  # 0x47
                560, 560, 560, 560, 560, 560, 560, 1690, 560, 1690, 560, 1690, 560, 560, 560, 1690,  # 0xB8
                560,
            ],
        ),
        (
            "volume_up_extended_addr_807f",
            0x807F,
            0x15,
            EXTENDED,
            [
                9000, 4500,
                560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 560,  # 0x7F
                560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 1690,  # 0x80
                560, 1690, 560, 560, 560, 1690, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560,  # 0x15
                560, 560, 560, 1690, 560, 560, 560, 1690, 560, 560, 560, 1690, 560, 1690, 560, 1690,  # 0xEA
                560,
            ],
        ),
]

REPEAT_VECTOR = [9000, 2250, 560]


def _kotlin_int_array(values, per_line=8, indent="            "):
    lines = []
    for start in range(0, len(values), per_line):
        chunk = ", ".join(str(v) for v in values[start : start + per_line])
        lines.append(f"{indent}{chunk},")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--emit",
        action="store_true",
        help="print the verified vectors as Kotlin intArrayOf(...) fixtures",
    )
    args = parser.parse_args()

    failures = []

    # 1. spec sanity
    if encode_repeat() != REPEAT_VECTOR:
        failures.append("repeat frame mismatch with REPEAT_VECTOR")
    worst_case = frame_duration_us(encode_frame(0xFFFF, 0xFF, EXTENDED))
    best_case = frame_duration_us(encode_frame(0x0000, 0x00, EXTENDED))
    if worst_case >= REPEAT_PERIOD_US:
        failures.append(
            f"longest frame ({worst_case}us) must stay below the {REPEAT_PERIOD_US}us repeat period"
        )

    # 2. every vector: encode == expected, decode(expected) == input
    for name, address, command, mode, expected in VECTORS:
        actual = encode_frame(address, command, mode)
        if actual != expected:
            failures.append(
                f"{name}: encoder output differs from the committed expected pattern\n"
                f"    expected: {expected}\n"
                f"    actual:   {actual}"
            )
            continue
        if len(expected) != 67:
            failures.append(f"{name}: expected pattern must have 67 entries")
            continue
        wire_bytes = decode_frame(expected)
        if wire_bytes != frame_bytes(address, command, mode):
            failures.append(
                f"{name}: round trip failed -> bytes {[hex(b) for b in wire_bytes]}"
            )
            continue
        decoded_address = decode_address(wire_bytes, mode)
        if (decoded_address, wire_bytes[2]) != (address, command):
            failures.append(
                f"{name}: round trip failed -> addr=0x{decoded_address:04X} "
                f"cmd=0x{wire_bytes[2]:02X}"
            )

    # 3. protocol invariants
    for name, address, command, mode, expected in VECTORS:
        if expected[0] != LEADER_MARK_US or expected[1] != LEADER_SPACE_US:
            failures.append(f"{name}: leader must be {LEADER_MARK_US}/{LEADER_SPACE_US} us")
        if expected[-1] != TRAILING_MARK_US:
            failures.append(f"{name}: must end with a {TRAILING_MARK_US} us burst")
        for i, value in enumerate(expected):
            if i % 2 == 0 and value not in (BIT_MARK_US, LEADER_MARK_US):
                failures.append(f"{name}: index {i} ({value}) must be a burst, not a space")

    if args.emit:
        for name, address, command, mode, expected in VECTORS:
            print(f"// {name}: address=0x{address:04X} command=0x{command:02X} mode={mode}")
            print(f"private val {name.upper()} = intArrayOf(")
            print(_kotlin_int_array(expected))
            print(")")
            print()
        print("private val REPEAT_FRAME = intArrayOf(")
        print(_kotlin_int_array(REPEAT_VECTOR, per_line=3))
        print(")")
        return 0

    if failures:
        print("FAIL")
        for failure in failures:
            print(f"  - {failure}")
        return 1

    print("OK")
    print(f"  carrier                 : {CARRIER_HZ} Hz")
    print(f"  leader / bit / trailing : {LEADER_MARK_US}/{BIT_MARK_US}/{TRAILING_MARK_US} us")
    print(f"  logical 0 / 1 space     : {ZERO_SPACE_US}/{ONE_SPACE_US} us")
    print(f"  frame length            : 67 entries (2 leader + 64 data + 1 trailing)")
    print(f"  frame duration          : {best_case}..{worst_case} us")
    print(f"  repeat frame            : {REPEAT_VECTOR} every {REPEAT_PERIOD_US} us")
    print(f"  verified vectors        : {len(VECTORS)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
