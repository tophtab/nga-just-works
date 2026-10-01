import unittest,subprocess,collections,sys,tarfile,types,pathlib,json
phase=sys.argv[1]; counts=collections.Counter(); original=subprocess.Popen
class CountedPopen(original):
 def __init__(self,args,*a,**kw):
  counts[str(args[0] if isinstance(args,(list,tuple)) else '<shell>')]+=1
  super().__init__(args,*a,**kw)
subprocess.Popen=CountedPopen
sys.path.insert(0,str(pathlib.Path('scripts').resolve()))
sys.path.insert(0,str(pathlib.Path.cwd()))
suite=unittest.TestSuite()
with tarfile.open('/tmp/simplify-tests-baseline.tar.gz') as archive:
 for p in sorted(pathlib.Path('scripts').glob('test_*.py')):
  source=archive.extractfile(str(p)).read().decode() if phase=='baseline' else p.read_text()
  module=types.ModuleType(p.stem);module.__file__=str(p.resolve());sys.modules[p.stem]=module
  exec(compile(source,module.__file__,'exec'),module.__dict__)
  suite.addTests(unittest.defaultTestLoader.loadTestsFromModule(module))
r=unittest.TextTestRunner(verbosity=0).run(suite)
output={'phase':phase,'tests':r.testsRun,'successful':r.wasSuccessful(),'direct_subprocess_starts':sum(counts.values()),'by_executable':dict(counts),'scope':'Popen calls by Python test process only; Bash/Git/stub descendant processes are not counted; untimed instrumentation run'}
pathlib.Path(f'.trellis/tasks/10-01-simplify-tests/research/{phase}-process-count.json').write_text(json.dumps(output,indent=2)+'\n')
print(json.dumps(output))
sys.exit(not r.wasSuccessful())
