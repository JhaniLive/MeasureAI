# UI refresh: ⋯ menu, accuracy words, grouped mode sheet, welcome screens (no code replacement).
import loc

E = []
def add(key, en, hi, ur, te):
    E.append((None, None, key, en, hi, ur, te))

# ⋯ menu
add('cd_more', 'More options', 'और विकल्प', 'مزید اختیارات', 'మరిన్ని ఎంపికలు')
add('flashlight', 'Flashlight', 'टॉर्च', 'ٹارچ', 'ఫ్లాష్‌లైట్')
add('surface_grid', 'Surface dots', 'सतह के बिंदु', 'سطح کے نقطے', 'ఉపరితల చుక్కలు')
add('magnifier', 'Magnifier', 'आवर्धक', 'میگنیفائر', 'మాగ్నిఫైయర్')
add('menu_units', 'Units', 'इकाई', 'اکائی', 'యూనిట్లు')
add('menu_occlusion', 'Hide dots behind objects', 'चीज़ों के पीछे बिंदु छिपाएँ', 'چیزوں کے پیچھے نقطے چھپائیں', 'వస్తువుల వెనుక చుక్కలను దాచండి')

# Accuracy in words (tap for the exact ±)
add('acc_good', 'Good', 'अच्छा', 'اچھا', 'బాగుంది')
add('acc_ok', 'OK', 'ठीक', 'ٹھیک', 'పర్వాలేదు')
add('acc_rough', 'Rough', 'मोटा अनुमान', 'موٹا اندازہ', 'స్థూలంగా')
add('acc_estimate', 'Estimate', 'अनुमान', 'تخمینہ', 'అంచనా')
add('cd_accuracy', 'Accuracy of the next point — tap for the exact margin',
    'अगले बिंदु की सटीकता — सटीक अंतर के लिए टैप करें',
    'اگلے نقطے کی درستگی — درست فرق کے لیے ٹیپ کریں',
    'తదుపరి బిందువు ఖచ్చితత్వం — ఖచ్చితమైన తేడా కోసం ట్యాప్ చేయండి')

# Grouped mode sheet
add('sec_recent', 'Recent', 'हाल ही में', 'حالیہ', 'ఇటీవలివి')
add('sec_measure', 'Measure', 'नापें', 'ناپیں', 'కొలవండి')
add('sec_room', 'Room & floor', 'कमरा और फ़र्श', 'کمرہ اور فرش', 'గది & నేల')
add('sec_home', 'Home', 'घर', 'گھر', 'ఇల్లు')
add('sec_outdoors', 'Outdoors', 'बाहर', 'باہر', 'బయట')

# Welcome (first launch, before the camera permission)
add('welcome_1_t', 'Measure with your camera', 'कैमरे से नापें', 'کیمرے سے ناپیں', 'కెమెరాతో కొలవండి')
add('welcome_1_b',
    'Point at a floor, table or wall and tap to measure lengths, heights and angles — no tape needed.',
    'फ़र्श, मेज़ या दीवार की ओर रखें और टैप करके लंबाई, ऊँचाई और कोण नापें — फ़ीते की ज़रूरत नहीं।',
    'فرش، میز یا دیوار کی طرف رکھیں اور ٹیپ کر کے لمبائی، اونچائی اور زاویے ناپیں — فیتے کی ضرورت نہیں۔',
    'నేల, టేబుల్ లేదా గోడ వైపు చూపి ట్యాప్ చేసి పొడవులు, ఎత్తులు, కోణాలు కొలవండి — టేప్ అవసరం లేదు.')
add('welcome_2_t', 'Rooms, walls and furniture', 'कमरे, दीवारें और फ़र्नीचर', 'کمرے، دیواریں اور فرنیچر', 'గదులు, గోడలు, ఫర్నిచర్')
add('welcome_2_b',
    'Get room areas and floor plans, work out paint and tiles, hang pictures level and check if a sofa fits.',
    'कमरों का क्षेत्रफल और फ़्लोर प्लान पाएँ, पेंट और टाइल का हिसाब लगाएँ, तस्वीरें सीधी टाँगें और देखें कि सोफ़ा फिट होगा या नहीं।',
    'کمروں کا رقبہ اور فلور پلان پائیں، پینٹ اور ٹائل کا حساب لگائیں، تصویریں سیدھی لٹکائیں اور دیکھیں کہ صوفہ فٹ ہوگا یا نہیں۔',
    'గదుల వైశాల్యం, ఫ్లోర్ ప్లాన్‌లు పొందండి, పెయింట్, టైల్స్ లెక్కించండి, చిత్రాలను సమతలంగా వేలాడదీయండి, సోఫా సరిపోతుందో చూడండి.')
add('welcome_3_t', 'Camera access', 'कैमरे की अनुमति', 'کیمرے کی اجازت', 'కెమెరా అనుమతి')
add('welcome_3_b',
    'MeasureAR uses the camera only to see surfaces. Nothing is uploaded — your measurements stay on your phone.',
    'MeasureAR कैमरे का इस्तेमाल सिर्फ़ सतहें देखने के लिए करता है। कुछ भी अपलोड नहीं होता — आपके माप आपके फ़ोन पर ही रहते हैं।',
    'MeasureAR کیمرے کو صرف سطحیں دیکھنے کے لیے استعمال کرتا ہے۔ کچھ بھی اپ لوڈ نہیں ہوتا — آپ کی پیمائشیں آپ کے فون پر ہی رہتی ہیں۔',
    'MeasureAR కెమెరాను ఉపరితలాలను చూడటానికి మాత్రమే వాడుతుంది. ఏదీ అప్‌లోడ్ కాదు — మీ కొలతలు మీ ఫోన్‌లోనే ఉంటాయి.')
add('welcome_start', 'Allow camera and start', 'कैमरा अनुमति दें और शुरू करें', 'کیمرے کی اجازت دیں اور شروع کریں', 'కెమెరాను అనుమతించి ప్రారంభించండి')
add('welcome_skip', 'Skip', 'छोड़ें', 'چھوڑیں', 'దాటవేయండి')

loc.apply('app/src/main/java/com/jhani/measurear/presentation/HelpGuide.kt', E)
