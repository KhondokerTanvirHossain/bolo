# MouthDiagram

Side views of the mouth for the six sound patterns Bangla speakers meet in English (ASM-2): v, w, th, s and sh, z, and starting a word with s. The s/sh pattern has a drawing for each sound, so there are seven drawings.

**Needs:** the sound (`v`, `w`, `th`, `s`, `sh`, `z`, `cluster`). Each drawing carries its own TalkBack description ("Side view: top teeth resting on the lower lip…").

**Drawing rules**
- A close-up cut-through of the mouth, facing right, the same frame for every sound so only the difference moves.
- Flesh (lips, gums, palate, a still tongue) is `mouth-flesh`; the inside of the mouth is `surface`; teeth are `mouth-tooth`; every edge is a 2px `ink` line.
- **Exactly one part moves** and only it is coloured: `mouth-move` with a 2.5px `hills-ink` edge. v moves the lower lip; w moves both lips; th, s, sh and z move the tongue.
- Air is a dashed `ink-secondary` line with an arrowhead, leaving the mouth at the right. z adds three small `hills-ink` buzz marks low in the throat; that is the only difference from s, which is the point.
- No labels inside the drawing. The words around it do the naming.

**Behaviour:** with `bo-mouth--play`, the moving part travels from rest into position, back, and in again (three 900 ms legs, `ease-out`), then holds. It plays once when the card appears and again from the replay button. Each drawing's rest offset is set with `--rest-x` and `--rest-y`. With reduced motion it shows the final position only. The final app builds these in Rive with the same frames.

**Copy:** each tip is one or two short lines in plain words, with no phonetics terms. The Bangla lines here are placeholders for the Bangla copywriter.
