#!/usr/bin/env python3
"""
build_clean_verbs_db.py
Generador de la base de datos relacional limpia de Verbolandia (verbs.db).

Crea desde cero las siguientes tablas:
1. `verbs`: Información morfológica, fonética IPA, nivel CEFR, grupo y errores comunes.
2. `verb_tenses`: Los 12 tiempos verbales + Voz Pasiva + Condicional en afirmativo, negativo e interrogativo con traducción.
3. `phrasal_verbs`: Verbos frasales asociados con significado y ejemplo.

Compatible con las consultas existentes de Android y preparado para la versión 2.
"""

import os
import shutil
import sqlite3

OUTPUT_DB = os.path.abspath(os.path.join(os.path.dirname(__file__), "../app/src/main/assets/verbs.db"))
BACKUP_DB = OUTPUT_DB + ".bak"

# 1. Catálogo lingüístico curado de verbos esenciales (A1 a C1)
# Estructura: (base, third, past, pp, gerund, ipa_base, ipa_past, ipa_pp, spanish, irregular, level, morph_group, error_tip, context_en, context_es)
VERBS_CATALOG = [
    # --- IRREGULARES A1 - B1 (Alta Frecuencia) ---
    ("be", "is", "was", "been", "being", "/biː/", "/wɒz/", "/biːn/", "ser, estar", 1, "A1", "mixed", "Cuidado: 'was' es para I/he/she/it y 'were' para you/we/they.", "at home every Sunday", "en casa cada domingo"),
    ("have", "has", "had", "had", "having", "/hæv/", "/hæd/", "/hæd/", "tener, haber", 1, "A1", "mixed", "En 3ra persona presente se dice 'has', nunca 'haves'.", "a lot of patience", "mucha paciencia"),
    ("do", "does", "did", "done", "doing", "/duː/", "/dɪd/", "/dʌn/", "hacer", 1, "A1", "mixed", "No confundas 'did' (pasado simple) con 'done' (participio).", "the homework carefully", "la tarea cuidadosamente"),
    ("go", "goes", "went", "gone", "going", "/ɡoʊ/", "/wɛnt/", "/ɡɒn/", "ir", 1, "A1", "vowel_change", "El pasado es 'went', una forma completamente distinta.", "to the gym every morning", "al gimnasio cada mañana"),
    ("say", "says", "said", "said", "saying", "/seɪ/", "/sɛd/", "/sɛd/", "decir", 1, "A1", "vowel_change", "La pronunciación de 'said' rima con 'bed' (/sɛd/), no con 'paid'.", "the truth always", "la verdad siempre"),
    ("get", "gets", "got", "gotten", "getting", "/ɡɛt/", "/ɡɒt/", "/ˈɡɒt.ən/", "obtener, conseguir", 1, "A1", "vowel_change", "En inglés americano el participio es 'gotten', en británico 'got'.", "good grades at school", "buenas calificaciones en la escuela"),
    ("make", "makes", "made", "made", "making", "/meɪk/", "/meɪd/", "/meɪd/", "hacer, fabricar", 1, "A1", "vowel_change", "Usa 'make' para crear y 'do' para actividades o tareas.", "a delicious chocolate cake", "un pastel de chocolate delicioso"),
    ("know", "knows", "knew", "known", "knowing", "/noʊ/", "/njuː/", "/noʊn/", "saber, conocer", 1, "A1", "en_participle", "La letra 'k' es muda: no pronuncies /knoʊ/, sino /noʊ/.", "the answer to the riddle", "la respuesta al acertijo"),
    ("take", "takes", "took", "taken", "taking", "/teɪk/", "/tʊk/", "/ˈteɪ.kən/", "tomar, llevar", 1, "A1", "en_participle", "El pasado es 'took' y el participio 'taken'. Evita 'taked'.", "notes during the lecture", "notas durante la clase"),
    ("see", "sees", "saw", "seen", "seeing", "/siː/", "/sɔː/", "/siːn/", "ver", 1, "A1", "en_participle", "El pasado es 'saw' (/sɔː/), nunca uses 'seen' sin auxiliar have/has.", "the sunrise from the hill", "el amanecer desde la colina"),
    ("come", "comes", "came", "come", "coming", "/kʌm/", "/keɪm/", "/kʌm/", "venir, llegar", 1, "A1", "vowel_change", "El participio vuelve a ser 'come', igual que el infinitivo.", "to visit us on weekends", "a visitarnos los fines de semana"),
    ("think", "thinks", "thought", "thought", "thinking", "/θɪŋk/", "/θɔːt/", "/θɔːt/", "pensar, creer", 1, "A1", "vowel_change", "'Thought' suena como /θɔːt/; la 'gh' es completamente muda.", "about the future constantly", "sobre el futuro constantemente"),
    ("look", "looks", "looked", "looked", "looking", "/lʊk/", "/lʊkt/", "/lʊkt/", "mirar, parecer", 0, "A1", "regular_ed", "La terminación '-ed' suena como /t/ sorda: /lʊkt/.", "at the stars at night", "a las estrellas de noche"),
    ("want", "wants", "wanted", "wanted", "wanting", "/wɒnt/", "/ˈwɒn.tɪd/", "/ˈwɒn.tɪd/", "querer, desear", 0, "A1", "regular_ed", "Como termina en 't', el sufijo '-ed' se pronuncia con sílaba extra: /-ɪd/.", "to learn new languages", "aprender nuevos idiomas"),
    ("give", "gives", "gave", "given", "giving", "/ɡɪv/", "/ɡeɪv/", "/ˈɡɪv.ən/", "dar, entregar", 1, "A1", "en_participle", "El pasado es 'gave' y el participio 'given'. Nunca digas 'gived'.", "great advice to friends", "buenos consejos a los amigos"),
    ("find", "finds", "found", "found", "finding", "/faɪnd/", "/faʊnd/", "/faʊnd/", "encontrar, hallar", 1, "A1", "vowel_change", "Tanto el pasado como el participio son 'found'.", "a solution to the issue", "una solución al problema"),
    ("tell", "tells", "told", "told", "telling", "/tɛl/", "/toʊld/", "/toʊld/", "decir, contar", 1, "A1", "vowel_change", "'Tell' suele requerir un objeto directo ('tell me'), a diferencia de 'say'.", "an interesting story", "una historia interesante"),
    ("ask", "asks", "asked", "asked", "asking", "/ɑːsk/", "/ɑːskt/", "/ɑːskt/", "preguntar, pedir", 0, "A1", "regular_ed", "La '-ed' se pronuncia como /t/: /ɑːskt/.", "for directions politely", "indicaciones amablemente"),
    ("work", "works", "worked", "worked", "working", "/wɜːrk/", "/wɜːrkt/", "/wɜːrkt/", "trabajar, funcionar", 0, "A1", "regular_ed", "La '-ed' se pronuncia como /t/: /wɜːrkt/.", "on creative projects", "en proyectos creativos"),
    ("seem", "seems", "seemed", "seemed", "seeming", "/siːm/", "/siːmd/", "/siːmd/", "parecer", 0, "A2", "regular_ed", "La '-ed' tras consonante sonora suena suave como /d/.", "very confident today", "muy seguro hoy"),
    ("feel", "feels", "felt", "felt", "feeling", "/fiːl/", "/fɛlt/", "/fɛlt/", "sentir, palpar", 1, "A1", "vowel_change", "El pasado y participio son 'felt'. Evita decir 'feeled'.", "happy with the results", "feliz con los resultados"),
    ("try", "tries", "tried", "tried", "trying", "/traɪ/", "/traɪd/", "/traɪd/", "intentar, probar", 0, "A1", "regular_ed", "La 'y' cambia a 'i' en 'tries' y 'tried'.", "to do my best always", "hacer lo mejor siempre"),
    ("leave", "leaves", "left", "left", "leaving", "/liːv/", "/lɛft/", "/lɛft/", "salir, dejar, abandonar", 1, "A2", "vowel_change", "Pasado y participio son 'left'. No confundir con la izquierda.", "the office before six", "la oficina antes de las seis"),
    ("call", "calls", "called", "called", "calling", "/kɔːl/", "/kɔːld/", "/kɔːld/", "llamar, nombrar", 0, "A1", "regular_ed", "La '-ed' suena como /d/: /kɔːld/.", "my family every weekend", "a mi familia cada fin de semana"),
    ("break", "breaks", "broke", "broken", "breaking", "/breɪk/", "/broʊk/", "/ˈbroʊ.kən/", "romper, quebrar", 1, "A2", "en_participle", "Evita decir 'broked' o usar 'broke' con el auxiliar have/has.", "the world record", "el récord mundial"),
    ("choose", "chooses", "chose", "chosen", "choosing", "/tʃuːz/", "/tʃoʊz/", "/ˈtʃoʊ.zən/", "elegir, escoger", 1, "A2", "en_participle", "'Choose' lleva doble 'o'; en pasado 'chose' solo lleva una.", "the best option available", "la mejor opción disponible"),
    ("speak", "speaks", "spoke", "spoken", "speaking", "/spiːk/", "/spoʊk/", "/ˈspoʊ.kən/", "hablar", 1, "A1", "en_participle", "El pasado es 'spoke' y el participio 'spoken'. Nunca 'speaked'.", "three languages fluently", "tres idiomas con fluidez"),
    ("write", "writes", "wrote", "written", "writing", "/raɪt/", "/roʊt/", "/ˈrɪt.ən/", "escribir", 1, "A1", "en_participle", "La 'w' es muda. 'Written' lleva doble 't'.", "an inspiring article", "un artículo inspirador"),
    ("read", "reads", "read", "read", "reading", "/riːd/", "/rɛd/", "/rɛd/", "leer", 1, "A1", "vowel_change", "Se escribe igual en pasado y participio ('read'), pero se pronuncia /rɛd/ (como 'red').", "books in English daily", "libros en inglés a diario"),
    ("drive", "drives", "drove", "driven", "driving", "/draɪv/", "/droʊv/", "/ˈdrɪv.ən/", "conducir, manejar", 1, "A2", "en_participle", "En participio se pronuncia /ˈdrɪv.ən/, con 'i' corta.", "safely on the highway", "con seguridad por la carretera"),
    ("eat", "eats", "ate", "eaten", "eating", "/iːt/", "/eɪt/", "/ˈiː.tən/", "comer", 1, "A1", "en_participle", "El pasado es 'ate' (/eɪt/), rima con 'late'.", "healthy food at home", "comida saludable en casa"),
    ("drink", "drinks", "drank", "drunk", "drinking", "/drɪŋk/", "/dræŋk/", "/drʌŋk/", "beber, tomar", 1, "A1", "vowel_change", "Secuencia clásica i-a-u: drink, drank, drunk.", "plenty of water daily", "mucha agua a diario"),
    ("sleep", "sleeps", "slept", "slept", "sleeping", "/sliːp/", "/slɛpt/", "/slɛpt/", "dormir", 1, "A1", "vowel_change", "Pasado y participio son 'slept'. Evita 'sleeped'.", "eight hours every night", "ocho horas cada noche"),
    ("buy", "buys", "bought", "bought", "buying", "/baɪ/", "/bɔːt/", "/bɔːt/", "comprar", 1, "A1", "vowel_change", "'Bought' rima con 'thought'. La 'gh' es muda.", "fresh fruit at the market", "fruta fresca en el mercado"),
    ("bring", "brings", "brought", "brought", "bringing", "/brɪŋ/", "/brɔːt/", "/brɔːt/", "traer, llevar", 1, "A2", "vowel_change", "No confundir 'brought' (traer) con 'bought' (comprar).", "good news to the team", "buenas noticias al equipo"),
    ("teach", "teaches", "taught", "taught", "teaching", "/tiːtʃ/", "/tɔːt/", "/tɔːt/", "enseñar", 1, "A2", "vowel_change", "El pasado es 'taught' (/tɔːt/). No uses 'teached'.", "valuable skills to students", "habilidades valiosas a los estudiantes"),
    ("catch", "catches", "caught", "caught", "catching", "/kætʃ/", "/kɔːt/", "/kɔːt/", "atrapar, tomar (transporte)", 1, "A2", "vowel_change", "Pasado 'caught' (/kɔːt/). Evita 'catched'.", "the early train to town", "el tren temprano a la ciudad"),
    ("fight", "fights", "fought", "fought", "fighting", "/faɪt/", "/fɔːt/", "/fɔːt/", "pelear, luchar", 1, "B1", "vowel_change", "Pasado y participio son 'fought' (/fɔːt/).", "for justice and equality", "por la justicia y la igualdad"),
    ("fly", "flies", "flew", "flown", "flying", "/flaɪ/", "/fluː/", "/floʊn/", "volar", 1, "A2", "en_participle", "Pasado 'flew' (/fluː/), participio 'flown' (/floʊn/).", "across the ocean smoothly", "a través del océano sin problemas"),
    ("grow", "grows", "grew", "grown", "growing", "/ɡroʊ/", "/ɡruː/", "/ɡroʊn/", "crecer, cultivar", 1, "A2", "en_participle", "Pasado 'grew' (/ɡruː/), participio 'grown'.", "rapidly this season", "rápidamente esta temporada"),
    ("throw", "throws", "threw", "thrown", "throwing", "/θroʊ/", "/θruː/", "/θroʊn/", "lanzar, tirar", 1, "A2", "en_participle", "Pasado 'threw' (/θruː/), participio 'thrown'.", "the ball with precision", "la pelota con precisión"),
    ("draw", "draws", "drew", "drawn", "drawing", "/drɔː/", "/druː/", "/drɔːn/", "dibujar, trazar", 1, "A2", "en_participle", "El participio es 'drawn' (/drɔːn/).", "detailed architectural plans", "planos arquitectónicos detallados"),
    ("fall", "falls", "fell", "fallen", "falling", "/fɔːl/", "/fɛl/", "/ˈfɔː.lən/", "caer", 1, "A2", "en_participle", "El pasado es 'fell' (/fɛl/) y el participio 'fallen'.", "in love unexpectedly", "enamorarse inesperadamente"),
    ("begin", "begins", "began", "begun", "beginning", "/bɪˈɡɪn/", "/bɪˈɡæn/", "/bɪˈɡʌn/", "comenzar, empezar", 1, "A2", "vowel_change", "Secuencia i-a-u: begin, began, begun.", "a new career chapter", "un nuevo capítulo profesional"),
    ("run", "runs", "ran", "run", "running", "/rʌn/", "/ræn/", "/rʌn/", "correr, ejecutar", 1, "A1", "vowel_change", "El participio vuelve a ser 'run' (have run).", "five miles every morning", "cinco millas cada mañana"),
    ("swim", "swims", "swam", "swum", "swimming", "/swɪm/", "/swæm/", "/swʌm/", "nadar", 1, "A2", "vowel_change", "Secuencia i-a-u: swim, swam, swum.", "in the ocean comfortably", "en el océano con comodidad"),
    ("sing", "sings", "sang", "sung", "singing", "/sɪŋ/", "/sæŋ/", "/sʌŋ/", "cantar", 1, "A2", "vowel_change", "Secuencia i-a-u: sing, sang, sung.", "traditional melodies beautifully", "melodías tradicionales hermosamente"),
    ("ring", "rings", "rang", "rung", "ringing", "/rɪŋ/", "/ræŋ/", "/rʌŋ/", "sonar, llamar", 1, "A2", "vowel_change", "Secuencia i-a-u: ring, rang, rung.", "the bell loudly", "la campana con fuerza"),
    ("wear", "wears", "wore", "worn", "wearing", "/wɛər/", "/wɔːr/", "/wɔːrn/", "vestir, usar (ropa)", 1, "A2", "en_participle", "El participio es 'worn', nunca 'weared'.", "formal attire to the gala", "vestimenta formal para la gala"),
    ("tear", "tears", "tore", "torn", "tearing", "/tɛər/", "/tɔːr/", "/tɔːrn/", "rasgar, desgarrar", 1, "B1", "en_participle", "Pronunciado como /tɛər/. Pasado 'tore', participio 'torn'.", "the paper into pieces", "el papel en pedazos"),
    ("bear", "bears", "bore", "borne", "bearing", "/bɛər/", "/bɔːr/", "/bɔːrn/", "soportar, tolerar", 1, "B2", "en_participle", "Pasado 'bore', participio 'borne' o 'born' (para nacer).", "heavy responsibilities at work", "pesadas responsabilidades en el trabajo"),
    ("hide", "hides", "hid", "hidden", "hiding", "/haɪd/", "/hɪd/", "/ˈhɪd.ən/", "esconder, ocultar", 1, "A2", "en_participle", "Pasado 'hid', participio 'hidden' (con doble d).", "the secret behind a smile", "el secreto tras una sonrisa"),
    ("bite", "bites", "bit", "bitten", "biting", "/baɪt/", "/bɪt/", "/ˈbɪt.ən/", "morder", 1, "B1", "en_participle", "Pasado 'bit', participio 'bitten'.", "into the crisp apple", "la manzana crujiente"),
    ("shake", "shakes", "shook", "shaken", "shaking", "/ʃeɪk/", "/ʃʊk/", "/ˈʃeɪ.kən/", "sacudir, agitar", 1, "A2", "en_participle", "Pasado 'shook', participio 'shaken'.", "hands after the agreement", "las manos tras el acuerdo"),
    ("freeze", "freezes", "froze", "frozen", "freezing", "/friːz/", "/froʊz/", "/ˈfroʊ.zən/", "congelar", 1, "B1", "en_participle", "Pasado 'froze', participio 'frozen'.", "water in winter instantly", "agua en invierno al instante"),
    ("steal", "steals", "stole", "stolen", "stealing", "/stiːl/", "/stoʊl/", "/ˈstoʊ.lən/", "robar, hurtar", 1, "B1", "en_participle", "No confundir 'steal' (robar el objeto) con 'rob' (asaltar a la persona).", "the show with great acting", "la atención con gran actuación"),
    ("wake", "wakes", "woke", "woken", "waking", "/weɪk/", "/woʊk/", "/ˈwoʊ.kən/", "despertar", 1, "A2", "en_participle", "Pasado 'woke', participio 'woken'.", "up early with energy", "temprano con energía"),
    ("turn", "turns", "turned", "turned", "turning", "/tɜːrn/", "/tɜːrnd/", "/tɜːrnd/", "girar, voltear", 0, "A1", "regular_ed", "La '-ed' suena como /d/: /tɜːrnd/.", "the key in the lock", "la llave en la cerradura"),
    ("lead", "leads", "led", "led", "leading", "/liːd/", "/lɛd/", "/lɛd/", "liderar, guiar", 1, "B1", "vowel_change", "En pasado se escribe 'led', no 'lead'.", "the team with integrity", "al equipo con integridad"),
    ("hold", "holds", "held", "held", "holding", "/hoʊld/", "/hɛld/", "/hɛld/", "sostener, celebrar", 1, "A2", "vowel_change", "Pasado y participio son 'held'.", "a meeting every Monday", "una reunión cada lunes"),
    ("meet", "meets", "met", "met", "meeting", "/miːt/", "/mɛt/", "/mɛt/", "conocer, reunirse", 1, "A1", "vowel_change", "Pasado y participio son 'met' (con una sola 'e').", "new colleagues at the summit", "nuevos colegas en la cumbre"),
    ("send", "sends", "sent", "sent", "sending", "/sɛnd/", "/sɛnt/", "/sɛnt/", "enviar, mandar", 1, "A1", "vowel_change", "La 'd' final cambia a 't' en pasado y participio: 'sent'.", "important emails promptly", "correos importantes puntualmente"),
    ("spend", "spends", "spent", "spent", "spending", "/spɛnd/", "/spɛnt/", "/spɛnt/", "gastar, pasar (tiempo)", 1, "A2", "vowel_change", "La 'd' final cambia a 't': 'spent'.", "quality time with loved ones", "tiempo de calidad con seres queridos"),
    ("lend", "lends", "lent", "lent", "lending", "/lɛnd/", "/lɛnt/", "/lɛnt/", "prestar", 1, "B1", "vowel_change", "'Lend' es prestar; 'borrow' es pedir prestado.", "books to friends willingly", "libros a amigos con gusto"),
    ("build", "builds", "built", "built", "building", "/bɪld/", "/bɪlt/", "/bɪlt/", "construir, edificar", 1, "A2", "vowel_change", "La 'd' final cambia a 't': 'built'.", "modern sustainable homes", "hogares sostenibles modernos"),
    ("lose", "loses", "lost", "lost", "losing", "/luːz/", "/lɒst/", "/lɒst/", "perder", 1, "A2", "vowel_change", "No confundir 'lose' (verbo) con 'loose' (adjetivo holgado).", "keys frequently around the house", "las llaves frecuentemente en casa"),
    ("shoot", "shoots", "shot", "shot", "shooting", "/ʃuːt/", "/ʃɒt/", "/ʃɒt/", "disparar, filmar", 1, "B1", "vowel_change", "Pasado y participio son 'shot'.", "scenes for the documentary", "escenas para el documental"),
    ("understand", "understands", "understood", "understood", "understanding", "/ˌʌn.dəˈstænd/", "/ˌʌn.dəˈstʊd/", "/ˌʌn.dəˈstʊd/", "comprender, entender", 1, "A2", "vowel_change", "Sigue el patrón de 'stand' -> 'stood'.", "complex grammar rules quickly", "reglas gramaticales complejas rápidamente"),
    ("stand", "stands", "stood", "stood", "standing", "/stænd/", "/stʊd/", "/stʊd/", "estar de pie, soportar", 1, "A2", "vowel_change", "Pasado y participio son 'stood'.", "in line patiently", "en la fila con paciencia"),
    ("win", "wins", "won", "won", "winning", "/wɪn/", "/wʌn/", "/wʌn/", "ganar, vencer", 1, "A2", "vowel_change", "'Won' se pronuncia exactamente igual que el número 'one' (/wʌn/).", "the championship proudly", "el campeonato con orgullo"),
    ("pay", "pays", "paid", "paid", "paying", "/peɪ/", "/peɪd/", "/peɪd/", "pagar", 1, "A2", "vowel_change", "Se escribe 'paid', no 'payed'.", "the bill on time", "la cuenta a tiempo"),
    ("lay", "lays", "laid", "laid", "laying", "/leɪ/", "/leɪd/", "/leɪd/", "poner, colocar", 1, "B1", "vowel_change", "'Lay' requiere objeto (lay the book); 'lie' no lo requiere.", "the foundation for growth", "los cimientos para el crecimiento"),
    ("lie", "lies", "lay", "lain", "lying", "/laɪ/", "/leɪ/", "/leɪn/", "recostarse, yacer", 1, "B2", "vowel_change", "El pasado de recostarse es 'lay' y participio 'lain'.", "down to rest a while", "para descansar un momento"),
    ("sell", "sells", "sold", "sold", "selling", "/sɛl/", "/soʊld/", "/soʊld/", "vender", 1, "A2", "vowel_change", "Pasado y participio son 'sold'.", "handcrafted goods online", "artesanías por internet"),
    ("open", "opens", "opened", "opened", "opening", "/ˈoʊ.pən/", "/ˈoʊ.pənd/", "/ˈoʊ.pənd/", "abrir", 0, "A1", "regular_ed", "La '-ed' se pronuncia como /d/.", "the windows to let the light in", "las ventanas para dejar entrar la luz"),
    ("hear", "hears", "heard", "heard", "hearing", "/hɪər/", "/hɜːrd/", "/hɜːrd/", "oír, escuchar", 1, "A2", "vowel_change", "'Heard' se pronuncia /hɜːrd/, no /hɪərd/.", "the gentle music clearly", "la suave música con claridad"),
    ("mean", "means", "meant", "meant", "meaning", "/miːn/", "/mɛnt/", "/mɛnt/", "significar, pretender", 1, "B1", "vowel_change", "'Meant' rima con 'tent' (/mɛnt/).", "to do no harm", "no hacer ningún daño"),
    ("set", "sets", "set", "set", "setting", "/sɛt/", "/sɛt/", "/sɛt/", "establecer, ajustar", 1, "B1", "invariable", "Verbo invariable: las 3 formas son idénticas (set, set, set).", "clear goals for success", "metas claras para el éxito"),
    ("put", "puts", "put", "put", "putting", "/pʊt/", "/pʊt/", "/pʊt/", "poner, colocar", 1, "A1", "invariable", "Verbo invariable: put, put, put. No agregues '-ed'.", "everything in its place", "todo en su lugar"),
    ("cut", "cuts", "cut", "cut", "cutting", "/kʌt/", "/kʌt/", "/kʌt/", "cortar", 1, "A1", "invariable", "Invariable: cut, cut, cut.", "the ribbon at the opening", "la cinta en la inauguración"),
    ("hit", "hits", "hit", "hit", "hitting", "/hɪt/", "/hɪt/", "/hɪt/", "golpear, acertar", 1, "A2", "invariable", "Invariable: hit, hit, hit.", "the target with accuracy", "el blanco con precisión"),
    ("let", "lets", "let", "let", "letting", "/lɛt/", "/lɛt/", "/lɛt/", "permitir, dejar", 1, "A2", "invariable", "Invariable: let, let, let.", "the fresh air into the room", "el aire fresco en la habitación"),
    ("cost", "costs", "cost", "cost", "costing", "/kɒst/", "/kɒst/", "/kɒst/", "costar", 1, "A2", "invariable", "Invariable: cost, cost, cost. 'Costed' se usa solo en contabilidad.", "a reasonable price", "un precio razonable"),
    ("hurt", "hurts", "hurt", "hurt", "hurting", "/hɜːrt/", "/hɜːrt/", "/hɜːrt/", "doler, herir", 1, "A2", "invariable", "Invariable: hurt, hurt, hurt. Evita 'hurted'.", "no one in the process", "a nadie en el proceso"),
    ("shut", "shuts", "shut", "shut", "shutting", "/ʃʌt/", "/ʃʌt/", "/ʃʌt/", "cerrar", 1, "A2", "invariable", "Invariable: shut, shut, shut.", "the heavy doors firmly", "las puertas pesadas con firmeza"),
    ("spread", "spreads", "spread", "spread", "spreading", "/sprɛd/", "/sprɛd/", "/sprɛd/", "extender, difundir", 1, "B1", "invariable", "Invariable: spread, spread, spread.", "positivity in the community", "positivismo en la comunidad"),
    ("bet", "bets", "bet", "bet", "betting", "/bɛt/", "/bɛt/", "/bɛt/", "apostar", 1, "B1", "invariable", "Invariable: bet, bet, bet.", "on the winning horse", "por el caballo ganador"),

    # --- VERBOS REGULARES CLAVE Y AVANZADOS (A1 - C1) ---
    ("accept", "accepts", "accepted", "accepted", "accepting", "/əkˈsɛpt/", "/əkˈsɛp.tɪd/", "/əkˈsɛp.tɪd/", "aceptar", 0, "A2", "regular_ed", "Termina en 't', por lo que '-ed' se pronuncia como /-ɪd/.", "the challenging offer", "la desafiante oferta"),
    ("achieve", "achieves", "achieved", "achieved", "achieving", "/əˈtʃiːv/", "/əˈtʃiːvd/", "/əˈtʃiːvd/", "lograr, alcanzar", 0, "B1", "regular_ed", "La '-ed' se pronuncia como /d/ suave.", "remarkable milestones this year", "hitos notables este año"),
    ("allow", "allows", "allowed", "allowed", "allowing", "/əˈlaʊ/", "/əˈlaʊd/", "/əˈlaʊd/", "permitir, autorizar", 0, "A2", "regular_ed", "La '-ed' suena como /d/.", "flexible working schedules", "horarios de trabajo flexibles"),
    ("appear", "appears", "appeared", "appeared", "appearing", "/əˈpɪər/", "/əˈpɪərd/", "/əˈpɪərd/", "aparecer, parecer", 0, "A2", "regular_ed", "La '-ed' se pronuncia como /d/.", "calm despite the pressure", "tranquilo a pesar de la presión"),
    ("apply", "applies", "applied", "applied", "applying", "/əˈplaɪ/", "/əˈplaɪd/", "/əˈplaɪd/", "aplicar, postularse", 0, "A2", "regular_ed", "La 'y' cambia a 'i' en 'applied'.", "for the university scholarship", "para la beca universitaria"),
    ("believe", "believes", "believed", "believed", "believing", "/bɪˈliːv/", "/bɪˈliːvd/", "/bɪˈliːvd/", "creer, confiar", 0, "A1", "regular_ed", "La '-ed' suena como /d/.", "in honesty and hard work", "en la honestidad y el trabajo duro"),
    ("belong", "belongs", "belonged", "belonged", "belonging", "/bɪˈlɒŋ/", "/bɪˈlɒŋd/", "/bɪˈlɒŋd/", "pertenecer", 0, "A2", "regular_ed", "Normalmente va seguido de la preposición 'to': 'belong to'.", "to an international association", "a una asociación internacional"),
    ("change", "changes", "changed", "changed", "changing", "/tʃeɪndʒ/", "/tʃeɪndʒd/", "/tʃeɪndʒd/", "cambiar, transformar", 0, "A1", "regular_ed", "La '-ed' suena como /d/.", "strategies when necessary", "estrategias cuando sea necesario"),
    ("consider", "considers", "considered", "considered", "considering", "/kənˈsɪd.ər/", "/kənˈsɪd.ərd/", "/kənˈsɪd.ərd/", "considerar, evaluar", 0, "B1", "regular_ed", "La '-ed' suena como /d/.", "all perspectives thoughtfully", "todas las perspectivas detenidamente"),
    ("continue", "continues", "continued", "continued", "continuing", "/kənˈtɪn.juː/", "/kənˈtɪn.juːd/", "/kənˈtɪn.juːd/", "continuar, seguir", 0, "A2", "regular_ed", "La '-ed' se pronuncia como /d/.", "the research next semester", "la investigación el próximo semestre"),
    ("create", "creates", "created", "created", "creating", "/kriˈeɪt/", "/kriˈeɪ.tɪd/", "/kriˈeɪ.tɪd/", "crear, originar", 0, "A2", "regular_ed", "Termina en 't', por lo que '-ed' se pronuncia /-ɪd/.", "innovative software solutions", "soluciones de software innovadoras"),
    ("decide", "decides", "decided", "decided", "deciding", "/dɪˈsaɪd/", "/dɪˈsaɪ.dɪd/", "/dɪˈsaɪ.dɪd/", "decidir, resolver", 0, "A2", "regular_ed", "Termina en 'd', por lo que '-ed' se pronuncia /-ɪd/.", "the final team strategy", "la estrategia final del equipo"),
    ("develop", "develops", "developed", "developed", "developing", "/dɪˈvɛl.əp/", "/dɪˈvɛl.əpt/", "/dɪˈvɛl.əpt/", "desarrollar, crear", 0, "B1", "regular_ed", "La '-ed' tras /p/ sorda suena como /t/: /dɪˈvɛl.əpt/.", "mobile applications natively", "aplicaciones móviles de forma nativa"),
    ("explain", "explains", "explained", "explained", "explaining", "/ɪkˈspleɪn/", "/ɪkˈspleɪnd/", "/ɪkˈspleɪnd/", "explicar, aclarar", 0, "A2", "regular_ed", "La '-ed' suena como /d/.", "the scientific process clearly", "el proceso científico con claridad"),
    ("follow", "follows", "followed", "followed", "following", "/ˈfɒl.oʊ/", "/ˈfɒl.oʊd/", "/ˈfɒl.oʊd/", "seguir, acatar", 0, "A1", "regular_ed", "La '-ed' suena como /d/.", "the guidelines rigorously", "las directrices rigurosamente"),
    ("happen", "happens", "happened", "happened", "happening", "/ˈhæp.ən/", "/ˈhæp.ənd/", "/ˈhæp.ənd/", "suceder, ocurrir", 0, "A2", "regular_ed", "Es un verbo intransitivo: no se utiliza en voz pasiva.", "for a meaningful reason", "por una razón significativa"),
    ("help", "helps", "helped", "helped", "helping", "/hɛlp/", "/hɛlpt/", "/hɛlpt/", "ayudar, colaborar", 0, "A1", "regular_ed", "La '-ed' suena como /t/: /hɛlpt/.", "new members adapt smoothly", "a los nuevos miembros a adaptarse fácilmente"),
    ("improve", "improves", "improved", "improved", "improving", "/ɪmˈpruːv/", "/ɪmˈpruːvd/", "/ɪmˈpruːvd/", "mejorar, progresar", 0, "B1", "regular_ed", "La '-ed' suena como /d/.", "pronunciation skills every day", "habilidades de pronunciación cada día"),
    ("include", "includes", "included", "included", "including", "/ɪnˈkluːd/", "/ɪnˈkluː.dɪd/", "/ɪnˈkluː.dɪd/", "incluir, abarcar", 0, "A2", "regular_ed", "Como termina en 'd', el sufijo '-ed' suena como /-ɪd/.", "all relevant documentation", "toda la documentación pertinente"),
    ("increase", "increases", "increased", "increased", "increasing", "/ɪnˈkriːs/", "/ɪnˈkriːst/", "/ɪnˈkriːst/", "aumentar, incrementar", 0, "B1", "regular_ed", "La '-ed' tras /s/ suena como /t/: /ɪnˈkriːst/.", "efficiency across departments", "la eficiencia en todos los departamentos"),
    ("manage", "manages", "managed", "managed", "managing", "/ˈmæn.ɪdʒ/", "/ˈmæn.ɪdʒd/", "/ˈmæn.ɪdʒd/", "gestionar, administrar, lograr", 0, "B1", "regular_ed", "La '-ed' suena como /d/.", "complex projects effectively", "proyectos complejos con eficacia"),
    ("provide", "provides", "provided", "provided", "providing", "/prəˈvaɪd/", "/prəˈvaɪ.dɪd/", "/prəˈvaɪ.dɪd/", "proporcionar, proveer", 0, "B1", "regular_ed", "Termina en 'd', la '-ed' se pronuncia /-ɪd/.", "reliable resources to users", "recursos confiables a los usuarios"),
    ("reach", "reaches", "reached", "reached", "reaching", "/riːtʃ/", "/riːtʃt/", "/riːtʃt/", "alcanzar, llegar a", 0, "B1", "regular_ed", "La '-ed' tras /tʃ/ suena como /t/: /riːtʃt/.", "new professional summits", "nuevas cumbres profesionales"),
    ("receive", "receives", "received", "received", "receiving", "/rɪˈsiːv/", "/rɪˈsiːvd/", "/rɪˈsiːvd/", "recibir, acoger", 0, "A2", "regular_ed", "La '-ed' suena como /d/.", "positive feedback from clients", "comentarios positivos de los clientes"),
    ("remember", "remembers", "remembered", "remembered", "remembering", "/rɪˈmɛm.bər/", "/rɪˈmɛm.bərd/", "/rɪˈmɛm.bərd/", "recordar", 0, "A1", "regular_ed", "La '-ed' suena como /d/.", "every single verb conjugation", "cada conjugación verbal"),
    ("support", "supports", "supported", "supported", "supporting", "/səˈpɔːrt/", "/səˈpɔːr.tɪd/", "/səˈpɔːr.tɪd/", "apoyar, respaldar", 0, "B1", "regular_ed", "Termina en 't', la '-ed' se pronuncia /-ɪd/.", "open source communities enthusiastically", "a comunidades de código abierto con entusiasmo"),
    ("undertake", "undertakes", "undertook", "undertaken", "undertaking", "/ˌʌn.dəˈteɪk/", "/ˌʌn.dəˈtʊk/", "/ˌʌn.dəˈteɪ.kən/", "emprender, asumir", 1, "C1", "en_participle", "Verbo formal de nivel C1; sigue el patrón de 'take'.", "challenging scientific research", "desafiantes investigaciones científicas"),
    ("withhold", "withholds", "withheld", "withheld", "withholding", "/wɪðˈhoʊld/", "/wɪðˈhɛld/", "/wɪðˈhɛld/", "retener, ocultar", 1, "C1", "vowel_change", "Verbo avanzado de nivel C1; sigue el patrón de 'hold'.", "sensitive confidential information", "información confidencial delicada"),

    # --- TANDA COMPLEMENTARIA DE VERBOS IRREGULARES Y REGULARES VITALES (65 VERBOS) ---
    ("blow", "blows", "blew", "blown", "blowing", "/bloʊ/", "/bluː/", "/bloʊn/", "soplar, volar", 1, "A2", "en_participle", "Pasado 'blew' (/bluː/), participio 'blown'.", "the candles on the birthday cake", "las velas en el pastel de cumpleaños"),
    ("deal", "deals", "dealt", "dealt", "dealing", "/diːl/", "/dɛlt/", "/dɛlt/", "tratar, negociar, lidiar", 1, "B1", "vowel_change", "Pasado y participio son 'dealt' (/dɛlt/), rima con 'felt'.", "with complex negotiations calmly", "con negociaciones complejas con calma"),
    ("feed", "feeds", "fed", "fed", "feeding", "/fiːd/", "/fɛd/", "/fɛd/", "alimentar, dar de comer", 1, "A2", "vowel_change", "Pasado y participio 'fed' (con una sola 'e').", "the animals at the animal shelter", "a los animales en el refugio de animales"),
    ("forgive", "forgives", "forgave", "forgiven", "forgiving", "/fərˈɡɪv/", "/fərˈɡeɪv/", "/fərˈɡɪv.ən/", "perdonar", 1, "B1", "en_participle", "Sigue el patrón de 'give' -> 'forgave' y 'forgiven'.", "past misunderstandings sincerely", "malentendidos pasados sinceramente"),
    ("forbid", "forbids", "forbade", "forbidden", "forbidding", "/fərˈbɪd/", "/fərˈbeɪd/", "/fərˈbɪd.ən/", "prohibir", 1, "B2", "en_participle", "Pasado 'forbade' y participio 'forbidden' (con doble 'd').", "smoking in all enclosed spaces", "fumar en todos los espacios cerrados"),
    ("hang", "hangs", "hung", "hung", "hanging", "/hæŋ/", "/hʌŋ/", "/hʌŋ/", "colgar, suspender", 1, "B1", "vowel_change", "Para colgar cuadros se usa 'hung'; 'hanged' se usa solo para ejecuciones.", "the famous artwork on the gallery wall", "la famosa obra de arte en la pared de la galería"),
    ("light", "lights", "lit", "lit", "lighting", "/laɪt/", "/lɪt/", "/lɪt/", "encender, iluminar", 1, "B1", "vowel_change", "En pasado se prefiere 'lit' sobre 'lighted'.", "the fireplace on cold winter nights", "la chimenea en frías noches de invierno"),
    ("ride", "rides", "rode", "ridden", "riding", "/raɪd/", "/roʊd/", "/ˈrɪd.ən/", "montar, pasear, viajar", 1, "A1", "en_participle", "Pasado 'rode' y participio 'ridden' (con doble 'd').", "bicycles along the scenic path", "bicicletas a lo largo del sendero panorámico"),
    ("rise", "rises", "rose", "risen", "rising", "/raɪz/", "/roʊz/", "/ˈrɪz.ən/", "elevarse, salir el sol, aumentar", 1, "B1", "en_participle", "'Rise' es intransitivo (sin objeto); 'raise' es transitivo ('raise your hand').", "above difficulties with dignity", "por encima de las dificultades con dignidad"),
    ("seek", "seeks", "sought", "sought", "seeking", "/siːk/", "/sɔːt/", "/sɔːt/", "buscar, procurar", 1, "B2", "vowel_change", "Pasado y participio son 'sought' (/sɔːt/), con 'gh' muda.", "new innovative opportunities", "nuevas oportunidades innovadoras"),
    ("shine", "shines", "shone", "shone", "shining", "/ʃaɪn/", "/ʃɒn/", "/ʃɒn/", "brillar, resplandecer", 1, "B1", "vowel_change", "Pasado 'shone' (/ʃɒn/ o /ʃoʊn/). 'Shined' se usa para lustrar zapatos.", "brightly in the clear summer sky", "intensamente en el despejado cielo de verano"),
    ("sink", "sinks", "sank", "sunk", "sinking", "/sɪŋk/", "/sæŋk/", "/sʌŋk/", "hundirse", 1, "B1", "vowel_change", "Secuencia clásica i-a-u: sink, sank, sunk.", "slowly beneath the gentle ocean waves", "lentamente bajo las suaves olas del océano"),
    ("slide", "slides", "slid", "slid", "sliding", "/slaɪd/", "/slɪd/", "/slɪd/", "deslizarse, resbalar", 1, "B1", "vowel_change", "Pasado y participio son 'slid'.", "smoothly on the fresh icy surface", "suavemente sobre la fresca superficie helada"),
    ("stick", "sticks", "stuck", "stuck", "sticking", "/stɪk/", "/stʌk/", "/stʌk/", "pegar, adherir, quedarse atascado", 1, "B1", "vowel_change", "Pasado y participio son 'stuck'.", "to the original commitment", "al compromiso original"),
    ("strike", "strikes", "struck", "struck", "striking", "/straɪk/", "/strʌk/", "/strʌk/", "golpear, chocar, estar en huelga", 1, "B1", "vowel_change", "Pasado 'struck'; participio 'struck' (o 'stricken' en sentido figurado).", "a fair balance in all decisions", "un equilibrio justo en todas las decisiones"),
    ("sweep", "sweeps", "swept", "swept", "sweeping", "/swiːp/", "/swɛpt/", "/swɛpt/", "barrer, arrasar", 1, "B1", "vowel_change", "Pasado y participio son 'swept'.", "the fallen leaves off the porch", "las hojas caídas del porche"),
    ("swing", "swings", "swung", "swung", "swinging", "/swɪŋ/", "/swʌŋ/", "/swʌŋ/", "columpiar, balancear", 1, "B2", "vowel_change", "Pasado y participio son 'swung'.", "the baseball bat with confidence", "el bate de béisbol con confianza"),
    ("wind", "winds", "wound", "wound", "winding", "/waɪnd/", "/waʊnd/", "/waʊnd/", "enrollar, dar cuerda, serpentear", 1, "B2", "vowel_change", "Como verbo se pronuncia /waɪnd/ y su pasado es 'wound' (/waʊnd/).", "the vintage watch every morning", "el reloj antiguo cada mañana"),
    ("withdraw", "withdraws", "withdrew", "withdrawn", "withdrawing", "/wɪðˈdrɔː/", "/wɪðˈdruː/", "/wɪðˈdrɔːn/", "retirar, retirar fondos", 1, "C1", "en_participle", "Sigue el patrón de 'draw' -> 'withdrew' y 'withdrawn'.", "cash from the local bank ATM", "efectivo del cajero automático local"),
    ("arise", "arises", "arose", "arisen", "arising", "/əˈraɪz/", "/əˈroʊz/", "/əˈrɪz.ən/", "surgir, presentarse", 1, "B2", "en_participle", "Verbo formal; pasado 'arose' y participio 'arisen'.", "when unexpected circumstances occur", "cuando ocurren circunstancias inesperadas"),
    ("bend", "bends", "bent", "bent", "bending", "/bɛnd/", "/bɛnt/", "/bɛnt/", "doblar, curvar", 1, "B1", "vowel_change", "La 'd' final cambia a 't': 'bent'.", "the flexible metal wire into shape", "el alambre de metal flexible en forma"),
    ("bleed", "bleeds", "bled", "bled", "bleeding", "/bliːd/", "/blɛd/", "/blɛd/", "sangrar", 1, "B2", "vowel_change", "Pasado y participio son 'bled'.", "lightly after the minor scratch", "ligeramente tras el pequeño rasguño"),
    ("burst", "bursts", "burst", "burst", "bursting", "/bɜːrst/", "/bɜːrst/", "/bɜːrst/", "estallar, reventar", 1, "B2", "invariable", "Invariable: burst, burst, burst. No existe 'bursted'.", "into tears of deep joy", "en llanto de profunda alegría"),
    ("cling", "clings", "clung", "clung", "clinging", "/klɪŋ/", "/klʌŋ/", "/klʌŋ/", "aferrarse, adherirse", 1, "B2", "vowel_change", "Pasado y participio son 'clung'.", "to ancestral traditions with pride", "a las tradiciones ancestrales con orgullo"),
    ("creep", "creeps", "crept", "crept", "creeping", "/kriːp/", "/krɛpt/", "/krɛpt/", "arrastrarse, deslizarse sigilosamente", 1, "B2", "vowel_change", "Pasado y participio son 'crept'.", "quietly into the darkened room", "silenciosamente en la habitación a oscuras"),
    ("dig", "digs", "dug", "dug", "digging", "/dɪɡ/", "/dʌɡ/", "/dʌɡ/", "cavar, excavar", 1, "A2", "vowel_change", "Pasado y participio son 'dug'.", "a deep hole for planting trees", "un hoyo profundo para plantar árboles"),
    ("flee", "flees", "fled", "fled", "fleeing", "/fliː/", "/flɛd/", "/flɛd/", "huir, escapar", 1, "B2", "vowel_change", "Pasado y participio son 'fled'.", "the dangerous disaster area quickly", "el área de peligro rápidamente"),
    ("fling", "flings", "flung", "flung", "flinging", "/flɪŋ/", "/flʌŋ/", "/flʌŋ/", "arrojar, lanzar con fuerza", 1, "C1", "vowel_change", "Pasado y participio son 'flung'.", "the old coat onto the sofa", "el viejo abrigo sobre el sofá"),
    ("kneel", "kneels", "knelt", "knelt", "kneeling", "/niːl/", "/nɛlt/", "/nɛlt/", "arrodillarse", 1, "B1", "vowel_change", "La 'k' es muda. Pasado y participio 'knelt'.", "on the soft grass to pray", "en el pasto suave para orar"),
    ("spin", "spins", "spun", "spun", "spinning", "/spɪn/", "/spʌn/", "/spʌn/", "girar rápidamente, hilar", 1, "B1", "vowel_change", "Pasado y participio son 'spun'.", "the wheel with enthusiasm", "la rueda con entusiasmo"),
    ("spit", "spits", "spat", "spat", "spitting", "/spɪt/", "/spæt/", "/spæt/", "escupir", 1, "B2", "vowel_change", "Pasado y participio 'spat'.", "out the bitter medicine immediately", "la amarga medicina inmediatamente"),
    ("split", "splits", "split", "split", "splitting", "/splɪt/", "/splɪt/", "/splɪt/", "dividir, partir", 1, "B1", "invariable", "Invariable: split, split, split.", "the bill equally among friends", "la cuenta equitativamente entre amigos"),
    ("sting", "stings", "stung", "stung", "stinging", "/stɪŋ/", "/stʌŋ/", "/stʌŋ/", "picar (insecto), arder", 1, "B2", "vowel_change", "Pasado y participio son 'stung'.", "sharply on the exposed skin", "agudamente en la piel expuesta"),
    ("stink", "stinks", "stank", "stunk", "stinking", "/stɪŋk/", "/stæŋk/", "/stʌŋk/", "apestar, oler mal", 1, "B2", "vowel_change", "Secuencia i-a-u: stink, stank, stunk.", "terribly after the storm", "terriblemente después de la tormenta"),
    ("swear", "swears", "swore", "sworn", "swearing", "/swɛər/", "/swɔːr/", "/swɔːrn/", "jurar, decir palabrotas", 1, "B2", "en_participle", "Pasado 'swore', participio 'sworn'.", "an oath to protect truth", "un juramento para proteger la verdad"),
    ("tread", "treads", "trod", "trodden", "treading", "/trɛd/", "/trɒd/", "/ˈtrɒd.ən/", "pisar, andar sobre", 1, "C1", "en_participle", "Pasado 'trod', participio 'trodden'.", "carefully on delicate matters", "con cuidado en asuntos delicados"),
    ("weep", "weeps", "wept", "wept", "weeping", "/wiːp/", "/wɛpt/", "/wɛpt/", "llorar, sollozar", 1, "B2", "vowel_change", "Pasado y participio son 'wept'.", "with relief after hearing good news", "con alivio tras escuchar buenas noticias"),
    ("start", "starts", "started", "started", "starting", "/stɑːrt/", "/ˈstɑːr.tɪd/", "/ˈstɑːr.tɪd/", "comenzar, arrancar", 0, "A1", "regular_ed", "Termina en 't', '-ed' se pronuncia /-ɪd/.", "a promising new business project", "un prometedor nuevo proyecto de negocio"),
    ("stop", "stops", "stopped", "stopped", "stopping", "/stɒp/", "/stɒpt/", "/stɒpt/", "detener, parar", 0, "A1", "regular_ed", "Duplica la 'p': 'stopped'. La '-ed' suena como /t/.", "at the red traffic light", "en la luz roja del semáforo"),
    ("clean", "cleans", "cleaned", "cleaned", "cleaning", "/kliːn/", "/kliːnd/", "/kliːnd/", "limpiar", 0, "A1", "regular_ed", "La '-ed' se pronuncia como /d/.", "the entire house before visitors arrive", "toda la casa antes de que lleguen visitas"),
    ("cook", "cooks", "cooked", "cooked", "cooking", "/kʊk/", "/kʊkt/", "/kʊkt/", "cocinar", 0, "A1", "regular_ed", "La '-ed' suena como /t/: /kʊkt/.", "traditional recipes with natural spices", "recetas tradicionales con especias naturales"),
    ("travel", "travels", "traveled", "traveled", "traveling", "/ˈtræv.əl/", "/ˈtræv.əld/", "/ˈtræv.əld/", "viajar", 0, "A2", "regular_ed", "En inglés británico suele escribirse con doble 'l': 'travelled'.", "to beautiful historical cities", "a hermosas ciudades históricas"),
    ("listen", "listens", "listened", "listened", "listening", "/ˈlɪs.ən/", "/ˈlɪs.ənd/", "/ˈlɪs.ənd/", "escuchar", 0, "A1", "regular_ed", "La 't' es muda (/ˈlɪs.ən/). Suele requerir 'to': 'listen to'.", "to classical music attentively", "música clásica con atención"),
    ("watch", "watches", "watched", "watched", "watching", "/wɒtʃ/", "/wɒtʃt/", "/wɒtʃt/", "mirar, observar (pantalla)", 0, "A1", "regular_ed", "La '-ed' suena como /t/: /wɒtʃt/.", "interesting scientific documentaries", "documentales científicos interesantes"),
    ("live", "lives", "lived", "lived", "living", "/lɪv/", "/lɪvd/", "/lɪvd/", "vivir, residir", 0, "A1", "regular_ed", "Como verbo se pronuncia /lɪv/ (con 'i' corta); en pasado 'lived'.", "in a vibrant multicultural city", "en una vibrante ciudad multicultural"),
    ("play", "plays", "played", "played", "playing", "/pleɪ/", "/pleɪd/", "/pleɪd/", "jugar, tocar (instrumento)", 0, "A1", "regular_ed", "La '-ed' suena como /d/.", "the acoustic guitar with passion", "la guitarra acústica con pasión"),
    ("talk", "talks", "talked", "talked", "talking", "/tɔːk/", "/tɔːkt/", "/tɔːkt/", "hablar, conversar", 0, "A1", "regular_ed", "La 'l' es muda (/tɔːk/). La '-ed' suena como /t/.", "openly about important goals", "abiertamente sobre metas importantes"),
    ("walk", "walks", "walked", "walked", "walking", "/wɔːk/", "/wɔːkt/", "/wɔːkt/", "caminar, pasear", 0, "A1", "regular_ed", "La 'l' es muda (/wɔːk/). La '-ed' suena como /t/.", "through the quiet green park", "por el tranquilo parque verde"),
    ("wait", "waits", "waited", "waited", "waiting", "/weɪt/", "/ˈweɪ.tɪd/", "/ˈweɪ.tɪd/", "esperar", 0, "A1", "regular_ed", "Termina en 't', '-ed' se pronuncia /-ɪd/.", "patiently for the doctor's appointment", "pacientemente para la cita del médico"),
    ("love", "loves", "loved", "loved", "loving", "/lʌv/", "/lʌvd/", "/lʌvd/", "amar, encantar", 0, "A1", "regular_ed", "La '-ed' se pronuncia como /d/.", "reading engaging literature", "leer literatura atractiva"),
    ("enjoy", "enjoys", "enjoyed", "enjoyed", "enjoying", "/ɪnˈdʒɔɪ/", "/ɪnˈdʒɔɪd/", "/ɪnˈdʒɔɪd/", "disfrutar, gozar de", 0, "A1", "regular_ed", "La '-ed' suena como /d/.", "sunny afternoons with good company", "tardes soleadas con buena compañía"),
    ("study", "studies", "studied", "studied", "studying", "/ˈstʌd.i/", "/ˈstʌd.id/", "/ˈstʌd.id/", "estudiar", 0, "A1", "regular_ed", "La 'y' cambia a 'i' en 'studied' y 'studies'.", "advanced computer science topics", "temas avanzados de informática"),
    ("hope", "hopes", "hoped", "hoped", "hoping", "/hoʊp/", "/hoʊpt/", "/hoʊpt/", "esperar, desear", 0, "A1", "regular_ed", "No confundir 'hoping' (esperar) con 'hopping' (saltar).", "for peaceful and prosperous times", "por tiempos pacíficos y prósperos"),
    ("share", "shares", "shared", "shared", "sharing", "/ʃɛər/", "/ʃɛərd/", "/ʃɛərd/", "compartir", 0, "A1", "regular_ed", "La '-ed' se pronuncia como /d/.", "knowledge with eager students", "conocimiento con estudiantes entusiastas"),
    ("practice", "practices", "practiced", "practiced", "practicing", "/ˈpræk.tɪs/", "/ˈpræk.tɪst/", "/ˈpræk.tɪst/", "practicar, ejercitar", 0, "A1", "regular_ed", "En inglés británico el verbo suele escribirse con 's': 'practise'.", "irregular verbs every afternoon", "verbos irregulares cada tarde"),
    ("agree", "agrees", "agreed", "agreed", "agreeing", "/əˈɡriː/", "/əˈɡriːd/", "/əˈɡriːd/", "estar de acuerdo, concordar", 0, "A2", "regular_ed", "Se dice 'I agree', nunca 'I am agree'.", "with the sensible proposal", "con la sensata propuesta"),
    ("discover", "discovers", "discovered", "discovered", "discovering", "/dɪˈskʌv.ər/", "/dɪˈskʌv.ərd/", "/dɪˈskʌv.ərd/", "descubrir, hallar", 0, "A2", "regular_ed", "La '-ed' se pronuncia como /d/.", "fascinating historical evidence", "evidencia histórica fascinante"),
    ("expect", "expects", "expected", "expected", "expecting", "/ɪkˈspɛkt/", "/ɪkˈspɛk.tɪd/", "/ɪkˈspɛk.tɪd/", "esperar (con certeza), prever", 0, "B1", "regular_ed", "Termina en 't', '-ed' se pronuncia /-ɪd/.", "great results from the new policy", "grandes resultados de la nueva política"),
    ("prepare", "prepares", "prepared", "prepared", "preparing", "/prɪˈpɛər/", "/prɪˈpɛərd/", "/prɪˈpɛərd/", "preparar, disponer", 0, "A2", "regular_ed", "La '-ed' suena como /d/.", "thoroughly for the certification exam", "a fondo para el examen de certificación"),
    ("protect", "protects", "protected", "protected", "protecting", "/prəˈtɛkt/", "/prəˈtɛk.tɪd/", "/prəˈtɛk.tɪd/", "proteger, resguardar", 0, "A2", "regular_ed", "Termina en 't', '-ed' se pronuncia /-ɪd/.", "endangered species in their habitat", "especies en peligro en su hábitat"),
    ("suggest", "suggests", "suggested", "suggested", "suggesting", "/səˈdʒɛst/", "/səˈdʒɛs.tɪd/", "/səˈdʒɛs.tɪd/", "sugerir, proponer", 0, "B1", "regular_ed", "Termina en 't', '-ed' se pronuncia /-ɪd/.", "effective practical alternatives", "alternativas prácticas efectivas"),
    ("survive", "survives", "survived", "survived", "surviving", "/sərˈvaɪv/", "/sərˈvaɪvd/", "/sərˈvaɪvd/", "sobrevivir, subsistir", 0, "B1", "regular_ed", "La '-ed' suena como /d/.", "harsh winter conditions gracefully", "condiciones invernales duras con elegancia"),
    ("challenge", "challenges", "challenged", "challenged", "challenging", "/ˈtʃæl.ɪndʒ/", "/ˈtʃæl.ɪndʒd/", "/ˈtʃæl.ɪndʒd/", "desafiar, retar", 0, "B2", "regular_ed", "La '-ed' suena como /d/.", "conventional wisdom with logic", "la sabiduría convencional con lógica"),
    ("confirm", "confirms", "confirmed", "confirmed", "confirming", "/kənˈfɜːrm/", "/kənˈfɜːrmd/", "/kənˈfɜːrmd/", "confirmar, ratificar", 0, "B1", "regular_ed", "La '-ed' suena como /d/.", "the travel reservations in advance", "las reservaciones de viaje con anticipación"),
    ("maintain", "maintains", "maintained", "maintained", "maintaining", "/meɪnˈteɪn/", "/meɪnˈteɪnd/", "/meɪnˈteɪnd/", "mantener, conservar", 0, "B2", "regular_ed", "La '-ed' suena como /d/.", "high professional standards consistently", "altos estándares profesionales de forma constante"),
    # --- VERBOS COMPLEMENTARIOS Y / Z (Cierre del Abecedario) ---
    ("yawn", "yawns", "yawned", "yawned", "yawning", "/jɔːn/", "/jɔːnd/", "/jɔːnd/", "bostezar", 0, "A2", "regular_ed", "La '-ed' suena suave como /d/: /jɔːnd/.", "during the long lecture", "durante la conferencia larga"),
    ("yell", "yells", "yelled", "yelled", "yelling", "/jɛl/", "/jɛld/", "/jɛld/", "gritar, vociferar", 0, "B1", "regular_ed", "Usa 'yell at' para regañar y 'yell to' para llamar a distancia.", "excitedly at the stadium", "con entusiasmo en el estadio"),
    ("yield", "yields", "yielded", "yielded", "yielding", "/jiːld/", "/ˈjiːl.dɪd/", "/ˈjiːl.dɪd/", "ceder, rendir frutos", 0, "B2", "regular_ed", "Como termina en 'd', '-ed' se pronuncia con sílaba extra /-ɪd/.", "positive investment results", "resultados positivos de inversión"),
    ("zip", "zips", "zipped", "zipped", "zipping", "/zɪp/", "/zɪpt/", "/zɪpt/", "abrochar, cerrar la cremallera", 0, "B1", "regular_ed", "Duplica la 'p' en zipped y zipping. La '-ed' suena /t/.", "up the warm winter jacket", "la cálida chaqueta de invierno"),
    ("zoom", "zooms", "zoomed", "zoomed", "zooming", "/zuːm/", "/zuːmd/", "/zuːmd/", "acercar, desplazarse velozmente", 0, "B1", "regular_ed", "La '-ed' suena como /d/: /zuːmd/.", "in on the fascinating details", "en los detalles fascinantes")
]

# 2. Catálogo curado de Phrasal Verbs clave
PHRASAL_VERBS_CATALOG = [
    ("yield", "yield to", "ceder ante la presión o tentación", "He refused to yield to unfair demands.", "Se negó a ceder ante demandas injustas."),
    ("zip", "zip up", "subir la cremallera por completo", "Zip up your jacket before going outside.", "Sube la cremallera de tu chaqueta antes de salir."),
    ("zoom", "zoom in", "acercar la cámara o imagen", "Zoom in to see the details clearly.", "Acerca la imagen para ver los detalles claramente."),
    ("break", "break down", "descomponerse, averiarse", "The elevator broke down this morning.", "El elevador se descompuso esta mañana."),
    ("break", "break up", "terminar una relación", "They broke up after five years together.", "Terminaron su relación tras cinco años juntos."),
    ("break", "break out", "estallar, desatarse (un conflicto/epidemia)", "A fire broke out in the old warehouse.", "Un incendio se desató en el viejo almacén."),
    ("bring", "bring up", "mencionar un tema, criar a alguien", "She brought up an interesting question.", "Ella mencionó una pregunta interesante."),
    ("bring", "bring about", "provocar, causar", "The new law brought about major reforms.", "La nueva ley provocó grandes reformas."),
    ("call", "call off", "cancelar un evento", "They had to call off the soccer match.", "Tuvieron que cancelar el partido de fútbol."),
    ("call", "call back", "devolver la llamada", "I will call you back in ten minutes.", "Te devolveré la llamada en diez minutos."),
    ("come", "come across", "encontrarse con algo/alguien por casualidad", "I came across an old family portrait.", "Me encontré con un antiguo retrato familiar por casualidad."),
    ("come", "come up with", "idear, inventar una solución", "He came up with a brilliant business idea.", "A él se le ocurrió una idea de negocio brillante."),
    ("get", "get along", "llevarse bien con alguien", "They get along very well with colleagues.", "Se llevan muy bien con sus compañeros."),
    ("get", "get over", "superar una dificultad o enfermedad", "It took weeks to get over that flu.", "Tomó semanas superar esa gripe."),
    ("get", "get by", "arreglárselas, subsistir", "We can get by with this budget.", "Nos las arreglamos con este presupuesto."),
    ("give", "give up", "rendirse, abandonar un hábito", "Never give up on your dreams.", "Nunca te rindas en tus sueños."),
    ("give", "give in", "ceder, capitular", "He finally gave in to their demands.", "Él finalmente cedió ante sus demandas."),
    ("go", "go on", "continuar, seguir", "Please go on with your presentation.", "Por favor continúa con tu presentación."),
    ("go", "go through", "atravesar, examinar a fondo", "She went through a difficult period.", "Ella atravesó un periodo difícil."),
    ("look", "look after", "cuidar de alguien", "She looks after her younger brother.", "Ella cuida de su hermano menor."),
    ("look", "look forward to", "esperar algo con ansias", "I look forward to meeting you.", "Espero con ansias conocerte."),
    ("look", "look up to", "admirar, respetar a alguien", "Young students look up to their teacher.", "Los jóvenes estudiantes admiran a su profesor."),
    ("make", "make up", "inventar, reconciliarse", "They made up after a brief disagreement.", "Se reconciliaron tras un breve desacuerdo."),
    ("make", "make out", "distinguir, descifrar", "I could barely make out the distant signs.", "Apenas pude distinguir las señales lejanas."),
    ("put", "put off", "posponer, postergar", "Do not put off what you can do today.", "No postergues lo que puedes hacer hoy."),
    ("put", "put up with", "tolerar, aguantar", "She refuses to put up with rudeness.", "Ella se rehúsa a tolerar la grosería."),
    ("run", "run out of", "quedarse sin algo", "We ran out of coffee this morning.", "Nos quedamos sin café esta mañana."),
    ("run", "run into", "tropezar con alguien por azar", "I ran into an old classmate yesterday.", "Me encontré con un viejo compañero ayer."),
    ("take", "take off", "despegar (avión), tener éxito repentino", "The plane took off right on schedule.", "El avión despegó justo a tiempo."),
    ("take", "take after", "parecerse a un familiar", "He takes after his grandfather in sports.", "Se parece a su abuelo en los deportes."),
    ("turn", "turn down", "rechazar una oferta, bajar volumen", "She turned down the initial job offer.", "Ella rechazó la oferta de trabajo inicial."),
    ("turn", "turn into", "transformarse en", "A small mistake turned into a big dilemma.", "Un pequeño error se transformó en un gran dilema."),
    ("come", "come back", "volver, regresar", "They came back from vacation yesterday.", "Regresaron de vacaciones ayer."),
    ("find", "find out", "descubrir, enterarse de algo", "We finally found out the real truth.", "Finalmente nos enteramos de la verdad real."),
    ("give", "give away", "regalar, donar, delatar", "They gave away clothes to charity.", "Regalaron ropa a la caridad."),
    ("set", "set up", "establecer, fundar, configurar", "She set up her own tech startup.", "Ella fundó su propia empresa tecnológica."),
    ("stand", "stand out", "destacarse, sobresalir", "Her creative work stood out from the rest.", "Su trabajo creativo se destacó del resto."),
    ("take", "take up", "empezar un pasatiempo/hábito", "He took up photography last summer.", "Él empezó fotografía el verano pasado."),
    ("work", "work out", "hacer ejercicio, resolverse bien", "Things worked out better than expected.", "Las cosas salieron mejor de lo esperado.")
]


def generate_tenses_for_verb(base, third, past, pp, gerund, spanish_inf, ctx_en, ctx_es):
    """
    Genera los 12 tiempos verbales + Voz Pasiva + Condicional
    con oraciones afirmativas, negativas e interrogativas en inglés y traducción española natural.
    """
    first_letter = spanish_inf[0] if spanish_inf else 'a'
    
    tenses = [
        # 1. Present Simple
        (
            "PRESENT_SIMPLE", "Present Simple", "Presente Simple",
            f"They {base} {ctx_en}.",
            f"They do not {base} {ctx_en}.",
            f"Do they {base} {ctx_en}?",
            f"Ellos suelen {spanish_inf} {ctx_es}."
        ),
        # 2. Present Continuous
        (
            "PRESENT_CONTINUOUS", "Present Continuous", "Presente Continuo",
            f"They are {gerund} {ctx_en} right now.",
            f"They are not {gerund} {ctx_en} right now.",
            f"Are they {gerund} {ctx_en} right now?",
            f"Ellos están {gerund_es(spanish_inf)} {ctx_es} ahora mismo."
        ),
        # 3. Present Perfect
        (
            "PRESENT_PERFECT", "Present Perfect", "Presente Perfecto",
            f"They have {pp} {ctx_en} recently.",
            f"They have not {pp} {ctx_en} yet.",
            f"Have they {pp} {ctx_en} recently?",
            f"Ellos han {pp_es(spanish_inf)} {ctx_es} recientemente."
        ),
        # 4. Present Perfect Continuous
        (
            "PRESENT_PERFECT_CONT", "Present Perfect Continuous", "Presente Perfecto Continuo",
            f"They have been {gerund} {ctx_en} all morning.",
            f"They have not been {gerund} {ctx_en} all morning.",
            f"Have they been {gerund} {ctx_en} all morning?",
            f"Ellos han estado {gerund_es(spanish_inf)} {ctx_es} toda la mañana."
        ),
        # 5. Past Simple
        (
            "PAST_SIMPLE", "Past Simple", "Pasado Simple",
            f"Yesterday, they {past} {ctx_en}.",
            f"Yesterday, they did not {base} {ctx_en}.",
            f"Did they {base} {ctx_en} yesterday?",
            f"Ayer ellos {past_es(spanish_inf)} {ctx_es}."
        ),
        # 6. Past Continuous
        (
            "PAST_CONTINUOUS", "Past Continuous", "Pasado Continuo",
            f"They were {gerund} {ctx_en} when we arrived.",
            f"They were not {gerund} {ctx_en} when we arrived.",
            f"Were they {gerund} {ctx_en} when we arrived?",
            f"Ellos estaban {gerund_es(spanish_inf)} {ctx_es} cuando llegamos."
        ),
        # 7. Past Perfect
        (
            "PAST_PERFECT", "Past Perfect", "Pasado Perfecto",
            f"They had {pp} {ctx_en} before noon.",
            f"They had not {pp} {ctx_en} before noon.",
            f"Had they {pp} {ctx_en} before noon?",
            f"Ellos habían {pp_es(spanish_inf)} {ctx_es} antes del mediodía."
        ),
        # 8. Past Perfect Continuous
        (
            "PAST_PERFECT_CONT", "Past Perfect Continuous", "Pasado Perfecto Continuo",
            f"They had been {gerund} {ctx_en} for hours.",
            f"They had not been {gerund} {ctx_en} for hours.",
            f"Had they been {gerund} {ctx_en} for hours?",
            f"Ellos habían estado {gerund_es(spanish_inf)} {ctx_es} durante horas."
        ),
        # 9. Future Simple (Will)
        (
            "FUTURE_WILL", "Future Simple (will)", "Futuro Simple (will)",
            f"They will {base} {ctx_en} tomorrow.",
            f"They will not {base} {ctx_en} tomorrow.",
            f"Will they {base} {ctx_en} tomorrow?",
            f"Ellos {future_es(spanish_inf)} {ctx_es} mañana."
        ),
        # 10. Future (Going to)
        (
            "FUTURE_GOING_TO", "Future (going to)", "Futuro Inmediato (going to)",
            f"They are going to {base} {ctx_en} soon.",
            f"They are not going to {base} {ctx_en} soon.",
            f"Are they going to {base} {ctx_en} soon?",
            f"Ellos van a {spanish_inf} {ctx_es} pronto."
        ),
        # 11. Future Continuous
        (
            "FUTURE_CONTINUOUS", "Future Continuous", "Futuro Continuo",
            f"They will be {gerund} {ctx_en} at this time tomorrow.",
            f"They will not be {gerund} {ctx_en} at this time tomorrow.",
            f"Will they be {gerund} {ctx_en} at this time tomorrow?",
            f"Ellos estarán {gerund_es(spanish_inf)} {ctx_es} a esta hora mañana."
        ),
        # 12. Future Perfect
        (
            "FUTURE_PERFECT", "Future Perfect", "Futuro Perfecto",
            f"By next week, they will have {pp} {ctx_en}.",
            f"By next week, they will not have {pp} {ctx_en}.",
            f"Will they have {pp} {ctx_en} by next week?",
            f"Para la próxima semana, ellos habrán {pp_es(spanish_inf)} {ctx_es}."
        ),
        # 13. Conditional Simple
        (
            "CONDITIONAL_SIMPLE", "Conditional Simple", "Condicional Simple",
            f"They would {base} {ctx_en} if they had the chance.",
            f"They would not {base} {ctx_en} without permission.",
            f"Would they {base} {ctx_en} in this situation?",
            f"Ellos {cond_es(spanish_inf)} {ctx_es} si tuvieran la oportunidad."
        ),
        # 14. Passive Voice
        (
            "PASSIVE_VOICE", "Passive Voice", "Voz Pasiva",
            f"It was {pp} by the team {ctx_en}.",
            f"It was not {pp} by the team {ctx_en}.",
            f"Was it {pp} by the team {ctx_en}?",
            f"Fue {pp_es(spanish_inf)} por el equipo {ctx_es}."
        )
    ]
    return tenses


def gerund_es(inf):
    primary = inf.split(",")[0].strip()
    if primary.endswith("ar"):
        return primary[:-2] + "ando"
    elif primary.endswith("er") or primary.endswith("ir"):
        return primary[:-2] + "iendo"
    return primary + "ndo"


def pp_es(inf):
    primary = inf.split(",")[0].strip()
    # excepciones comunes
    irreg = {
        "hacer": "hecho", "decir": "dicho", "escribir": "escrito",
        "romper": "roto", "ver": "visto", "abrir": "abierto",
        "morir": "muerto", "poner": "puesto", "volver": "vuelto"
    }
    if primary in irreg:
        return irreg[primary]
    if primary.endswith("ar"):
        return primary[:-2] + "ado"
    elif primary.endswith("er") or primary.endswith("ir"):
        return primary[:-2] + "ido"
    return primary + "do"


def past_es(inf):
    primary = inf.split(",")[0].strip()
    irreg = {
        "ser": "fueron", "estar": "estuvieron", "ir": "fueron",
        "tener": "tuvieron", "hacer": "hicieron", "decir": "dijeron",
        "ver": "vieron", "dar": "dieron", "saber": "supieron",
        "poder": "pudieron", "poner": "pusieron", "querer": "quisieron"
    }
    if primary in irreg:
        return irreg[primary]
    if primary.endswith("ar"):
        return primary[:-2] + "aron"
    elif primary.endswith("er") or primary.endswith("ir"):
        return primary[:-2] + "ieron"
    return "lograron " + primary


def future_es(inf):
    primary = inf.split(",")[0].strip()
    irreg = {
        "hacer": "harán", "decir": "dirán", "tener": "tendrán",
        "poder": "podrán", "poner": "pondrán", "salir": "saldrán"
    }
    if primary in irreg:
        return irreg[primary]
    return primary + "án"


def cond_es(inf):
    primary = inf.split(",")[0].strip()
    irreg = {
        "hacer": "harían", "decir": "dirían", "tener": "tendrían",
        "poder": "podrían", "poner": "pondrían", "salir": "saldrían"
    }
    if primary in irreg:
        return irreg[primary]
    return primary + "ían"


def build_database():
    print(f"[*] Iniciando construcción limpia de Verbolandia SQLite...")
    print(f"[*] Destino: {OUTPUT_DB}")

    # Respaldar base anterior si existe
    if os.path.exists(OUTPUT_DB):
        shutil.copyfile(OUTPUT_DB, BACKUP_DB)
        print(f"[+] Respaldo creado en: {BACKUP_DB}")
        os.remove(OUTPUT_DB)

    conn = sqlite3.connect(OUTPUT_DB)
    cursor = conn.cursor()

    # Optimizaciones de SQLite
    cursor.execute("PRAGMA user_version = 3;")
    cursor.execute("PRAGMA encoding = 'UTF-8';")
    cursor.execute("PRAGMA foreign_keys = ON;")

    # 1. Crear tabla verbs
    cursor.execute("""
    CREATE TABLE verbs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        base TEXT UNIQUE NOT NULL,
        third_person TEXT NOT NULL,
        past TEXT NOT NULL,
        past_participle TEXT NOT NULL,
        gerund TEXT NOT NULL,
        ipa_base TEXT NOT NULL,
        ipa_past TEXT NOT NULL,
        ipa_participle TEXT NOT NULL,
        spanish_meaning TEXT NOT NULL,
        irregular INTEGER NOT NULL DEFAULT 0,
        common INTEGER NOT NULL DEFAULT 1,
        favorite INTEGER NOT NULL DEFAULT 0,
        spanish TEXT,
        phonetic TEXT,
        cefr_level TEXT NOT NULL DEFAULT 'A1',
        morph_group TEXT NOT NULL DEFAULT 'regular_ed',
        phonetic_group_id INTEGER NOT NULL DEFAULT 1,
        common_error_tip TEXT NOT NULL
    );
    """)

    # 2. Crear tabla verb_tenses
    cursor.execute("""
    CREATE TABLE verb_tenses (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        verb_id INTEGER NOT NULL,
        tense_code TEXT NOT NULL,
        tense_name_en TEXT NOT NULL,
        tense_name_es TEXT NOT NULL,
        affirmative_en TEXT NOT NULL,
        negative_en TEXT NOT NULL,
        interrogative_en TEXT NOT NULL,
        example_es TEXT NOT NULL,
        FOREIGN KEY (verb_id) REFERENCES verbs(id) ON DELETE CASCADE
    );
    """)

    # 3. Crear tabla phrasal_verbs
    cursor.execute("""
    CREATE TABLE phrasal_verbs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        verb_id INTEGER NOT NULL,
        phrasal_en TEXT NOT NULL,
        meaning_es TEXT NOT NULL,
        example_en TEXT NOT NULL,
        example_es TEXT NOT NULL,
        FOREIGN KEY (verb_id) REFERENCES verbs(id) ON DELETE CASCADE
    );
    """)

    # Crear índices de alto rendimiento
    cursor.execute("CREATE INDEX idx_verbs_base ON verbs(base);")
    cursor.execute("CREATE INDEX idx_verbs_spanish ON verbs(spanish_meaning);")
    cursor.execute("CREATE INDEX idx_verbs_level ON verbs(cefr_level);")
    cursor.execute("CREATE INDEX idx_verbs_irregular ON verbs(irregular);")
    cursor.execute("CREATE INDEX idx_verbs_common ON verbs(common);")
    cursor.execute("CREATE INDEX idx_tenses_verb_id ON verb_tenses(verb_id);")
    cursor.execute("CREATE INDEX idx_tenses_code ON verb_tenses(tense_code);")
    cursor.execute("CREATE INDEX idx_phrasal_verb_id ON phrasal_verbs(verb_id);")

    # Poblar verbs y verb_tenses
    verb_id_map = {}
    inserted_verbs = 0
    inserted_tenses = 0

    for item in VERBS_CATALOG:
        base, third, past, pp, gerund, ipa_b, ipa_p, ipa_pp, spanish, irreg, level, morph, error_tip, ctx_en, ctx_es = item

        cursor.execute("""
        INSERT INTO verbs (
            base, third_person, past, past_participle, gerund,
            ipa_base, ipa_past, ipa_participle, spanish_meaning,
            irregular, common, favorite, spanish, phonetic,
            cefr_level, morph_group, phonetic_group_id, common_error_tip
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """, (
            base, third, past, pp, gerund,
            ipa_b, ipa_p, ipa_pp, spanish,
            irreg, 1, 0, spanish, ipa_b,
            level, morph, 1, error_tip
        ))

        v_id = cursor.lastrowid
        verb_id_map[base] = v_id
        inserted_verbs += 1

        # Generar los 14 tiempos gramaticales para este verbo
        tenses = generate_tenses_for_verb(base, third, past, pp, gerund, spanish, ctx_en, ctx_es)
        for t in tenses:
            t_code, t_name_en, t_name_es, aff, neg, interrog, ex_es = t
            cursor.execute("""
            INSERT INTO verb_tenses (
                verb_id, tense_code, tense_name_en, tense_name_es,
                affirmative_en, negative_en, interrogative_en, example_es
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
            """, (v_id, t_code, t_name_en, t_name_es, aff, neg, interrog, ex_es))
            inserted_tenses += 1

    # Poblar phrasal_verbs
    inserted_phrasals = 0
    for p in PHRASAL_VERBS_CATALOG:
        verb_base, phrasal_en, meaning_es, ex_en, ex_es = p
        if verb_base in verb_id_map:
            v_id = verb_id_map[verb_base]
            cursor.execute("""
            INSERT INTO phrasal_verbs (
                verb_id, phrasal_en, meaning_es, example_en, example_es
            ) VALUES (?, ?, ?, ?, ?);
            """, (v_id, phrasal_en, meaning_es, ex_en, ex_es))
            inserted_phrasals += 1

    conn.commit()
    conn.close()

    file_size_kb = os.path.getsize(OUTPUT_DB) / 1024
    print(f"\n[✔] Base de datos creada exitosamente:")
    print(f"    - Verbos insertados: {inserted_verbs}")
    print(f"    - Tiempos gramaticales generados: {inserted_tenses}")
    print(f"    - Phrasal verbs registrados: {inserted_phrasals}")
    print(f"    - Tamaño final: {file_size_kb:.1f} KB")


if __name__ == "__main__":
    build_database()
