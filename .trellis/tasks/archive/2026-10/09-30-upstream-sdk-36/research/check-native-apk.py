#!/usr/bin/env python3
"""Inspect the freshly built APK; this is alignment evidence, not device execution."""
import hashlib
import json
import struct
import sys
import zipfile
from pathlib import Path

apk = Path(sys.argv[1]).resolve()
result = {"apk": str(apk), "size": apk.stat().st_size,
          "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "libraries": []}
with apk.open('rb') as raw, zipfile.ZipFile(apk) as archive:
    for entry in archive.infolist():
        if not entry.filename.startswith('lib/') or not entry.filename.endswith('.so'):
            continue
        assert entry.filename.startswith('lib/arm64-v8a/'), entry.filename
        data = archive.read(entry)
        assert data[:6] == b'\x7fELF\x02\x01', entry.filename
        machine = struct.unpack_from('<H', data, 18)[0]
        assert machine == 183
        offset = struct.unpack_from('<Q', data, 32)[0]
        stride, count = struct.unpack_from('<HH', data, 54)
        loads = []
        for i in range(count):
            header = struct.unpack_from('<IIQQQQQQ', data, offset + i * stride)
            if header[0] == 1:
                align = header[7]
                assert align >= 16384 and header[2] % 16384 == header[3] % 16384
                loads.append(align)
        assert loads
        raw.seek(entry.header_offset)
        header = raw.read(30)
        name_length, extra_length = struct.unpack_from('<HH', header, 26)
        data_offset = entry.header_offset + 30 + name_length + extra_length
        assert entry.compress_type == zipfile.ZIP_STORED and data_offset % 16384 == 0
        result['libraries'].append({"path": entry.filename, "elf_load_alignments": loads,
                                    "zip_offset": data_offset, "compressed": False,
                                    "sha256": hashlib.sha256(data).hexdigest()})
assert {item['path'] for item in result['libraries']} == {
    'lib/arm64-v8a/libBugly_Native.so',
    'lib/arm64-v8a/libandroidx.graphics.path.so',
    'lib/arm64-v8a/libumeng-spy.so',
}, result
print(json.dumps(result, indent=2))
