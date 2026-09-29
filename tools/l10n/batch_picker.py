# ModePicker.kt: mode cards, result card, far/fit/hang/calibration dialogs.
import io
import re
import loc

P = 'app/src/main/java/com/jhani/measurear/presentation/ModePicker.kt'
S = 'stringResource(R.string.%s)'


def sr(key, *args):
    return 'stringResource(R.string.%s%s)' % (key, ''.join(', ' + a for a in args))


# Multi-line literals first (regex on whitespace)
full = loc.ROOT + P
s = io.open(full, encoding='utf-8').read()
s, n1 = re.subn(r'"How high you\'re holding the phone above the ground\. About 10 cm below your eye " \+\s*'
                r'"height works well\. Or point at the ground nearby so the app can measure it\.",',
                'stringResource(R.string.phone_height_help),', s)
s, n2 = re.subn(r'else -> "Every measurement will be corrected by \$\{"%\+\.1f"\.format\(\(factor - 1f\) \* 100\)\}% " \+\s*'
                r'"for this session\."',
                'else -> stringResource(R.string.calib_will_correct, "%+.1f%%".format((factor - 1f) * 100))', s)
assert n1 == 1 and n2 == 1, (n1, n2)
io.open(full, 'w', encoding='utf-8', newline='').write(s)

E = [
    (None, None, 'phone_height_help',
     "How high you're holding the phone above the ground. About 10 cm below your eye height works well. Or point at the ground nearby so the app can measure it.",
     'आप फ़ोन को ज़मीन से कितना ऊपर पकड़े हैं। आँखों की ऊँचाई से लगभग 10 सेमी नीचे ठीक रहता है। या पास की ज़मीन की ओर करें ताकि ऐप इसे माप सके।',
     'آپ فون کو زمین سے کتنا اوپر پکڑے ہیں۔ آنکھوں کی اونچائی سے تقریباً 10 سینٹی میٹر نیچے ٹھیک رہتا ہے۔ یا قریب کی زمین کی طرف کریں تاکہ ایپ اسے ناپ سکے۔',
     'మీరు ఫోన్‌ను నేల నుండి ఎంత ఎత్తులో పట్టుకున్నారు. కంటి ఎత్తు కంటే సుమారు 10 సెం.మీ. కింద బాగుంటుంది. లేదా యాప్ కొలవడానికి దగ్గరి నేల వైపు చూపించండి.'),
    (None, None, 'calib_will_correct', 'Every measurement will be corrected by %1$s for this session.',
     'इस सत्र में हर माप %1$s सुधारा जाएगा।', 'اس سیشن میں ہر پیمائش %1$s درست کی جائے گی۔',
     'ఈ సెషన్‌లో ప్రతి కొలత %1$s సరిచేయబడుతుంది.'),
    ('Text(mode.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)',
     'Text(stringResource(mode.titleRes()), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)', None, None, None, None, None),
    ('            mode.howTo,\n', '            stringResource(mode.howToRes()),\n', None, None, None, None, None),
    ('"What do you want to measure?",', S % 'picker_title' + ',', 'picker_title', 'What do you want to measure?',
     'आप क्या मापना चाहते हैं?', 'آپ کیا ناپنا چاہتے ہیں؟', 'మీరు ఏమి కొలవాలనుకుంటున్నారు?'),
    ('Text("Calibrate with a card",', 'Text(' + S % 'calib_button' + ',', 'calib_button', 'Calibrate with a card',
     'कार्ड से कैलिब्रेट करें', 'کارڈ سے کیلیبریٹ کریں', 'కార్డుతో క్యాలిబ్రేట్ చేయండి'),
    ('if (scale == 1f) "Measure a bank card once to make every reading more accurate"',
     'if (scale == 1f) ' + S % 'calib_button_sub', 'calib_button_sub', 'Measure a bank card once to make every reading more accurate',
     'हर माप को ज़्यादा सटीक बनाने के लिए एक बार बैंक कार्ड मापें', 'ہر پیمائش کو زیادہ درست بنانے کے لیے ایک بار بینک کارڈ ناپیں',
     'ప్రతి కొలతను మరింత ఖచ్చితంగా చేయడానికి ఒకసారి బ్యాంక్ కార్డును కొలవండి'),
    ('else "Calibrated: readings corrected by ${"%+.1f".format((scale - 1f) * 100)}% · tap to redo",',
     'else ' + sr('calib_button_done', '"%+.1f%%".format((scale - 1f) * 100)') + ',', 'calib_button_done',
     'Calibrated: readings corrected by %1$s · tap to redo', 'कैलिब्रेट हुआ: माप %1$s सुधारे गए · दोबारा करने के लिए टैप करें',
     'کیلیبریٹ ہو گیا: پیمائشیں %1$s درست · دوبارہ کرنے کے لیے ٹیپ کریں', 'క్యాలిబ్రేట్ అయింది: కొలతలు %1$s సరిచేయబడ్డాయి · మళ్లీ చేయడానికి ట్యాప్ చేయండి'),
    ('append("Built with ")', 'append(stringResource(R.string.built_with))', 'built_with', 'Built with ', 'बनाया गया ', 'بنایا گیا ', 'రూపొందించారు '),
    ('append(" by ")', 'append(stringResource(R.string.built_by))', 'built_by', ' by ', ' द्वारा ', ' از ', ' ద్వారా '),
    ('result.values.first().label', 'LocalContext.current.resultLabel(result.values.first().label)', None, None, None, None, None),
    ('draftCount == 0 -> mode.howTo', 'draftCount == 0 -> stringResource(mode.howToRes())', None, None, None, None, None),
    ('needed != null -> "Point ${draftCount + 1} of $needed"', 'needed != null -> ' + sr('step_point_of', 'draftCount + 1', 'needed'),
     'step_point_of', 'Point %1$d of %2$d', 'बिंदु %1$d / %2$d', 'نقطہ %1$d از %2$d', 'బిందువు %1$d / %2$d'),
    ('"Point ${draftCount + 1} — $more more to go"', sr('step_more', 'draftCount + 1', 'more'),
     'step_more', 'Point %1$d — %2$d more to go', 'बिंदु %1$d — %2$d और बाकी', 'نقطہ %1$d — %2$d مزید باقی', 'బిందువు %1$d — ఇంకా %2$d మిగిలి ఉన్నాయి'),
    ('mode == MeasureMode.AREA -> "$draftCount corners · tap the first corner or Done"',
     'mode == MeasureMode.AREA -> ' + sr('step_area', 'draftCount'), 'step_area',
     '%1$d corners · tap the first corner or Done', '%1$d कोने · पहला कोना या "हो गया" टैप करें',
     '%1$d کونے · پہلا کونا یا "مکمل" ٹیپ کریں', '%1$d మూలలు · మొదటి మూల లేదా "పూర్తి" ట్యాప్ చేయండి'),
    ('else -> "$draftCount points · tap Done to finish"', 'else -> ' + sr('step_points', 'draftCount'), 'step_points',
     '%1$d points · tap Done to finish', '%1$d बिंदु · ख़त्म करने के लिए "हो गया" टैप करें',
     '%1$d نقطے · ختم کرنے کے لیے "مکمل" ٹیپ کریں', '%1$d బిందువులు · ముగించడానికి "పూర్తి" ట్యాప్ చేయండి'),
    ('"${v.label} ${formatValue(v, false, unit)}",', '"${LocalContext.current.resultLabel(v.label)} ${formatValue(v, false, unit)}",', None, None, None, None, None),
    ('"✓ Ground found · phone ${com.jhani.measurear.measurement.formatLength(phoneHeight, unit)} up"',
     sr('ground_found', 'com.jhani.measurear.measurement.formatLength(phoneHeight, unit)'), 'ground_found',
     '✓ Ground found · phone %1$s up', '✓ ज़मीन मिली · फ़ोन %1$s ऊपर', '✓ زمین ملی · فون %1$s اوپر', '✓ నేల కనుగొనబడింది · ఫోన్ %1$s ఎత్తులో'),
    ('"Phone height ${com.jhani.measurear.measurement.formatLength(phoneHeight, unit)} · tap to set"',
     sr('phone_height_set', 'com.jhani.measurear.measurement.formatLength(phoneHeight, unit)'), 'phone_height_set',
     'Phone height %1$s · tap to set', 'फ़ोन की ऊँचाई %1$s · सेट करने के लिए टैप करें', 'فون کی اونچائی %1$s · سیٹ کرنے کے لیے ٹیپ کریں',
     'ఫోన్ ఎత్తు %1$s · సెట్ చేయడానికి ట్యాప్ చేయండి'),
    ('title = { Text("Phone height",', 'title = { Text(' + S % 'phone_height' + ',', 'phone_height', 'Phone height', 'फ़ोन की ऊँचाई',
     'فون کی اونچائی', 'ఫోన్ ఎత్తు'),
    ('                "Done",\n', '                ' + S % 'done' + ',\n', None, None, None, None, None),
    ('title = { Text("Calibration",', 'title = { Text(' + S % 'calibration' + ',', 'calibration', 'Calibration', 'कैलिब्रेशन', 'کیلیبریشن', 'క్యాలిబ్రేషన్'),
    ('Text("What did you measure?",', 'Text(' + S % 'calib_what' + ',', 'calib_what', 'What did you measure?', 'आपने क्या मापा?',
     'آپ نے کیا ناپا؟', 'మీరు ఏమి కొలిచారు?'),
    ('"${ref.label} · ${fmt(ref.meters)}",', '"${stringResource(ref.labelRes())} · ${fmt(ref.meters)}",', None, None, None, None, None),
    ('Text("Measured ${fmt(measuredMeters)}  ·  real ${fmt(reference.meters)}",',
     'Text(' + sr('calib_measured', 'fmt(measuredMeters)', 'fmt(reference.meters)') + ',', 'calib_measured',
     'Measured %1$s  ·  real %2$s', 'मापा %1$s  ·  असली %2$s', 'ناپا %1$s  ·  اصل %2$s', 'కొలిచినది %1$s  ·  అసలు %2$s'),
    ('!onSurface -> "Both ends need to be on the teal dots for a reliable calibration. Try again on a detected surface."',
     '!onSurface -> ' + S % 'calib_need_surface', 'calib_need_surface',
     'Both ends need to be on the teal dots for a reliable calibration. Try again on a detected surface.',
     'भरोसेमंद कैलिब्रेशन के लिए दोनों सिरे हरे-नीले बिंदुओं पर होने चाहिए। पहचानी गई सतह पर फिर से कोशिश करें।',
     'قابل اعتماد کیلیبریشن کے لیے دونوں سرے فیروزی نقطوں پر ہونے چاہییں۔ پہچانی گئی سطح پر دوبارہ کوشش کریں۔',
     'నమ్మకమైన క్యాలిబ్రేషన్ కోసం రెండు చివర్లూ టీల్ చుక్కలపై ఉండాలి. గుర్తించిన ఉపరితలంపై మళ్లీ ప్రయత్నించండి.'),
    ('factor == null -> "That\'s too far off to be a scale error — a point probably missed the edge. Try again."',
     'factor == null -> ' + S % 'calib_too_far', 'calib_too_far',
     "That's too far off to be a scale error — a point probably missed the edge. Try again.",
     'यह स्केल की ग़लती होने के लिए बहुत ज़्यादा अंतर है — शायद कोई बिंदु किनारे से चूक गया। फिर से कोशिश करें।',
     'یہ اسکیل کی غلطی ہونے کے لیے بہت زیادہ فرق ہے — شاید کوئی نقطہ کنارے سے چوک گیا۔ دوبارہ کوشش کریں۔',
     'ఇది స్కేల్ పొరపాటుకు చాలా ఎక్కువ తేడా — బహుశా ఒక బిందువు అంచును తప్పింది. మళ్లీ ప్రయత్నించండి.'),
    ('if (ok) "Apply" else "Try again",', 'if (ok) ' + S % 'apply' + ' else ' + S % 'try_again' + ',', 'apply', 'Apply', 'लागू करें', 'لاگو کریں', 'వర్తింపజేయండి'),
    ('                "Cancel",\n', '                ' + S % 'cancel' + ',\n', 'cancel', 'Cancel', 'रद्द करें', 'منسوخ کریں', 'రద్దు చేయండి'),
    ('"${spec.name} · ${fmt(spec.width)} × ${fmt(spec.depth)} × ${fmt(spec.height)}  ▾",',
     '"${LocalContext.current.boxName(spec)} · ${fmt(spec.width)} × ${fmt(spec.depth)} × ${fmt(spec.height)}  ▾",', None, None, None, None, None),
    ('title = { Text("What do you want to fit?",', 'title = { Text(' + S % 'fit_title' + ',', 'fit_title', 'What do you want to fit?',
     'आप क्या फिट करना चाहते हैं?', 'آپ کیا رکھنا چاہتے ہیں؟', 'మీరు ఏమి సరిపెట్టాలనుకుంటున్నారు?'),
    ('Text(preset.name, color = Color.White', 'Text(LocalContext.current.boxName(preset), color = Color.White', None, None, None, None, None),
    ('Text("Or your own size ($unitLabel)",', 'Text(' + sr('fit_own_size', 'unitLabel') + ',', 'fit_own_size', 'Or your own size (%1$s)',
     'या अपना आकार (%1$s)', 'یا اپنا سائز (%1$s)', 'లేదా మీ సొంత పరిమాణం (%1$s)'),
    ('listOf(Triple("Width", w) { v: String -> w = v }, Triple("Depth", d) { v: String -> d = v }, Triple("Height", ht) { v: String -> ht = v })',
     'listOf(Triple(stringResource(R.string.label_width), w) { v: String -> w = v }, Triple(stringResource(R.string.label_depth), d) { v: String -> d = v }, Triple(stringResource(R.string.label_height), ht) { v: String -> ht = v })',
     None, None, None, None, None),
    ('                "Use my size",\n', '                ' + S % 'fit_use_mine' + ',\n', 'fit_use_mine', 'Use my size', 'मेरा आकार इस्तेमाल करें',
     'میرا سائز استعمال کریں', 'నా పరిమాణం వాడండి'),
    ('                "Close",\n', '                ' + S % 'close' + ',\n', None, None, None, None, None),
    ('"${spec.count} frame${if (spec.count > 1) "s" else ""} · ${fmt(spec.width)} × ${fmt(spec.height)} · gap ${fmt(spec.gap)}  ▾",',
     'pluralStringResource(R.plurals.hang_summary, spec.count, spec.count, fmt(spec.width), fmt(spec.height), fmt(spec.gap)) + "  ▾",',
     None, None, None, None, None),
    ('val fields = listOf("Frame width", "Frame height", "Gap between", "Hook below top")',
     'val fields = listOf(' + ', '.join(S % k for k in ['hang_frame_width', 'hang_frame_height', 'hang_gap', 'hang_hook']) + ')',
     'hang_frame_width', 'Frame width', 'फ़्रेम की चौड़ाई', 'فریم کی چوڑائی', 'ఫ్రేమ్ వెడల్పు'),
    (None, None, 'hang_frame_height', 'Frame height', 'फ़्रेम की ऊँचाई', 'فریم کی اونچائی', 'ఫ్రేమ్ ఎత్తు'),
    (None, None, 'hang_gap', 'Gap between', 'बीच की दूरी', 'درمیانی فاصلہ', 'మధ్య దూరం'),
    (None, None, 'hang_hook', 'Hook below top', 'ऊपर से हुक नीचे', 'اوپر سے ہک نیچے', 'పై నుండి హుక్ కింద'),
    ('title = { Text("Hang pictures",', 'title = { Text(' + S % 'mode_hang' + ',', None, None, None, None, None),
    ('Text("Frames", color', 'Text(' + S % 'hang_frames' + ', color', 'hang_frames', 'Frames', 'फ़्रेम', 'فریم', 'ఫ్రేమ్‌లు'),
    ('"Hook below top: how far the hook or taut wire sits below the frame\'s top edge.",', S % 'hang_hook_help' + ',', 'hang_hook_help',
     "Hook below top: how far the hook or taut wire sits below the frame's top edge.",
     'ऊपर से हुक नीचे: हुक या तनी हुई तार फ़्रेम के ऊपरी किनारे से कितनी नीचे है।',
     'اوپر سے ہک نیچے: ہک یا تنی ہوئی تار فریم کے اوپری کنارے سے کتنی نیچے ہے۔',
     'పై నుండి హుక్ కింద: హుక్ లేదా బిగించిన తీగ ఫ్రేమ్ పై అంచు నుండి ఎంత కింద ఉంటుంది.'),
    ('                "Apply",\n', '                ' + S % 'apply' + ',\n', None, None, None, None, None),
]
loc.apply(P, E, imports=['androidx.compose.ui.res.stringResource', 'androidx.compose.ui.res.pluralStringResource',
                         'androidx.compose.ui.platform.LocalContext', 'com.jhani.measurear.R'])

# Calibration reference names (mapped in ModeText.kt)
loc.apply('app/src/main/java/com/jhani/measurear/presentation/ModeText.kt', [
    (None, None, 'ref_card', 'Bank / ID card (long edge)', 'बैंक / आईडी कार्ड (लंबा किनारा)', 'بینک / شناختی کارڈ (لمبا کنارہ)',
     'బ్యాంక్ / ఐడీ కార్డు (పొడవైన అంచు)'),
    (None, None, 'ref_a4_short', 'A4 paper (short edge)', 'A4 काग़ज़ (छोटा किनारा)', 'A4 کاغذ (چھوٹا کنارہ)', 'A4 కాగితం (చిన్న అంచు)'),
    (None, None, 'ref_a4_long', 'A4 paper (long edge)', 'A4 काग़ज़ (लंबा किनारा)', 'A4 کاغذ (لمبا کنارہ)', 'A4 కాగితం (పొడవైన అంచు)'),
])
