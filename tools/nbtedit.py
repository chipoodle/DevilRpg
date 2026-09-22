"""Editor NBT minimo (tipo-consciente) para tocar el guardado con cirugia.

Lo importante: NO reescribe el arbol a ojo — se guarda el TIPO de cada etiqueta al leerla y se vuelve a
escribir con el mismo, y hay una PRUEBA DE IDA Y VUELTA: si parsear+serializar un archivo no devuelve los
mismos bytes, el editor se niega a escribir nada. Asi un cambio de tipo (int -> long, etc.) no puede colarse.

Uso como libreria:
    nodo = cargar(ruta)                 # ('raiz', Tag) con el arbol tipado
    tag = buscar(nodo, ['Data', 'Player', 'neoforge:attachments'])
    fijar_arreglo_int(tag, 'aldeasReveladas', [0])
    guardar(ruta, nodo)                 # escribe .bak antes de pisar
"""
import gzip
import os
import struct
import sys

# --- lector / escritor de bytes -------------------------------------------------------------------


class L:
    def __init__(self, b):
        self.b, self.i = b, 0

    def u1(self):
        v = self.b[self.i]
        self.i += 1
        return v

    def i2(self):
        v = struct.unpack_from('>h', self.b, self.i)[0]
        self.i += 2
        return v

    def u2(self):
        v = struct.unpack_from('>H', self.b, self.i)[0]
        self.i += 2
        return v

    def i4(self):
        v = struct.unpack_from('>i', self.b, self.i)[0]
        self.i += 4
        return v

    def i8(self):
        v = struct.unpack_from('>q', self.b, self.i)[0]
        self.i += 8
        return v

    def f4(self):
        v = struct.unpack_from('>f', self.b, self.i)[0]
        self.i += 4
        return v

    def f8(self):
        v = struct.unpack_from('>d', self.b, self.i)[0]
        self.i += 8
        return v

    def s(self):
        n = self.u2()
        v = self.b[self.i:self.i + n].decode('utf-8')
        self.i += n
        return v

    def crudo(self, n):
        v = self.b[self.i:self.i + n]
        self.i += n
        return v


def _payload(r, t):
    if t == 1:
        return r.u1() if r.u1.__self__ else 0
    raise AssertionError


def leer_payload(r, t):
    """Devuelve el valor de una etiqueta del tipo t (el tipo se guarda aparte, en Tag)."""
    if t == 1:
        b = r.u1()
        return b - 256 if b > 127 else b          # byte con signo
    if t == 2:
        return r.i2()
    if t == 3:
        return r.i4()
    if t == 4:
        return r.i8()
    if t == 5:
        return r.f4()
    if t == 6:
        return r.f8()
    if t == 7:                                      # ByteArray
        n = r.i4()
        return list(r.crudo(n))
    if t == 8:
        return r.s()
    if t == 9:                                      # List: tipo de elemento + tamaño
        et = r.u1()
        n = r.i4()
        return (et, [leer_payload(r, et) for _ in range(n)])
    if t == 10:                                     # Compound
        d = {}
        while True:
            tt = r.u1()
            if tt == 0:
                return d
            nombre = r.s()
            d[nombre] = Tag(tt, leer_payload(r, tt))
    if t == 11:                                     # IntArray
        n = r.i4()
        return [r.i4() for _ in range(n)]
    if t == 12:                                     # LongArray
        n = r.i4()
        return [r.i8() for _ in range(n)]
    raise Exception('tipo NBT desconocido: %d' % t)


def escribir_payload(out, t, v):
    if t == 1:
        out.append(struct.pack('>b', v))
    elif t == 2:
        out.append(struct.pack('>h', v))
    elif t == 3:
        out.append(struct.pack('>i', v))
    elif t == 4:
        out.append(struct.pack('>q', v))
    elif t == 5:
        out.append(struct.pack('>f', v))
    elif t == 6:
        out.append(struct.pack('>d', v))
    elif t == 7:
        out.append(struct.pack('>i', len(v)))
        out.append(bytes(x & 0xFF for x in v))
    elif t == 8:
        b = v.encode('utf-8')
        out.append(struct.pack('>H', len(b)))
        out.append(b)
    elif t == 9:
        et, lista = v
        out.append(struct.pack('>B', et))
        out.append(struct.pack('>i', len(lista)))
        for x in lista:
            escribir_payload(out, et, x)
    elif t == 10:
        for nombre, tag in v.items():
            out.append(struct.pack('>B', tag.t))
            b = nombre.encode('utf-8')
            out.append(struct.pack('>H', len(b)))
            out.append(b)
            escribir_payload(out, tag.t, tag.v)
        out.append(b'\x00')
    elif t == 11:
        out.append(struct.pack('>i', len(v)))
        for x in v:
            out.append(struct.pack('>i', x))
    elif t == 12:
        out.append(struct.pack('>i', len(v)))
        for x in v:
            out.append(struct.pack('>q', x))
    else:
        raise Exception('tipo NBT desconocido: %d' % t)


class Tag:
    __slots__ = ('t', 'v')

    def __init__(self, t, v):
        self.t = t
        self.v = v

    def __repr__(self):
        if self.t == 10:
            return 'Compound(%s)' % sorted(self.v.keys())
        if self.t == 9:
            return 'List(tipo=%d, %d elementos)' % (self.v[0], len(self.v[1]))
        return '%r' % (self.v,)


def _descomprimir(raw):
    try:
        return gzip.decompress(raw)
    except Exception:
        return raw


def cargar(ruta):
    """Devuelve (nombre_raiz, Tag) del archivo NBT (con o sin gzip)."""
    raw = open(ruta, 'rb').read()
    datos = _descomprimir(raw)
    r = L(datos)
    t = r.u1()
    nombre = r.s()
    return nombre, Tag(t, leer_payload(r, t))


def serializar(nombre, raiz):
    out = [struct.pack('>B', raiz.t)]
    b = nombre.encode('utf-8')
    out += [struct.pack('>H', len(b)), b]
    escribir_payload(out, raiz.t, raiz.v)
    return b''.join(out)


def prueba_de_ida_y_vuelta(ruta):
    """Comprueba que parsear+serializar devuelve EXACTAMENTE los mismos bytes (sin comprimir)."""
    raw = _descomprimir(open(ruta, 'rb').read())
    nombre, raiz = cargar(ruta)
    vuelta = serializar(nombre, raiz)
    if vuelta != raw:
        raise SystemExit('IDA Y VUELTA FALLIDA en %s (%d vs %d bytes): no se toca nada'
                         % (ruta, len(raw), len(vuelta)))
    return True


def buscar(raiz, camino):
    """Baja por un camino de nombres de compuestos. Lanza si no existe."""
    nodo = raiz
    for paso in camino:
        if nodo.t != 10 or paso not in nodo.v:
            raise SystemExit('no existe el camino %s (me quede en %r)' % (camino, nodo))
        nodo = nodo.v[paso]
    return nodo


def guardar(ruta, nombre, raiz, comprimir=True):
    datos = serializar(nombre, raiz)
    if os.path.exists(ruta) and not os.path.exists(ruta + '.bak'):
        open(ruta + '.bak', 'wb').write(open(ruta, 'rb').read())
    salida = gzip.compress(datos) if comprimir else datos
    open(ruta, 'wb').write(salida)
    return len(datos), len(salida)


if __name__ == '__main__':
    for ruta in sys.argv[1:]:
        prueba_de_ida_y_vuelta(ruta)
        nombre, raiz = cargar(ruta)
        print('OK ida y vuelta: %s (raiz %r, tipo %d)' % (ruta, nombre, raiz.t))
