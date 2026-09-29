# Mode names, how-tos, result labels, furniture presets, shortcuts (no code replacement).
import loc

E = []
def add(key, en, hi, ur, te):
    E.append((None, None, key, en, hi, ur, te))

add('shortcut_height', 'Height', 'ऊँचाई', 'اونچائی', 'ఎత్తు')
add('shortcut_area', 'Room area', 'कमरे का क्षेत्रफल', 'کمرے کا رقبہ', 'గది వైశాల్యం')
add('shortcut_far', 'Building height', 'इमारत की ऊँचाई', 'عمارت کی اونچائی', 'భవనం ఎత్తు')

add('mode_line', 'Line', 'रेखा', 'لکیر', 'రేఖ')
add('mode_height', 'Height', 'ऊँचाई', 'اونچائی', 'ఎత్తు')
add('mode_far', 'Far height', 'दूर की ऊँचाई', 'دور کی اونچائی', 'దూరపు ఎత్తు')
add('mode_distance', 'Distance', 'दूरी', 'فاصلہ', 'దూరం')
add('mode_angle', 'Angle', 'कोण', 'زاویہ', 'కోణం')
add('mode_path', 'Path', 'पथ', 'راستہ', 'మార్గం')
add('mode_rectangle', 'Rectangle', 'आयत', 'مستطیل', 'దీర్ఘచతురస్రం')
add('mode_circle', 'Circle', 'वृत्त', 'دائرہ', 'వృత్తం')
add('mode_area', 'Area', 'क्षेत्रफल', 'رقبہ', 'వైశాల్యం')
add('mode_volume', 'Volume', 'आयतन', 'حجم', 'ఘనపరిమాణం')
add('mode_hang', 'Hang pictures', 'तस्वीरें टाँगें', 'تصویریں لٹکائیں', 'చిత్రాలు వేలాడదీయండి')
add('mode_fit', 'Will it fit?', 'क्या यह फिट होगा?', 'کیا یہ آئے گا؟', 'ఇది సరిపోతుందా?')
add('mode_calibrate', 'Calibrate', 'कैलिब्रेट', 'کیلیبریٹ', 'క్యాలిబ్రేట్')

add('howto_line', 'Tap two points', 'दो बिंदुओं पर टैप करें', 'دو نقطوں پر ٹیپ کریں', 'రెండు బిందువులపై ట్యాప్ చేయండి')
add('howto_height', 'Tap the base on the floor or table, then tilt up to the top',
    'फ़र्श या मेज़ पर आधार टैप करें, फिर ऊपर तक झुकाएँ',
    'فرش یا میز پر بنیاد ٹیپ کریں، پھر اوپر تک جھکائیں',
    'నేల లేదా టేబుల్‌పై అడుగు భాగాన్ని ట్యాప్ చేసి, పైకి వంచండి')
add('howto_far', 'Buildings & trees: aim at the base on the ground, then at the top',
    'इमारतें और पेड़: ज़मीन पर आधार की ओर, फिर ऊपर की ओर निशाना लगाएँ',
    'عمارتیں اور درخت: زمین پر بنیاد کی طرف، پھر اوپر کی طرف نشانہ لگائیں',
    'భవనాలు & చెట్లు: నేలపై అడుగు భాగానికి, తర్వాత పైభాగానికి గురి పెట్టండి')
add('howto_distance', 'Aim at any spot — tap to keep the distance from you',
    'किसी भी जगह पर निशाना लगाएँ — आपसे दूरी रखने के लिए टैप करें',
    'کسی بھی جگہ پر نشانہ لگائیں — آپ سے فاصلہ محفوظ کرنے کے لیے ٹیپ کریں',
    'ఏ చోటుకైనా గురి పెట్టండి — మీ నుండి దూరాన్ని ఉంచడానికి ట్యాప్ చేయండి')
add('howto_angle', 'Tap one arm, the corner, then the other arm',
    'एक भुजा, कोना, फिर दूसरी भुजा टैप करें',
    'ایک بازو، کونا، پھر دوسرا بازو ٹیپ کریں',
    'ఒక భుజం, మూల, తర్వాత మరో భుజాన్ని ట్యాప్ చేయండి')
add('howto_path', 'Tap points along the way, then Done',
    'रास्ते के बिंदु टैप करें, फिर "हो गया"',
    'راستے کے نقطے ٹیپ کریں، پھر "مکمل"',
    'దారిలోని బిందువులను ట్యాప్ చేసి, తర్వాత "పూర్తి"')
add('howto_rectangle', 'Tap three corners — the fourth is added for you',
    'तीन कोने टैप करें — चौथा अपने आप जुड़ जाएगा',
    'تین کونے ٹیپ کریں — چوتھا خود بخود لگ جائے گا',
    'మూడు మూలలను ట్యాప్ చేయండి — నాల్గవది ఆటోమేటిక్‌గా జోడించబడుతుంది')
add('howto_circle', 'Tap the center, then a point on the edge',
    'केंद्र टैप करें, फिर किनारे का एक बिंदु',
    'مرکز ٹیپ کریں، پھر کنارے کا ایک نقطہ',
    'కేంద్రాన్ని ట్యాప్ చేసి, తర్వాత అంచుపై ఒక బిందువు')
add('howto_area', 'Tap each corner, then back on the first to close',
    'हर कोना टैप करें, फिर बंद करने के लिए पहले पर वापस',
    'ہر کونا ٹیپ کریں، پھر بند کرنے کے لیے پہلے پر واپس',
    'ప్రతి మూలను ట్యాప్ చేసి, మూసివేయడానికి మొదటిదానిపై మళ్లీ ట్యాప్ చేయండి')
add('howto_volume', 'Tap three corners of the base, then tilt up to the top',
    'आधार के तीन कोने टैप करें, फिर ऊपर तक झुकाएँ',
    'بنیاد کے تین کونے ٹیپ کریں، پھر اوپر تک جھکائیں',
    'అడుగు భాగం మూడు మూలలను ట్యాప్ చేసి, పైకి వంచండి')
add('howto_hang', 'Tap the wall where the middle of your frames should be',
    'दीवार पर वहाँ टैप करें जहाँ फ़्रेमों का बीच होना चाहिए',
    'دیوار پر وہاں ٹیپ کریں جہاں فریموں کا درمیان ہونا چاہیے',
    'ఫ్రేమ్‌ల మధ్య భాగం ఎక్కడ ఉండాలో గోడపై అక్కడ ట్యాప్ చేయండి')
add('howto_fit', 'Pick a size, then tap the floor to place a life-size box — drag to move it',
    'आकार चुनें, फिर असली आकार का बॉक्स रखने के लिए फ़र्श टैप करें — खिसकाने के लिए खींचें',
    'سائز چنیں، پھر اصل سائز کا ڈبہ رکھنے کے لیے فرش ٹیپ کریں — ہٹانے کے لیے کھینچیں',
    'పరిమాణం ఎంచుకుని, అసలు పరిమాణం పెట్టెను ఉంచడానికి నేలపై ట్యాప్ చేయండి — కదిలించడానికి లాగండి')
add('howto_calibrate', 'Lay a bank card flat on the teal dots, then tap both ends of its long edge',
    'बैंक कार्ड को हरे-नीले बिंदुओं पर सपाट रखें, फिर उसके लंबे किनारे के दोनों सिरे टैप करें',
    'بینک کارڈ کو فیروزی نقطوں پر سیدھا رکھیں، پھر اس کے لمبے کنارے کے دونوں سرے ٹیپ کریں',
    'బ్యాంక్ కార్డును టీల్ చుక్కలపై సమంగా ఉంచి, దాని పొడవైన అంచు రెండు చివర్లను ట్యాప్ చేయండి')

for key, en, hi, ur, te in [
    ('label_length', 'Length', 'लंबाई', 'لمبائی', 'పొడవు'),
    ('label_height', 'Height', 'ऊँचाई', 'اونچائی', 'ఎత్తు'),
    ('label_distance', 'Distance', 'दूरी', 'فاصلہ', 'దూరం'),
    ('label_distance_from_you', 'Distance from you', 'आपसे दूरी', 'آپ سے فاصلہ', 'మీ నుండి దూరం'),
    ('label_from_you', 'From you', 'आपसे', 'آپ سے', 'మీ నుండి'),
    ('label_angle', 'Angle', 'कोण', 'زاویہ', 'కోణం'),
    ('label_arm1', 'Arm 1', 'भुजा 1', 'بازو 1', 'భుజం 1'),
    ('label_arm2', 'Arm 2', 'भुजा 2', 'بازو 2', 'భుజం 2'),
    ('label_total_length', 'Total length', 'कुल लंबाई', 'کل لمبائی', 'మొత్తం పొడవు'),
    ('label_last_segment', 'Last segment', 'आख़िरी हिस्सा', 'آخری حصہ', 'చివరి భాగం'),
    ('label_area', 'Area', 'क्षेत्रफल', 'رقبہ', 'వైశాల్యం'),
    ('label_width', 'Width', 'चौड़ाई', 'چوڑائی', 'వెడల్పు'),
    ('label_depth', 'Depth', 'गहराई', 'گہرائی', 'లోతు'),
    ('label_perimeter', 'Perimeter', 'परिधि', 'احاطہ', 'చుట్టుకొలత'),
    ('label_diameter', 'Diameter', 'व्यास', 'قطر', 'వ్యాసం'),
    ('label_circumference', 'Circumference', 'परिधि', 'محیط', 'పరిధి'),
    ('label_radius', 'Radius', 'त्रिज्या', 'رداس', 'వ్యాసార్థం'),
    ('label_side', 'Side', 'भुजा', 'ضلع', 'భుజం'),
    ('label_volume', 'Volume', 'आयतन', 'حجم', 'ఘనపరిమాణం'),
    ('label_base', 'Base', 'आधार', 'بنیاد', 'అడుగు'),
    ('label_distance_to_base', 'Distance to base', 'आधार तक दूरी', 'بنیاد تک فاصلہ', 'అడుగు వరకు దూరం'),
    ('label_pm_distance', '± Distance', '± दूरी', '± فاصلہ', '± దూరం'),
    ('label_pm_height', '± Height', '± ऊँचाई', '± اونچائی', '± ఎత్తు'),
    ('label_footprint', 'Footprint', 'फ़र्श पर जगह', 'فرش پر جگہ', 'నేలపై స్థలం'),
    ('label_nail_spacing', 'Nail spacing', 'कीलों के बीच दूरी', 'کیلوں کا درمیانی فاصلہ', 'మేకుల మధ్య దూరం'),
    ('label_total_width', 'Total width', 'कुल चौड़ाई', 'کل چوڑائی', 'మొత్తం వెడల్పు'),
    ('label_nail_below_top', 'Nail below frame top', 'फ़्रेम के ऊपर से कील नीचे', 'فریم کے اوپر سے کیل نیچے', 'ఫ్రేమ్ పై నుండి మేకు కింద'),
    ('label_floor_plan', 'Floor plan', 'फ़्लोर प्लान', 'فلور پلان', 'ఫ్లోర్ ప్లాన్'),
    ('box_sofa', '3-seat sofa', '3 सीट सोफ़ा', '3 سیٹ صوفہ', '3 సీట్ల సోఫా'),
    ('box_bed', 'Queen bed', 'क्वीन बेड', 'کوئین بیڈ', 'క్వీన్ బెడ్'),
    ('box_dining', 'Dining table', 'डाइनिंग टेबल', 'کھانے کی میز', 'డైనింగ్ టేబుల్'),
    ('box_desk', 'Desk', 'डेस्क', 'ڈیسک', 'డెస్క్'),
    ('box_fridge', 'Fridge', 'फ़्रिज', 'فریج', 'ఫ్రిజ్'),
    ('box_washer', 'Washing machine', 'वॉशिंग मशीन', 'واشنگ مشین', 'వాషింగ్ మెషిన్'),
    ('box_tv', '55" TV', '55" टीवी', '55" ٹی وی', '55" టీవీ'),
    ('box_wardrobe', 'Wardrobe', 'अलमारी', 'الماری', 'బీరువా'),
    ('box_custom', 'Custom', 'अपना आकार', 'اپنا سائز', 'మీ పరిమాణం'),
]:
    add(key, en, hi, ur, te)

loc.apply('app/src/main/java/com/jhani/measurear/presentation/ModeText.kt', E)
