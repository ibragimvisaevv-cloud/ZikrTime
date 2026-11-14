"""Compare shipped ARMs, preserved package resources and native ELF alignment."""
import hashlib
import struct
import sys
import zipfile


def inspect_elf(data):
    if data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("Expected a 64-bit little-endian ELF")
    offset = struct.unpack_from("<Q", data, 32)[0]
    size, count = struct.unpack_from("<HH", data, 54)
    headers = [struct.unpack_from("<IIQQQQQQ", data, offset + i * size) for i in range(count)]
    segments = [align for typ, _, _, _, _, _, _, align in headers if typ == 1]
    if not segments or min(segments) < 16384:
        raise ValueError(f"ELF LOAD segment alignment: {segments}")
    return min(segments)


with zipfile.ZipFile(sys.argv[1]) as previous, zipfile.ZipFile(sys.argv[2]) as current:
    libs = {n for n in previous.namelist() if n.startswith("lib/") and n.endswith(".so")}
    assert libs and libs == {n for n in current.namelist() if n.startswith("lib/") and n.endswith(".so")}
    assert {n.split("/")[1] for n in libs} == {"arm64-v8a", "armeabi-v7a"}
    for name in sorted(libs):
        data = current.read(name)
        assert data == previous.read(name), name + " changed"
        assert current.getinfo(name).compress_type == zipfile.ZIP_DEFLATED, name + " is not compressed"
        if name.startswith("lib/arm64-v8a/"):
            print(f"{name}: ELF LOAD {inspect_elf(data)}, compressed, SHA256 {hashlib.sha256(data).hexdigest()}")
    assets = {n for n in previous.namelist() if n.startswith("assets/")}
    assert assets and assets == {n for n in current.namelist() if n.startswith("assets/")}
    # ART profiles index the DEX generated for this precise version. Their checksum
    # changes with BuildConfig.VERSION_CODE, while the shipped product data must match.
    generated_profiles = {"assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm"}
    product_assets = assets - generated_profiles
    for name in product_assets:
        assert previous.read(name) == current.read(name), name + " changed"
    print(f"Verified {len(libs)} native libraries and {len(product_assets)} unchanged product assets.")
