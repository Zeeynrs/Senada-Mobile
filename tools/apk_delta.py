#!/usr/bin/env python3
"""Generate, apply, and verify SNDL v1 binary delta patches for Senada Android APKs.

Produces byte-for-byte identical signed target APKs (preserving APK Signature Scheme v2/v3 blocks)
and emits a `senada-android-delta.json` manifest consumed by `senada.updater.ApkDelta` on Android.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import struct
import sys
from pathlib import Path

MAGIC = b"SNDL"
FORMAT_VERSION = 1
OP_EOF = 0
OP_COPY = 1
OP_INSERT = 2
BLOCK_SIZE = 1024
MIN_MATCH_BYTES = 32
MAX_TARGET_BYTES = 512 * 1024 * 1024


def sha256_file(path: Path) -> bytes:
    h = hashlib.sha256()
    with path.open("rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.digest()


def _write_varint(buf: bytearray, value: int) -> None:
    while True:
        if (value & ~0x7F) == 0:
            buf.append(value)
            return
        buf.append((value & 0x7F) | 0x80)
        value >>= 7


def _read_varint(stream: io.BufferedIOBase) -> int:
    result = 0
    shift = 0
    while shift < 64:
        b = stream.read(1)
        if not b:
            raise EOFError("Unexpected EOF reading varint")
        byte = b[0]
        result |= (byte & 0x7F) << shift
        if (byte & 0x80) == 0:
            return result
        shift += 7
    raise ValueError("Corrupt varint in delta stream")


def _index_zip_entries(base: memoryview) -> dict[bytes, list[int]]:
    index: dict[bytes, list[int]] = {}
    raw = base.tobytes()
    pos = 0
    limit = len(raw) - 30
    while pos <= limit:
        found = raw.find(b"PK\x03\x04", pos)
        if found < 0 or found > limit:
            break
        key = raw[found : found + min(64, len(raw) - found)]
        bucket = index.setdefault(key, [])
        if len(bucket) < 8:
            bucket.append(found)
        pos = found + 30
    return index


def _index_blocks(base: bytes) -> dict[bytes, list[int]]:
    index: dict[bytes, list[int]] = {}
    for offset in range(0, len(base) - BLOCK_SIZE + 1, BLOCK_SIZE):
        key = base[offset : offset + BLOCK_SIZE]
        bucket = index.setdefault(key, [])
        if len(bucket) < 4:
            bucket.append(offset)
    return index


def _common_prefix_len(base: bytes, b_off: int, target: bytes, t_off: int) -> int:
    limit = min(len(base) - b_off, len(target) - t_off)
    step = 4096
    matched = 0
    while matched + step <= limit and base[b_off + matched : b_off + matched + step] == target[t_off + matched : t_off + matched + step]:
        matched += step
    while matched < limit and base[b_off + matched] == target[t_off + matched]:
        matched += 1
    return matched


def create_delta(base_path: Path, target_path: Path, patch_path: Path) -> dict:
    base = base_path.read_bytes()
    target = target_path.read_bytes()
    base_hash = hashlib.sha256(base).digest()
    target_hash = hashlib.sha256(target).digest()

    zip_index = _index_zip_entries(memoryview(base))
    block_index = _index_blocks(base)

    ops = bytearray()
    literals = bytearray()

    def flush_literals() -> None:
        if literals:
            ops.append(OP_INSERT)
            _write_varint(ops, len(literals))
            ops.extend(literals)
            literals.clear()

    pos = 0
    t_len = len(target)
    copied_bytes = 0
    inserted_bytes = 0

    while pos < t_len:
        best_off = -1
        best_len = 0

        if pos + 30 <= t_len and target[pos : pos + 4] == b"PK\x03\x04":
            key = target[pos : pos + min(64, t_len - pos)]
            for cand in zip_index.get(key, ()):
                length = _common_prefix_len(base, cand, target, pos)
                if length >= MIN_MATCH_BYTES and length > best_len:
                    best_off, best_len = cand, length

        if best_len == 0 and pos + BLOCK_SIZE <= t_len:
            key = target[pos : pos + BLOCK_SIZE]
            for cand in block_index.get(key, ()):
                length = _common_prefix_len(base, cand, target, pos)
                if length >= BLOCK_SIZE and length > best_len:
                    best_off, best_len = cand, length

        if best_len >= MIN_MATCH_BYTES:
            flush_literals()
            ops.append(OP_COPY)
            _write_varint(ops, best_off)
            _write_varint(ops, best_len)
            copied_bytes += best_len
            pos += best_len
        else:
            literals.append(target[pos])
            inserted_bytes += 1
            pos += 1

    flush_literals()
    ops.append(OP_EOF)

    patch_path.parent.mkdir(parents=True, exist_ok=True)
    header = MAGIC + struct.pack(">I", FORMAT_VERSION) + base_hash + target_hash + struct.pack(">Q", t_len)
    compressed_ops = gzip.compress(bytes(ops), compresslevel=9, mtime=0)
    patch_bytes = header + compressed_ops
    patch_path.write_bytes(patch_bytes)

    patch_sha = hashlib.sha256(patch_bytes).hexdigest()
    saved_pct = round((1 - len(patch_bytes) / t_len) * 100, 2) if t_len else 0.0
    return {
        "baseSha256": base_hash.hex(),
        "targetSha256": target_hash.hex(),
        "patchSha256": patch_sha,
        "baseSizeBytes": len(base),
        "targetSizeBytes": t_len,
        "patchSizeBytes": len(patch_bytes),
        "copiedBytes": copied_bytes,
        "insertedBytes": inserted_bytes,
        "savedPercent": saved_pct,
    }


def apply_delta(base_path: Path, patch_path: Path, output_path: Path) -> str:
    actual_base_hash = sha256_file(base_path)
    base_len = base_path.stat().st_size
    with patch_path.open("rb") as pf:
        header = pf.read(80)
        if len(header) != 80:
            raise ValueError("Patch header truncated")
        if header[:4] != MAGIC:
            raise ValueError("Invalid patch magic")
        version = struct.unpack(">I", header[4:8])[0]
        if version != FORMAT_VERSION:
            raise ValueError(f"Unsupported format version {version}")
        expected_base_hash = header[8:40]
        expected_target_hash = header[40:72]
        expected_target_size = struct.unpack(">Q", header[72:80])[0]
        if expected_target_size == 0 or expected_target_size > MAX_TARGET_BYTES:
            raise ValueError("Invalid target size")
        if actual_base_hash != expected_base_hash:
            raise ValueError("Base APK SHA-256 mismatch")

        output_path.parent.mkdir(parents=True, exist_ok=True)
        out_hash = hashlib.sha256()
        written = 0
        with base_path.open("rb") as bf, output_path.open("wb") as out, gzip.GzipFile(fileobj=pf, mode="rb") as ops:
            saw_eof = False
            while True:
                op_b = ops.read(1)
                if not op_b:
                    break
                op = op_b[0]
                if op == OP_EOF:
                    saw_eof = True
                    # Drain to EOF so GzipFile verifies the GZIP CRC32/ISIZE trailer.
                    if ops.read(1):
                        raise ValueError("Unexpected trailing data after EOF marker")
                    break
                if op == OP_COPY:
                    offset = _read_varint(ops)
                    length = _read_varint(ops)
                    if offset < 0 or length <= 0 or offset + length > base_len:
                        raise ValueError("Copy range out of bounds")
                    if written + length > expected_target_size:
                        raise ValueError("Output exceeds target size")
                    bf.seek(offset)
                    rem = length
                    while rem > 0:
                        chunk = bf.read(min(65536, rem))
                        if not chunk:
                            raise EOFError("Base APK truncated")
                        out.write(chunk)
                        out_hash.update(chunk)
                        rem -= len(chunk)
                        written += len(chunk)
                elif op == OP_INSERT:
                    length = _read_varint(ops)
                    if length <= 0 or written + length > expected_target_size:
                        raise ValueError("Insert length out of bounds")
                    rem = length
                    while rem > 0:
                        chunk = ops.read(min(65536, rem))
                        if not chunk:
                            raise EOFError("Patch literal truncated")
                        out.write(chunk)
                        out_hash.update(chunk)
                        rem -= len(chunk)
                        written += len(chunk)
                else:
                    raise ValueError(f"Unknown op {op}")
            if not saw_eof:
                raise ValueError("Missing EOF marker")

        if written != expected_target_size:
            output_path.unlink(missing_ok=True)
            raise ValueError("Reconstructed size mismatch")
        if out_hash.digest() != expected_target_hash:
            output_path.unlink(missing_ok=True)
            raise ValueError("Reconstructed target SHA-256 mismatch")
        return out_hash.hexdigest()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="SNDL v1 binary delta tool for Senada Android APKs")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_create = sub.add_parser("create", help="Create a delta patch and optional manifest JSON")
    p_create.add_argument("--base", type=Path, required=True)
    p_create.add_argument("--target", type=Path, required=True)
    p_create.add_argument("--patch", type=Path, required=True)
    p_create.add_argument("--manifest", type=Path, default=None)
    p_create.add_argument("--target-version", default=None)
    p_create.add_argument("--target-version-code", type=int, default=None)

    p_apply = sub.add_parser("apply", help="Apply a delta patch to reconstruct the target APK")
    p_apply.add_argument("--base", type=Path, required=True)
    p_apply.add_argument("--patch", type=Path, required=True)
    p_apply.add_argument("--output", type=Path, required=True)

    args = parser.parse_args(argv)
    if args.cmd == "create":
        summary = create_delta(args.base, args.target, args.patch)
        if args.manifest is not None:
            manifest = {
                "formatVersion": FORMAT_VERSION,
                "targetAssetName": args.target.name,
                "targetSha256": summary["targetSha256"],
                "patches": [
                    {
                        "baseSha256": summary["baseSha256"],
                        "targetSha256": summary["targetSha256"],
                        "patchAssetName": args.patch.name,
                        "patchSizeBytes": summary["patchSizeBytes"],
                        "patchSha256": summary["patchSha256"],
                        **({"targetVersion": args.target_version} if args.target_version else {}),
                        **({"targetVersionCode": args.target_version_code} if args.target_version_code is not None else {}),
                    }
                ],
            }
            args.manifest.parent.mkdir(parents=True, exist_ok=True)
            args.manifest.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(summary, indent=2))
        return 0

    if args.cmd == "apply":
        digest = apply_delta(args.base, args.patch, args.output)
        print(json.dumps({"output": str(args.output), "sha256": digest}, indent=2))
        return 0

    return 1


if __name__ == "__main__":
    raise SystemExit(main())
