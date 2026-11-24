
#!/usr/bin/env python3

import math
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHADER = ROOT / "app/src/main/assets/shaders/hdr-toys/gamut-mapping/bottosson.glsl"

HUE_SIZE = 256
LIGHTNESS_SIZE = 128
SEARCH_ITERATIONS = 16
TAU = 2.0 * math.pi

# GLSL mat3 constructor values are column-major.
OKLAB_TO_LMS = (
    (1.0, 0.3963377773761749, 0.2158037573099136),
    (1.0, -0.1055613458156586, -0.0638541728258133),
    (1.0, -0.0894841775298119, -1.2914855480194092),
)

LMS_TO_XYZ = (
    (1.2268798758459243, -0.0405757452148008, -0.0763729366746601),
    (-0.5578149944602171, 1.1122868032803170, -0.4214933324022432),
    (0.2813910456659647, -0.0717110580655164, 1.5869240198367816),
)

XYZ_TO_BT2020 = (
    (1.716651187971268, -0.666684351832489, 0.017639857445311),
    (-0.355670783776392, 1.616481236634939, -0.042770613257809),
    (-0.253366281373660, 0.0157685458139111, 0.942103121235474),
)

XYZ_TO_DISPLAY_P3 = (
    (2.493496911941425, -0.829488969561575, 0.035845830243784),
    (-0.931383617919124, 1.762664060318346, -0.076172389268041),
    (-0.402710784450717, 0.023624685841944, 0.956884524007687),
)

COLORCHECKER = (
    0.18158027, 0.19516221, 0.19640634, 0.20838294,
    0.22344620, 0.22778351, 0.21271361, 0.18519045,
    0.19126318, 0.19723860, 0.18640968, 0.16684714,
    0.16482479, 0.17893054, 0.18111996, 0.17452106,
    0.18723654, 0.19247854, 0.18363837, 0.18146806,
    0.18256231, 0.18268473, 0.18513779, 0.17611732,
    0.17041260, 0.18824219, 0.19375455, 0.18274167,
    0.16157620, 0.14881917, 0.14981350, 0.14395775,
    0.13463538, 0.13850075, 0.14259487, 0.14075095,
    0.13407823, 0.13329459, 0.14016930, 0.14252374,
    0.14097312, 0.15197975, 0.16111179, 0.16087414,
    0.15178165, 0.15817795, 0.17093226, 0.17291499,
    0.16295567, 0.14675358, 0.13180242, 0.12200595,
    0.11884665, 0.12692752, 0.14128497, 0.15998762,
    0.17610309, 0.18126176, 0.17243716, 0.15512532,
    0.16286327, 0.17827389, 0.18209356, 0.17319406,
)


def mul_matrix(v, matrix):
    return tuple(
        sum(v[i] * matrix[j][i] for i in range(3))
        for j in range(3)
    )


def oklch_to_xyz(lightness, chroma, hue):
    lab = (
        lightness,
        chroma * math.cos(hue),
        chroma * math.sin(hue),
    )
    roots = mul_matrix(lab, OKLAB_TO_LMS)
    lms = tuple(x * x * x for x in roots)
    return mul_matrix(lms, LMS_TO_XYZ)


def inside_gamut(rgb):
    return all(0.0 <= x <= 1.0 for x in rgb)


def find_boundary(lightness, hue, matrix):
    lower = 0.0
    upper = 1.0

    for _ in range(SEARCH_ITERATIONS):
        candidate = 0.5 * (lower + upper)
        xyz = oklch_to_xyz(lightness, candidate, hue)
        rgb = mul_matrix(xyz, matrix)

        if inside_gamut(rgb):
            lower = candidate
        else:
            upper = candidate

    return lower


def monotone_slope(previous, following):
    if previous * following <= 0.0:
        return 0.0

    magnitude = min(
        abs(previous),
        abs(following),
        0.5 * abs(previous + following),
    )
    return math.copysign(magnitude, previous)


def monotone_cubic(p0, p1, p2, p3, weight):
    m1 = monotone_slope(p1 - p0, p2 - p1)
    m2 = monotone_slope(p2 - p1, p3 - p2)

    w2 = weight * weight
    w3 = w2 * weight

    return (
        (2.0 * w3 - 3.0 * w2 + 1.0) * p1
        + (w3 - 2.0 * w2 + weight) * m1
        + (-2.0 * w3 + 3.0 * w2) * p2
        + (w3 - w2) * m2
    )


def colorchecker_protection(hue):
    position = (hue / TAU % 1.0) * len(COLORCHECKER)
    index = math.floor(position)
    weight = position - index
    mask = len(COLORCHECKER) - 1

    p0 = COLORCHECKER[(index - 1) & mask]
    p1 = COLORCHECKER[index & mask]
    p2 = COLORCHECKER[(index + 1) & mask]
    p3 = COLORCHECKER[(index + 2) & mask]

    return monotone_cubic(p0, p1, p2, p3, weight)


def generate_texture():
    data = bytearray()

    for y in range(LIGHTNESS_SIZE):
        lightness = y / (LIGHTNESS_SIZE - 1)

        for x in range(HUE_SIZE):
            hue = TAU * x / HUE_SIZE

            input_chroma = find_boundary(
                lightness, hue, XYZ_TO_BT2020
            )
            output_chroma = find_boundary(
                lightness, hue, XYZ_TO_DISPLAY_P3
            )
            protection = colorchecker_protection(hue)

            data.extend(struct.pack(
                "<4e",
                input_chroma,
                output_chroma,
                protection,
                1.0,
            ))

    return data.hex()


def main():
    source = SHADER.read_text(encoding="utf-8")

    if "//!TEXTURE GAMUT_BOUNDARY" in source:
        raise SystemExit(
            "ERROR: Shader already contains a static LUT. "
            "Expected the original COMPUTE shader."
        )

    start_marker = "//!HOOK OUTPUT\n//!SAVE GAMUT_BOUNDARY"
    end_marker = "//!HOOK OUTPUT\n//!BIND HOOKED"

    start = source.index(start_marker)
    end = source.index(end_marker, start + len(start_marker))

    lut_hex = generate_texture()
    lines = [
        lut_hex[i:i + 96]
        for i in range(0, len(lut_hex), 96)
    ]

    texture_block = (
        "//!TEXTURE GAMUT_BOUNDARY\n"
        "//!SIZE 256 128\n"
        "//!FORMAT rgba16hf\n"
        "//!FILTER NEAREST\n"
        "//!BORDER CLAMP\n"
        + "\n".join(lines)
        + "\n\n"
    )

    result = source[:start] + texture_block + source[end:]
    SHADER.write_text(result, encoding="utf-8")

    print("Bottosson static LUT generated successfully.")
    print(f"Dimensions: {HUE_SIZE} x {LIGHTNESS_SIZE}")
    print("Format: rgba16hf")
    print(f"Texture data: {len(lut_hex)} hexadecimal characters")
    print("Compute hook removed; OUTPUT hook retained.")


if __name__ == "__main__":
    main()
