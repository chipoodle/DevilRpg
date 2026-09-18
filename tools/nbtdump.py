import gzip, struct, sys, io

class R:
    def __init__(self, b): self.b, self.i = b, 0
    def u1(self):
        v = self.b[self.i]; self.i += 1; return v
    def i1(self):
        v = struct.unpack_from('>b', self.b, self.i)[0]; self.i += 1; return v
    def i2(self):
        v = struct.unpack_from('>h', self.b, self.i)[0]; self.i += 2; return v
    def u2(self):
        v = struct.unpack_from('>H', self.b, self.i)[0]; self.i += 2; return v
    def i4(self):
        v = struct.unpack_from('>i', self.b, self.i)[0]; self.i += 4; return v
    def i8(self):
        v = struct.unpack_from('>q', self.b, self.i)[0]; self.i += 8; return v
    def f4(self):
        v = struct.unpack_from('>f', self.b, self.i)[0]; self.i += 4; return v
    def f8(self):
        v = struct.unpack_from('>d', self.b, self.i)[0]; self.i += 8; return v
    def s(self):
        n = self.u2(); v = self.b[self.i:self.i+n].decode('utf-8', 'replace'); self.i += n; return v

def payload(r, t):
    if t == 1: return r.i1()
    if t == 2: return r.i2()
    if t == 3: return r.i4()
    if t == 4: return r.i8()
    if t == 5: return r.f4()
    if t == 6: return r.f8()
    if t == 7:
        n = r.i4(); v = r.b[r.i:r.i+n]; r.i += n; return v
    if t == 8: return r.s()
    if t == 9:
        et = r.u1(); n = r.i4()
        return [payload(r, et) for _ in range(n)]
    if t == 10:
        d = {}
        while True:
            tt = r.u1()
            if tt == 0: return d
            name = r.s(); d[name] = payload(r, tt)
    if t == 11:
        n = r.i4(); return [r.i4() for _ in range(n)]
    if t == 12:
        n = r.i4(); return [r.i8() for _ in range(n)]
    raise Exception('tag %d' % t)

def load(path):
    with open(path, 'rb') as f:
        raw = f.read()
    try:
        raw = gzip.decompress(raw)
    except Exception:
        pass
    r = R(raw)
    t = r.u1(); name = r.s()
    return payload(r, t)

def walk(node, path='', out=None, depth=0):
    if out is None: out = []
    if isinstance(node, dict):
        for k, v in node.items():
            p = path + '/' + k
            if isinstance(v, (dict, list)):
                walk(v, p, out, depth+1)
            elif isinstance(v, bytes):
                out.append((p, 'bytes[%d]' % len(v), v[:24].hex()))
            else:
                out.append((p, type(v).__name__, repr(v)[:120]))
    elif isinstance(node, list):
        for i, v in enumerate(node):
            p = '%s[%d]' % (path, i)
            if isinstance(v, (dict, list)):
                walk(v, p, out, depth+1)
            elif isinstance(v, bytes):
                out.append((p, 'bytes[%d]' % len(v), v[:24].hex()))
            else:
                out.append((p, type(v).__name__, repr(v)[:120]))
    return out

if __name__ == '__main__':
    for path in sys.argv[1:]:
        print('=' * 100)
        print(path)
        try:
            root = load(path)
        except Exception as e:
            print('  ERROR:', e); continue
        rows = walk(root)
        for p, t, v in rows:
            if 'Minion' in p or 'minion' in p:
                print('  %-70s %-12s %s' % (p, t, v))
        print('  --- total tags: %d' % len(rows))
