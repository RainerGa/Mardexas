#!/usr/bin/env python3
"""Reject mixed-architecture JavaFX libraries in executable shaded JARs."""
import argparse
from pathlib import Path
import struct
import zipfile


def machine(data, extension):
    if len(data) < 64:
        raise ValueError('Truncated native library header')
    if extension == '.so' and data[:5] == b'\x7fELF\x02' and data[5] in (1, 2):
        endian = '<' if data[5] == 1 else '>'
        return struct.unpack_from(endian + 'H', data, 18)[0]
    if extension == '.dll' and data[:2] == b'MZ':
        offset = struct.unpack_from('<I', data, 60)[0]
        if offset + 6 <= len(data) and data[offset:offset + 4] == b'PE\0\0':
            return struct.unpack_from('<H', data, offset + 4)[0]
    if extension == '.dylib' and data[:4] == b'\xcf\xfa\xed\xfe':
        return struct.unpack_from('<I', data, 4)[0]
    raise ValueError(f'Invalid or unsupported native library header: {extension}')


def verify(platform, path):
    extension, expected = {'linux': ('.so', 62), 'win': ('.dll', 0x8664),
                           'mac': ('.dylib', 0x1000007),
                           'mac-aarch64': ('.dylib', 0x100000c)}[platform]
    count = 0
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            suffix = Path(name).suffix
            if suffix in ('.so', '.dll', '.dylib'):
                count += 1
                if suffix != extension or machine(archive.read(name), suffix) != expected:
                    raise ValueError(f'Wrong native library for {platform}: {name}')
    if count == 0:
        raise ValueError('No native JavaFX libraries found')
    return count


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('platform', choices=('linux', 'win', 'mac', 'mac-aarch64'))
    parser.add_argument('jar', type=Path)
    args = parser.parse_args()
    print(f'Checked {verify(args.platform, args.jar)} native libraries for {args.platform}')
