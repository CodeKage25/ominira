# Known issues and what comes next

What we know does not work yet, why, and the plan. Newest thinking first in each section.

## Talking over the app

**Now:** the customer has to wait for the app to finish speaking before they can answer. On the language screen that is the whole roll call, about 15 seconds.

**Why:** the microphone only opens once the app is quiet. If it were open while the app spoke, it would hear its own voice, and on the language screen the app itself says every language name, so it would pick the language it had just said. Phones only cancel their own audio on the call path: there the speaker's output is wired into the echo canceller as a reference. Media playback, which is how the app speaks, has no such wiring, and the phone's speech recogniser opens the microphone itself, so the app cannot hand it cleaned audio.

**Options:**

1. Listen in the gaps: after each line of the roll call, open the microphone for about two seconds. The customer never waits more than one line. Small and reliable.
2. Listen the whole time and ignore the word the app is saying at that moment. Faster, but the same word said at the same moment is missed, and the recogniser sometimes catches half of each.
3. Real echo cancellation: play every sound on the call path, record with Android's echo canceller and noise suppressor, feed that audio to YarnGPT or to the phone's recogniser (Android 13 and later only), and add voice activity detection to stop speaking the moment the customer starts. Two to three days, then testing on several phones: echo cancellers vary a lot between a Pixel and a low-cost Tecno or Itel, playback gets quieter, and Bluetooth behaves differently again.

Touch and hold to talk was tried and dropped.

## Yorùbá, Hausa and Igbo by voice, offline

**Now:** the phone's recogniser has no Yorùbá, Hausa or Igbo (checked on a Pixel 7: none on the phone, none online). With no Gateway, yes and no in those languages are touches (tap for yes, touch and hold for no); names, amounts and digits are still heard in en-NG.

**Next:** with the Gateway and cloud voice on, YarnGPT hears all five languages. A recogniser that runs on the phone for these languages would make it work offline.

## Copy and voices

- Every Pidgin, Yorùbá, Hausa and Igbo line in `android/.../core/Words.kt` is an unreviewed draft. Native speakers need to check them first where money is involved: "Nothing was sent", the readbacks and the scam pause.
- Bundled clips: Yorùbá and Hausa are complete for the lines that existed when they were made. Still missing: 7 Igbo lines, and in all three languages the camera directions, the BVN consent and choosing the PIN. YarnGPT allows 120 calls a day on each route, so this is one more run of `gateway/scripts/make_speech_clips.py`.
- Lines with a name or an amount in them cannot be recorded ahead, so offline they are read by the phone's English voice.
- The Gateway's own replies exist only in English and Pidgin; a Yorùbá, Hausa or Igbo customer on the Gateway hears English from it.

## Sending to another person

**Now:** every phone that signs up becomes the same demo customer, and a transfer only takes money out: nothing is credited, because the people paid are names in a directory, not accounts in the mock bank.

**Plan:**

- Gateway: each sign-up creates its own customer, with an account number, a balance and a token, named from the card the camera read.
- Gateway: a transfer between two Zenith accounts debits one and credits the other in the same database transaction.
- App: keeps its own customer's token, says its account number, sends to someone new by account number with a name read back, and announces money coming in while it is open.

The pitch would then need the network for both phones.

## Smaller things

- The sign-in screen is no longer reached after onboarding (the new customer has just chosen their PIN). It is kept for returning customers, who do not have a way in yet.
- The bank inside the app keeps everything in memory: closing the app, or "Start again from welcome", brings back the demo PIN 2 0 7 5 and the demo balance.
- The APK is 54 MB, mostly the bundled ML Kit models. Through the Play Store each phone gets only its own processor's libraries, and a minified release is smaller still.
- The ID card is read on the phone, and only the NIN's last four digits are kept. A real rollout sends the image to Zenith's verification vendor, and the Gateway's logs and transcripts would go through Presidio, with recognisers for NIN, BVN and account numbers.
