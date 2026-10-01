import json, pathlib, subprocess, time, sys, xml.etree.ElementTree as ET
phase=sys.argv[1]
out=pathlib.Path('.trellis/tasks/10-01-simplify-tests/research')
results=[]
for suite,command in [('jvm',['./gradlew','testDebugUnitTest','--console=plain','-I','/tmp/simplify-tests-force.gradle']),('python',['python3','-m','unittest','discover','-s','scripts'])]:
 if len(sys.argv)>2 and suite != sys.argv[2]:continue
 for i in range(1,4):
  log=out/f'{phase}-{suite}-{i}.log'
  start=time.monotonic()
  with log.open('w') as f:p=subprocess.run(command,stdout=f,stderr=subprocess.STDOUT)
  record={'suite':suite,'sample':i,'wall_seconds':time.monotonic()-start,'exit_code':p.returncode,'command':command}
  if suite=='jvm':
   rows=[]
   for xml in pathlib.Path('.').glob('*/build/test-results/testDebugUnitTest/TEST-*.xml'):
    if not list((xml.parents[3]/'src/test').rglob('*Test*')):continue
    r=ET.parse(xml).getroot()
    rows.append({'name':r.get('name'),'tests':int(r.get('tests',0)),'failures':int(r.get('failures',0)),'errors':int(r.get('errors',0)),'skipped':int(r.get('skipped',0)),'seconds':float(r.get('time',0))})
   record['suites']=rows
   record['test_seconds']=sum(x['seconds'] for x in rows)
   record['tests']=sum(x['tests'] for x in rows)
  results.append(record)
  (out/f'{phase}-{sys.argv[2] if len(sys.argv)>2 else "all"}-benchmark.json').write_text(json.dumps(results,indent=2)+'\n')
  print(phase,suite,i,round(record['wall_seconds'],3),record.get('tests'),record.get('test_seconds'),p.returncode,flush=True)
  if p.returncode:sys.exit(p.returncode)
