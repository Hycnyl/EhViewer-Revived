#!/usr/bin/env python3
"""Independently check every chunked V2/V3 content digest (not signer identity)."""

import argparse
import hashlib
from pathlib import Path
import struct

BLOCK_IDS = {0x7109871A: "v2", 0xF05368C0: "v3"}
DIGESTS = {0x0101: "sha256", 0x0102: "sha512", 0x0103: "sha256", 0x0104: "sha512", 0x0201: "sha256", 0x0202: "sha512", 0x0301: "sha256"}


def take(data, offset=0):
    if offset + 4 > len(data):
        raise ValueError("Truncated length-prefixed signing data")
    size = struct.unpack_from("<I", data, offset)[0]
    end = offset + 4 + size
    if end > len(data):
        raise ValueError("Truncated signing record")
    return data[offset + 4:end], end


def sequence(data):
    offset = 0
    while offset < len(data):
        value, offset = take(data, offset)
        yield value


def content_digest(sections, algorithm):
    chunks = []
    for section in sections:
        for offset in range(0, len(section), 1024 * 1024):
            chunk = section[offset:offset + 1024 * 1024]
            chunks.append(hashlib.new(algorithm, b"\xa5" + struct.pack("<I", len(chunk)) + chunk).digest())
    return hashlib.new(algorithm, b"\x5a" + struct.pack("<I", len(chunks)) + b"".join(chunks)).digest()


def verify_content_digests(path):
    data = path.read_bytes()
    # A valid EOCD ends exactly at EOF, including its optional ZIP comment.
    eocd = next((p for p in range(len(data) - 22, max(-1, len(data) - 65558), -1)
                 if data[p:p + 4] == b"PK\x05\x06" and p + 22 + struct.unpack_from("<H", data, p + 20)[0] == len(data)), None)
    if eocd is None:
        raise ValueError("APK ZIP end record is missing")
    central = struct.unpack_from("<I", data, eocd + 16)[0]
    if central < 32 or data[central - 16:central] != b"APK Sig Block 42":
        raise ValueError("APK signing block is missing")
    size = struct.unpack_from("<Q", data, central - 24)[0]
    start = central - size - 8
    if start < 0 or struct.unpack_from("<Q", data, start)[0] != size:
        raise ValueError("APK signing block sizes disagree")
    end_record = bytearray(data[eocd:])
    struct.pack_into("<I", end_record, 16, start)
    sections = (memoryview(data)[:start], memoryview(data)[central:eocd], end_record)
    calculated = {}
    checked = []
    offset = start + 8
    while offset < central - 24:
        pair_size = struct.unpack_from("<Q", data, offset)[0]
        if pair_size < 4 or offset + 8 + pair_size > central - 24:
            raise ValueError("Invalid APK signing pair")
        block_id = struct.unpack_from("<I", data, offset + 8)[0]
        value = data[offset + 12:offset + 8 + pair_size]
        offset += 8 + pair_size
        if block_id not in BLOCK_IDS:
            continue
        signers, consumed = take(value)
        if consumed != len(value):
            raise ValueError("Trailing signer data")
        for signer in sequence(signers):
            signed_data, _ = take(signer)
            digests, _ = take(signed_data)
            count = 0
            for record in sequence(digests):
                algorithm_id = struct.unpack_from("<I", record)[0]
                expected, consumed = take(record, 4)
                if consumed != len(record):
                    raise ValueError("Trailing digest data")
                if algorithm_id not in DIGESTS:
                    raise ValueError(f"Unsupported content digest algorithm: {algorithm_id:#x}")
                algorithm = DIGESTS[algorithm_id]
                if algorithm not in calculated:
                    calculated[algorithm] = content_digest(sections, algorithm)
                if expected != calculated[algorithm]:
                    raise ValueError(f"{BLOCK_IDS[block_id]} {algorithm} content digest does not verify: {path.name}")
                checked.append((BLOCK_IDS[block_id], algorithm_id, algorithm))
                count += 1
            if not count:
                raise ValueError("Signer has no content digests")
    if {scheme for scheme, _, _ in checked} != {"v2", "v3"}:
        raise ValueError("Both V2 and V3 content digests are required")
    return checked


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apks", type=Path, nargs="+")
    args = parser.parse_args()
    for apk in args.apks:
        for scheme, algorithm_id, algorithm in verify_content_digests(apk):
            print(f"PASS: {apk.name}: {scheme} {algorithm} ({algorithm_id:#06x}) content digest")
