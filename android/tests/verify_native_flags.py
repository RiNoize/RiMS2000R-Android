"""Check actual NDK commands, not the APK's debug/release label."""
import json, pathlib, shlex
wanted = {'h8s2350_emulator.cpp', 'h8s2350_instructions.cpp', 'ms2000_runner.cpp',
          'dsp.cpp', 'jit.cpp', 'rims_android.cpp'}
seen = set()
for path in pathlib.Path('android/app/.cxx').rglob('compile_commands.json'):
    if 'arm64-v8a' not in str(path):
        continue
    for row in json.loads(path.read_text()):
        name = pathlib.Path(row['file']).name
        if name not in wanted:
            continue
        cmd = row.get('arguments') or shlex.split(row['command'])
        options = [v for v in cmd if v.startswith('-O')]
        assert options and options[-1] == '-O3', (name, options)
        assert '-flto=thin' in cmd, name
        assert '-DNDEBUG' in cmd, name
        assert '-ffast-math' not in cmd and '-Ofast' not in cmd, name
        seen.add(name)
assert seen == wanted, ('Missing compile commands', wanted-seen)
print('PASS: effective -O3 + ThinLTO + NDEBUG, no fast-math:', ', '.join(sorted(seen)))
