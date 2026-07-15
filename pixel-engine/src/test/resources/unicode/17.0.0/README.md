# Unicode 17.0.0 text and Bidi conformance inputs

Pixel Engine implements the default extended grapheme cluster rules from Unicode Standard Annex
#29 revision 47. Production code uses generated engine-owned tables and does not delegate boundary
decisions to `java.text.BreakIterator`, Android ICU, or another platform Unicode implementation.

Tracked upstream artifacts:

| Artifact | Official source | SHA-256 |
| --- | --- | --- |
| `GraphemeBreakProperty.txt` | `https://www.unicode.org/Public/17.0.0/ucd/auxiliary/GraphemeBreakProperty.txt` | `d6b51d1d2ae5c33b451b7ed994b48f1f4dc62b2272a5831e7fd418514a6bae89` |
| `GraphemeBreakTest.txt` | `https://www.unicode.org/Public/17.0.0/ucd/auxiliary/GraphemeBreakTest.txt` | `e2d134d2c52919bace503ebb6a551c1855fe1a1faec18478c78fff254a1793ec` |
| `emoji-data.txt` | `https://www.unicode.org/Public/17.0.0/ucd/emoji/emoji-data.txt` | `2cb2bb9455cda83e8481541ecf5b6dfda66a3bb89efa3fa7c5297eccf607b72b` |
| `DerivedCoreProperties.txt` | `https://www.unicode.org/Public/17.0.0/ucd/DerivedCoreProperties.txt` | `24c7fed1195c482faaefd5c1e7eb821c5ee1fb6de07ecdbaa64b56a99da22c08` |
| `DerivedBidiClass.txt` | `https://www.unicode.org/Public/17.0.0/ucd/extracted/DerivedBidiClass.txt` | `4867b4b7f0731ed1bfcd34cc6251211ff1542541fce0734b6fbda139ee80b3a4` |
| `BidiBrackets.txt` | `https://www.unicode.org/Public/17.0.0/ucd/BidiBrackets.txt` | `dadbaf38a0d0246e5b805bf8725cb81b7c621f93d030595635f5ba2c2f179428` |
| `BidiMirroring.txt` | `https://www.unicode.org/Public/17.0.0/ucd/BidiMirroring.txt` | `a2f16fb873ab4fcdf3221cb1a8a85a134ddd6ed03603181823ff5206af3741ce` |
| `BidiTest.txt` | `https://www.unicode.org/Public/17.0.0/ucd/BidiTest.txt` | `888bdfc8090652272d1f859cdb00ae659e2dc6c26740be61ef1d03998a687620` |
| `BidiCharacterTest.txt` | `https://www.unicode.org/Public/17.0.0/ucd/BidiCharacterTest.txt` | `a3e6e905ab5afbe318a96df5401d0372a04cd73ef139ab5e3cf0ae241c255488` |
| `LICENSE-UNICODE.txt` | `https://www.unicode.org/license.txt` | `e7a93b009565cfce55919a381437ac4db883e9da2126fa28b91d12732bc53d96` |

`GraphemeBreakTest.txt` is the complete 766-case Unicode 17.0.0 default grapheme corpus, not a
hand-selected sample. `DerivedCoreProperties.txt` supplies `Indic_Conjunct_Break` for GB9c and
`emoji-data.txt` supplies `Extended_Pictographic` for GB11. The data files are covered by the
tracked Unicode License v3 (`SPDX-License-Identifier: Unicode-3.0`); the generator also places the
license at `src/main/resources/META-INF/LICENSE-UNICODE.txt` for release-artifact packaging.

Pixel Engine also fixes Unicode Bidirectional Algorithm behavior to Unicode 17.0.0, UAX #9
revision 51. `DerivedBidiClass.txt` supplies all Bidi_Class values, `BidiBrackets.txt` supplies the
normative N0/BD16 pair data, and `BidiMirroring.txt` supplies all 428 character-based L4 mappings.
The complete property-only `BidiTest.txt` and explicit-code-point `BidiCharacterTest.txt` corpora
run against the engine-owned resolver; production paragraph layout does not delegate Bidi levels
or visual order to the desktop JDK, Android `java.text.Bidi`, or version-dependent Android ICU.

Regenerate the Kotlin tables and re-import the licensed test corpus with:

```shell
python3 tools/generate_unicode_grapheme_data.py
python3 tools/generate_unicode_grapheme_data.py --check
python3 tools/generate_unicode_bidi_data.py
python3 tools/generate_unicode_bidi_data.py \
  --input-dir pixel-engine/src/test/resources/unicode/17.0.0 \
  --check
```

Both generators reject any upstream byte change until a reviewer deliberately updates the pinned
Unicode version and checksums. Bidi `--check` accepts the tracked flat resource directory as an
offline input, verifies every pinned digest, and fails without rewriting production tables when an
output is missing or stale. The two canonical bracket singleton mappings used by BD16 are reviewed
constants tied to the pinned Unicode 17 `UnicodeData.txt` digest recorded by the generator tests.
