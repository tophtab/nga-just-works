#!/usr/bin/env python3
"""Run with test runtime classpath list and the AGP-resolved builder/R8 jar.
No original input bean is permitted on the post-shrink runtime classpath.
"""
import hashlib, os, pathlib, subprocess, sys, tempfile, zipfile
root = pathlib.Path.cwd()
source = pathlib.Path(__file__).resolve().parent
classpath_file, r8 = map(pathlib.Path, sys.argv[1:3])
work = pathlib.Path(tempfile.mkdtemp(prefix='u3-b2-r8-'))
print('WORK', work, flush=True)
cp = [pathlib.Path(p) for p in classpath_file.read_text().splitlines()]
prefixes = ['sp/phone/http/bean/TopicListBean', 'sp/phone/mvp/model/entity/ThreadPageInfo',
 'gov/anzong/androidnga/core/board/data/BoardEntity',
 'gov/anzong/androidnga/activity/compose/filter/FilterKeyword',
 'sp/phone/task/ReportTask$ResultBean', 'sp/phone/task/AvatarFileUploadTask$NonameUploadResponse',
 'gov/anzong/androidnga/common/base/JavaBean']
def selected(name):
 return any(name == p + '.class' or name.startswith(p + '$') for p in prefixes)
seen = set()
with zipfile.ZipFile(work/'beans.jar','w') as beans, zipfile.ZipFile(work/'libraries.jar','w') as libs:
 for p in cp:
  if not p.is_file() or p.suffix != '.jar': continue
  with zipfile.ZipFile(p) as z:
   for name in z.namelist():
    if not name.endswith('.class') or name in seen or name.startswith(('java/','javax/','sun/','jdk/','com/alibaba/fastjson/')): continue
    seen.add(name)
    (beans if selected(name) else libs).writestr(name,z.read(name))
with zipfile.ZipFile(work/'libraries.jar') as z:
 assert not any(selected(n) for n in z.namelist())
subprocess.run(['javac','-cp',str(work/'libraries.jar'),'-d',str(work/'harness'),str(source/'ReflectionProbe.java')],check=True)
subprocess.run(['jar','cf',str(work/'harness.jar'),'-C',str(work/'harness'),'.'],check=True)
java_home = pathlib.Path(os.path.realpath(subprocess.check_output(['which','java'],text=True).strip())).parent.parent
config = work/'probe.pro'
config.write_text('-keep class ReflectionProbe { public static void main(java.lang.String[]); }\n-printmapping '+str(work/'mapping.txt')+'\n-printconfiguration '+str(work/'configuration.txt')+'\n')
subprocess.run(['java','-cp',str(r8),'com.android.tools.r8.R8','--version'],check=True)
print('R8_SHA256',hashlib.sha256(r8.read_bytes()).hexdigest(),flush=True)
subprocess.run(['java','-Xmx2g','-cp',str(r8),'com.android.tools.r8.R8','--classfile','--release','--output',str(work/'shrunk.jar'),'--lib',str(java_home),'--lib',str(work/'libraries.jar'),'--pg-conf',str(root/'nga_phone_base_3.0/proguard.cfg'),'--pg-conf',str(root/'lib_base_common/consumer-rules.pro'),'--pg-conf',str(config),str(work/'beans.jar'),str(work/'harness.jar')],check=True)
subprocess.run(['java','-cp',str(work/'shrunk.jar')+os.pathsep+str(work/'libraries.jar'),'ReflectionProbe',str(source/'cases.json')],check=True)
print('PASS production keep rules; mapping/configuration:',work,flush=True)
