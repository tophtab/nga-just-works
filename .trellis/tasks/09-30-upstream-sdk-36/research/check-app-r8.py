#!/usr/bin/env python3
"""Static inspection of actual app R8 outputs; does not execute minified Android code."""
from pathlib import Path
import hashlib
import json
import re
import struct

root = Path('nga_phone_base_3.0/build')
mapping_dir = root / 'outputs/mapping/release'
mapping = (mapping_dir / 'mapping.txt').read_text()
config = (mapping_dir / 'configuration.txt').read_text()
classes = [
    'sp.phone.http.bean.TopicListBean',
    'sp.phone.mvp.model.entity.ThreadPageInfo',
    'sp.phone.http.bean.ThreadRowInfo',
    'sp.phone.http.bean.ThreadData',
    'gov.anzong.androidnga.core.board.data.BoardEntity',
    'gov.anzong.androidnga.activity.compose.filter.FilterKeyword',
    'sp.phone.task.ReportTask$ResultBean',
    'sp.phone.task.AvatarFileUploadTask$NonameUploadResponse',
]
for name in classes:
    assert f'{name} -> {name}:' in mapping, name
for contract in ['-keepattributes', 'Signature', '*Annotation*', 'com.alibaba.fastjson2.',
                 'gov.anzong.androidnga.common.base.JavaBean',
                 'com.alibaba.android.arouter.routes.', 'android.os.Parcelable']:
    assert contract in config, contract

# Read standard little-endian DEX tables, then annotation-set type references.
def read_dex(path):
    data = path.read_bytes()
    assert data.startswith(b'dex\n'), path
    u32 = lambda off: struct.unpack_from('<I', data, off)[0]
    def uleb(off):
        result = shift = 0
        while True:
            byte = data[off]; off += 1
            result |= (byte & 127) << shift
            if byte < 128: return result, off
            shift += 7
    string_count, string_off = struct.unpack_from('<II', data, 56)
    strings = []
    for i in range(string_count):
        _, off = uleb(u32(string_off + i * 4))
        strings.append(data[off:data.index(0, off)].decode('utf-8', errors='replace'))
    type_count, type_off = struct.unpack_from('<II', data, 64)
    types = [strings[u32(type_off + i * 4)] for i in range(type_count)]
    _, field_off = struct.unpack_from('<II', data, 80)
    _, method_off = struct.unpack_from('<II', data, 88)
    count, off = struct.unpack_from('<II', data, 96)
    result = {}
    def annotations(offset):
        if not offset: return []
        return [types[uleb(u32(offset + 4 + i * 4) + 1)[0]] for i in range(u32(offset))]
    for i in range(count):
        values = struct.unpack_from('<8I', data, off + i * 32)
        descriptor = types[values[0]]
        name = descriptor[1:-1].replace('/', '.')
        if name not in classes: continue
        attrs = {'dex': str(path), 'class_annotations': [], 'member_annotations': {}}
        directory = values[5]
        if directory:
            class_annotations, fields, methods, _ = struct.unpack_from('<4I', data, directory)
            attrs['class_annotations'] = annotations(class_annotations)
            for j in range(fields + methods):
                index, ann_off = struct.unpack_from('<II', data, directory + 16 + j * 8)
                table = field_off if j < fields else method_off
                member = strings[u32(table + index * 8 + 4)]
                attrs['member_annotations'][member] = annotations(ann_off)
        result[name] = attrs
    return result

dex_files = sorted((root/'intermediates/dex/release/minifyReleaseWithR8').glob('*.dex'))
assert dex_files
retained = {}
for p in dex_files: retained.update(read_dex(p))
assert set(retained) == set(classes), set(classes) - set(retained)
json_annotation = 'Lcom/alibaba/fastjson2/annotation/JSONField;'
for method in ['getAuthorId', 'setAuthorId', 'getPostDate', 'setPostDate', 'getLastPoster', 'setLastPoster', 'getTitleFont', 'setTitleFont', 'getTopicMisc', 'setTopicMisc']:
    assert json_annotation in retained['sp.phone.mvp.model.entity.ThreadPageInfo']['member_annotations'][method], method
assert json_annotation in retained['gov.anzong.androidnga.core.board.data.BoardEntity']['member_annotations']['getIconUrl']

files = list(mapping_dir.glob('*.txt')) + dex_files
result = {'outputs': [{'path': str(p), 'size': p.stat().st_size,
                      'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in files],
          'retained_classes': retained,
          'production_contracts': 'JavaBean, Parcelable, JSON2, ARouter, Signature and annotations present in merged rules',
          'execution_limit': 'static app DEX inspection; classfile reflection is a separate fixture'}
print(json.dumps(result, indent=2))
