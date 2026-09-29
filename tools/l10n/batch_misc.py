# HomeTools, Brand, Compass, Level, OnboardingHint, FloorPlanRenderer.
import loc

S = 'stringResource(R.string.%s)'
UI = ['androidx.compose.ui.res.stringResource', 'com.jhani.measurear.R']


def sr(key, *args):
    return 'stringResource(R.string.%s%s)' % (key, ''.join(', ' + a for a in args))


loc.apply('app/src/main/java/com/jhani/measurear/presentation/HomeTools.kt', [
    ('ActionPill("🎨  Materials", onMaterials)', 'ActionPill("🎨  " + ' + S % 'materials' + ', onMaterials)', 'materials', 'Materials',
     'सामग्री', 'سامان', 'సామగ్రి'),
    ('ActionPill("🗺  Floor plan", onPlan)', 'ActionPill("🗺  " + ' + S % 'label_floor_plan' + ', onPlan)', None, None, None, None, None),
    ('Text("Materials", color = Color.White', 'Text(' + S % 'materials' + ', color = Color.White', None, None, None, None, None),
    ('Text("for ${formatArea(area, false, unit)}",', 'Text(' + sr('materials_for', 'formatArea(area, false, unit)') + ',', 'materials_for',
     'for %1$s', '%1$s के लिए', '%1$s کے لیے', '%1$s కోసం'),
    ('{ listOf("🎨 Paint", "🧱 Tiles", "🪵 Flooring")[it] }',
     '{ listOf("🎨 " + paintLabel, "🧱 " + tilesLabel, "🪵 " + flooringLabel)[it] }', 'tab_paint', 'Paint', 'पेंट', 'پینٹ', 'పెయింట్'),
    (None, None, 'tab_tiles', 'Tiles', 'टाइल्स', 'ٹائلیں', 'టైల్స్'),
    (None, None, 'tab_flooring', 'Flooring', 'फ़्लोरिंग', 'فرش', 'ఫ్లోరింగ్'),
    ('Choice(listOf(1, 2, 3), coats, { "$it coat${if (it > 1) "s" else ""}" }) { coats = it }',
     'Choice(listOf(1, 2, 3), coats, { coatLabels[it - 1] }) { coats = it }', 'coats_1', '1 coat', '1 परत', '1 تہہ', '1 కోటు'),
    (None, None, 'coats_n', '%1$d coats', '%1$d परतें', '%1$d تہیں', '%1$d కోట్లు'),
    ('Stepper("Doors", doors)', 'Stepper(' + S % 'doors' + ', doors)', 'doors', 'Doors', 'दरवाज़े', 'دروازے', 'తలుపులు'),
    ('Stepper("Windows", windows)', 'Stepper(' + S % 'windows' + ', windows)', 'windows', 'Windows', 'खिड़कियाँ', 'کھڑکیاں', 'కిటికీలు'),
    ('{ "${it.toInt()} L can" }', '{ canLabel.format(it.toInt()) }', 'can_size', '%1$d L can', '%1$d लीटर डिब्बा', '%1$d لیٹر ڈبہ', '%1$d లీటర్ డబ్బా'),
    ('"%.1f L of paint for %s at ~10 m²/L per coat".format(r.liters, formatArea(r.paintedArea, false, unit))',
     sr('paint_detail', '"%.1f".format(r.liters)', 'formatArea(r.paintedArea, false, unit)'), 'paint_detail',
     '%1$s L of paint for %2$s at ~10 m²/L per coat', '%2$s के लिए %1$s लीटर पेंट (~10 m²/लीटर प्रति परत)',
     '%2$s کے لیے %1$s لیٹر پینٹ (~10 m²/لیٹر فی تہہ)', '%2$s కోసం %1$s లీటర్ల పెయింట్ (ఒక కోటుకు ~10 m²/లీటర్)'),
    ('BigResult("${r.tiles} tiles", "$tile cm, including ${waste.toInt()}% extra for cuts and breakage")',
     'BigResult(' + sr('tiles_count', 'r.tiles') + ', ' + sr('tiles_detail', 'tile', 'waste.toInt()') + ')', 'tiles_count',
     '%1$d tiles', '%1$d टाइल्स', '%1$d ٹائلیں', '%1$d టైల్స్'),
    (None, None, 'tiles_detail', '%1$s cm, including %2$d%% extra for cuts and breakage', '%1$s सेमी, कटाई और टूट-फूट के लिए %2$d%% अतिरिक्त सहित',
     '%1$s سینٹی میٹر، کٹائی اور ٹوٹ پھوٹ کے لیے %2$d%% اضافی سمیت', '%1$s సెం.మీ., కోతలు మరియు పగుళ్లకు %2$d%% అదనం సహా'),
    ('{ "%.1f m²/box".format(it) }', '{ boxLabel.format("%.1f".format(it)) }', 'box_area', '%1$s m²/box', '%1$s m²/डिब्बा', '%1$s m²/ڈبہ', '%1$s m²/డబ్బా'),
    ('BigResult("${r.boxes} boxes", "Covers %.1f m², including 8%% extra for cuts".format(r.coveredArea))',
     'BigResult(' + sr('boxes_count', 'r.boxes') + ', ' + sr('flooring_detail', '"%.1f".format(r.coveredArea)') + ')', 'boxes_count',
     '%1$d boxes', '%1$d डिब्बे', '%1$d ڈبے', '%1$d డబ్బాలు'),
    (None, None, 'flooring_detail', 'Covers %1$s m², including 8%% extra for cuts', '%1$s m² ढकता है, कटाई के लिए 8%% अतिरिक्त सहित',
     '%1$s m² ڈھکتا ہے، کٹائی کے لیے 8%% اضافی سمیت', '%1$s m² కవర్ చేస్తుంది, కోతలకు 8%% అదనం సహా'),
    ('"Estimates — check coverage on the pack before buying.",', S % 'estimates_note' + ',', 'estimates_note',
     'Estimates — check coverage on the pack before buying.', 'अनुमान — ख़रीदने से पहले पैक पर कवरेज देखें।',
     'تخمینہ — خریدنے سے پہلے پیک پر کوریج دیکھیں۔', 'అంచనాలు — కొనే ముందు ప్యాక్‌పై కవరేజ్ చూడండి.'),
    ('                "Done",\n', '                ' + S % 'done' + ',\n', None, None, None, None, None),
    ('title = { Text("Floor plan",', 'title = { Text(' + S % 'label_floor_plan' + ',', None, None, None, None, None),
    ('contentDescription = "Floor plan",', 'contentDescription = ' + S % 'label_floor_plan' + ',', None, None, None, None, None),
    ('ActionPill("Save to History", onSave)', 'ActionPill(' + S % 'save_to_history' + ', onSave)', 'save_to_history', 'Save to History',
     'इतिहास में सेव करें', 'تاریخچے میں محفوظ کریں', 'చరిత్రలో సేవ్ చేయండి'),
    ('                    "Share",\n', '                    ' + S % 'share' + ',\n', None, None, None, None, None),
    ('                "Close",\n', '                ' + S % 'close' + ',\n', None, None, None, None, None),
    # Labels computed once in the composable body
    ('    var tab by rememberSaveable { mutableIntStateOf(0) }',
     '''    var tab by rememberSaveable { mutableIntStateOf(0) }
    val paintLabel = stringResource(R.string.tab_paint)
    val tilesLabel = stringResource(R.string.tab_tiles)
    val flooringLabel = stringResource(R.string.tab_flooring)
    val coatLabels = listOf(stringResource(R.string.coats_1), stringResource(R.string.coats_n, 2), stringResource(R.string.coats_n, 3))
    val canLabel = stringResource(R.string.can_size).replace("%1\\$d", "%d")
    val boxLabel = stringResource(R.string.box_area).replace("%1\\$s", "%s")''', None, None, None, None, None),
], imports=UI)

loc.apply('app/src/main/java/com/jhani/measurear/presentation/Brand.kt', [
    ('            append("Built with ")', '            append(stringResource(R.string.built_with))', None, None, None, None, None),
    ('            append(" by ")', '            append(stringResource(R.string.built_by))', None, None, None, None, None),
    ('Text("Measure anything with your camera",', 'Text(' + S % 'tagline' + ',', 'tagline', 'Measure anything with your camera',
     'अपने कैमरे से कुछ भी मापें', 'اپنے کیمرے سے کچھ بھی ناپیں', 'మీ కెమెరాతో దేనినైనా కొలవండి'),
], imports=UI)

loc.apply('app/src/main/java/com/jhani/measurear/level/CompassScreen.kt', [
    ('Text("This phone has no compass sensor",', 'Text(' + S % 'compass_none' + ',', 'compass_none', 'This phone has no compass sensor',
     'इस फ़ोन में कम्पास सेंसर नहीं है', 'اس فون میں قطب نما سینسر نہیں ہے', 'ఈ ఫోన్‌లో దిక్సూచి సెన్సార్ లేదు'),
    ('                    CompassMath.cardinal(heading),', '                    directionName(CompassMath.cardinal(heading)),', None, None, None, None, None),
    ('Text("Magnetic north",', 'Text(' + S % 'magnetic_north' + ',', 'magnetic_north', 'Magnetic north', 'चुंबकीय उत्तर', 'مقناطیسی شمال', 'అయస్కాంత ఉత్తరం'),
    ('abs(turn) < 2f -> "On bearing ${target.roundToInt()}°"', 'abs(turn) < 2f -> ' + sr('on_bearing', 'target.roundToInt()'), 'on_bearing',
     'On bearing %1$d°', 'दिशा पर %1$d°', 'سمت پر %1$d°', 'దిశలో ఉన్నారు %1$d°'),
    ('turn > 0 -> "Turn ${turn.roundToInt()}° right"', 'turn > 0 -> ' + sr('turn_right', 'turn.roundToInt()'), 'turn_right',
     'Turn %1$d° right', '%1$d° दाएँ मुड़ें', '%1$d° دائیں مڑیں', '%1$d° కుడివైపు తిరగండి'),
    ('else -> "Turn ${(-turn).roundToInt()}° left"', 'else -> ' + sr('turn_left', '(-turn).roundToInt()'), 'turn_left',
     'Turn %1$d° left', '%1$d° बाएँ मुड़ें', '%1$d° بائیں مڑیں', '%1$d° ఎడమవైపు తిరగండి'),
    ('if (locked == null) "Lock bearing" else "Unlock",', 'if (locked == null) ' + S % 'lock_bearing' + ' else ' + S % 'unlock' + ',',
     'lock_bearing', 'Lock bearing', 'दिशा लॉक करें', 'سمت لاک کریں', 'దిశను లాక్ చేయండి'),
    (None, None, 'unlock', 'Unlock', 'अनलॉक', 'ان لاک', 'అన్‌లాక్'),
    ('"Compass needs calibration — move the phone in a figure-8",', S % 'compass_calibrate' + ',', 'compass_calibrate',
     'Compass needs calibration — move the phone in a figure-8', 'कम्पास को कैलिब्रेशन चाहिए — फ़ोन को 8 के आकार में घुमाएँ',
     'قطب نما کو کیلیبریشن چاہیے — فون کو 8 کی شکل میں گھمائیں', 'దిక్సూచికి క్యాలిబ్రేషన్ కావాలి — ఫోన్‌ను 8 ఆకారంలో తిప్పండి'),
    ('val label = when (deg) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "$deg" }',
     'val label = when (deg) { 0 -> dirN; 90 -> dirE; 180 -> dirS; 270 -> dirW; else -> "$deg" }', None, None, None, None, None),
    ('''private fun CompassDial(heading: Float, locked: Float?, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()''', '''private fun CompassDial(heading: Float, locked: Float?, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val dirN = directionName("N")
    val dirE = directionName("E")
    val dirS = directionName("S")
    val dirW = directionName("W")''', None, None, None, None, None),
    (None, None, 'dir_n', 'N', 'उ', 'ش', 'ఉ'),
    (None, None, 'dir_ne', 'NE', 'उपू', 'شم', 'ఈశా'),
    (None, None, 'dir_e', 'E', 'पू', 'م', 'తూ'),
    (None, None, 'dir_se', 'SE', 'दपू', 'جم', 'ఆగ్నే'),
    (None, None, 'dir_s', 'S', 'द', 'ج', 'ద'),
    (None, None, 'dir_sw', 'SW', 'दप', 'جغ', 'నైరు'),
    (None, None, 'dir_w', 'W', 'प', 'غ', 'ప'),
    (None, None, 'dir_nw', 'NW', 'उप', 'شغ', 'వాయ'),
], imports=UI)

loc.apply('app/src/main/java/com/jhani/measurear/level/LevelScreen.kt', [
    ('text = "This phone has no motion sensor for the level.",', 'text = ' + S % 'level_none' + ',', 'level_none',
     'This phone has no motion sensor for the level.', 'इस फ़ोन में लेवल के लिए मोशन सेंसर नहीं है।',
     'اس فون میں لیول کے لیے موشن سینسر نہیں ہے۔', 'ఈ ఫోన్‌లో లెవెల్ కోసం మోషన్ సెన్సార్ లేదు.'),
    ('isLevel -> "LEVEL"', 'isLevel -> ' + S % 'level_ok', 'level_ok', 'LEVEL', 'समतल', 'ہموار', 'సమతలం'),
    ('shown.mode == LevelMode.FLAT -> "Lay the phone on the surface"', 'shown.mode == LevelMode.FLAT -> ' + S % 'level_flat', 'level_flat',
     'Lay the phone on the surface', 'फ़ोन को सतह पर रखें', 'فون کو سطح پر رکھیں', 'ఫోన్‌ను ఉపరితలంపై ఉంచండి'),
    ('else -> "Hold the phone\'s edge against the surface"', 'else -> ' + S % 'level_edge', 'level_edge',
     "Hold the phone's edge against the surface", 'फ़ोन का किनारा सतह से सटाकर रखें', 'فون کا کنارہ سطح سے لگا کر رکھیں',
     'ఫోన్ అంచును ఉపరితలానికి ఆనించి పట్టుకోండి'),
    ('text = if (ref != null) "Relative to your reference · tap to reset" else "Tap to set a reference angle",',
     'text = if (ref != null) ' + S % 'level_relative' + ' else ' + S % 'level_set_ref' + ',', 'level_relative',
     'Relative to your reference · tap to reset', 'आपके संदर्भ के सापेक्ष · रीसेट करने के लिए टैप करें',
     'آپ کے حوالے کے مطابق · ری سیٹ کرنے کے لیے ٹیپ کریں', 'మీ సూచనతో పోలిస్తే · రీసెట్ చేయడానికి ట్యాప్ చేయండి'),
    (None, None, 'level_set_ref', 'Tap to set a reference angle', 'संदर्भ कोण सेट करने के लिए टैप करें', 'حوالہ زاویہ سیٹ کرنے کے لیے ٹیپ کریں',
     'సూచన కోణాన్ని సెట్ చేయడానికి ట్యాప్ చేయండి'),
], imports=UI)

loc.apply('app/src/main/java/com/jhani/measurear/presentation/OnboardingHint.kt', [
    ('text = "Point down at a table or floor from about 50 cm\\nand slide the phone sideways slowly",', 'text = ' + S % 'onboarding' + ',',
     'onboarding', 'Point down at a table or floor from about 50 cm\nand slide the phone sideways slowly',
     'लगभग 50 सेमी से मेज़ या फ़र्श की ओर करें\nऔर फ़ोन को धीरे-धीरे बगल में खिसकाएँ',
     'تقریباً 50 سینٹی میٹر سے میز یا فرش کی طرف کریں\nاور فون کو آہستہ آہستہ ایک طرف کھسکائیں',
     'సుమారు 50 సెం.మీ. నుండి టేబుల్ లేదా నేల వైపు చూపించి\nఫోన్‌ను నెమ్మదిగా పక్కకు జరపండి'),
], imports=UI)

loc.apply('app/src/main/java/com/jhani/measurear/presentation/FloorPlanRenderer.kt', [
    ('    fun render(outline: List<Vec3>, normal: Vec3?, unit: MeasureUnit, title: String, area: Float): Bitmap {',
     '    fun render(outline: List<Vec3>, normal: Vec3?, unit: MeasureUnit, title: String, area: Float, footer: String): Bitmap {',
     None, None, None, None, None),
    ('+ "  ·  measured with MeasureAR",', '+ "  ·  " + footer,', 'plan_footer', 'measured with MeasureAR',
     'MeasureAR से मापा गया', 'MeasureAR سے ناپا گیا', 'MeasureAR తో కొలిచారు'),
])
loc.apply('app/src/main/java/com/jhani/measurear/presentation/ARScreen.kt', [
    ('"${r.mode.title} plan"', '"${r.mode.title} plan"', None, None, None, None, None) if False else
    ('context.getString(R.string.plan_title, context.modeTitle(r.mode)), area)',
     'context.getString(R.string.plan_title, context.modeTitle(r.mode)), area, context.getString(R.string.plan_footer))',
     None, None, None, None, None),
])
