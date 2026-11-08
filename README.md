# FPLBP

Android aplikacija za mini ligu FPLBP (FPL liga 68740). Prati ko duguje 350 RSD posle svakog kola, bez servera: sve se čuva lokalno na telefonu, a podaci dolaze sa javnog FPL API-ja.

## Pravila duga

- Posle svakog kola, tim sa najmanje **neto** bodova (bodovi minus hitovi za transfere) duguje 350 RSD.
- Ako je više timova izjednačeno na dnu, dele dug (350/2, 350/3…). Iznosi se sabiraju kao tačni razlomci i zaokružuju na 2 decimale tek za prikaz, pa nema "curenja" kroz sezonu.
- Dug se upisuje tek kada FPL zvanično završi i potvrdi kolo (`finished` + `data_checked`). Do tada je sve označeno kao **Privremeno**.
- Čipovi se računaju normalno.

## Šta aplikacija radi

| Ekran | Sadržaj |
|---|---|
| **Dug** | Kasa za večeru, **Uživo · Kolo X** (tapni: ko je završio, ko igra, ko tek igra i kada), tabela dugovanja, dugovanje kroz sezonu |
| **Kola** | Sva kola sa neto bodovima i hitovima; detalj kola: **Samo kod njega** (igrači koje niko drugi nije imao) i postave, svaki igrač sa ikonama (golovi, asistencije, čista mreža, bonus, odbrane, kartoni), minutama ili vremenom utakmice; tapni igrača za tačne bodove |
| **Igrači** | Odakle dolaze bodovi (krug), minusi po vrsti, top 10 sa punom raspodelom, kapiteni, najvredniji igrači lige i diferencijali |
| **Čipovi** | Ko je koji čip iskoristio i šta je još ostalo |
| **Statistika** | Puta poslednji, hitovi, bodovi na klupi, najtesnija spasavanja, konzistentnost |
| **Podešavanja** | ID lige, iznos duga, obaveštenja, ručno osvežavanje, izvoz u JSON |

**Uživo predviđanje:** tokom kola aplikacija sama primenjuje automatske izmene sa klupe, prelazak trake na vice-kapitena i bonus po BPS-u, čim su sigurni, umesto da čeka FPL na kraju kola.

**Kad je FPL nedostupan** ("The game is being updated"): aplikacija prikazuje sačuvane podatke sa porukom i pokušava ponovo u pozadini. Polovični odgovori nikad ne pregaze dobre podatke.

**Deljenje:** dugme u gornjem uglu pravi sliku tabele dugovanja ili tekućeg kola za Viber/WhatsApp.

**Osvežavanje:** dok je aplikacija otvorena i igra se utakmica, osvežava se sama na 2 minuta. Teški FPL fajl sa svim igračima preuzima se jednom dnevno ili kad počne novo kolo.

**Obaveštenja** (najviše 3 dnevno, ništa od 23 do 8 h; obračun kola uvek stiže): posle roka (kapiteni, čipovi, hitovi), jedno tokom kola samo ako je borba za dno napeta, i obračun kola sa do dve zanimljivosti: hit koji je odlučio, nizovi ("Petar plaća treće kolo zaredom", "Prvi dug sezone", "Posle 7 kola bez duga…"), promašeni čipovi (Bench Boost, Wildcard, Free Hit, Triple Captain), kapiten "buntovnik", rekordno loše kolo, kasa i udeo u večeri. Tokom kola: posle svakog dana utakmica, "Ostala je još samo LIV–MCI…" kad je dno tesno pred poslednju utakmicu, i preokret na dnu (najviše jednom dnevno). Plus pregled na polusezoni i na kraju sezone.

## Prvo pokretanje

Aplikacija učita sva odigrana kola (oko 60 zahteva za 4 tima, nekoliko sekundi). Završena kola se posle toga više nikad ne preuzimaju; osvežavaju se samo tekuće i nepotvrđeno kolo.

## Objavljivanje preko GitHub-a

Svaki push na `main` pokreće GitHub Actions: testovi, potpisan APK i nova verzija na **Releases** stranici (`v1.0.<broj builda>`). Aplikacija proverava Releases i prikazuje "Nova verzija dostupna" (radi jer je repozitorijum javan).

### 1. Ključ za potpisivanje (jednom)

APK mora uvek biti potpisan **istim ključem**, inače drugari ne mogu da instaliraju novu verziju preko stare. Napravi ključ na svom računaru (treba Java):

```bash
keytool -genkeypair -v -keystore fplbp.jks -alias fplbp \
  -keyalg RSA -keysize 2048 -validity 10000
```

Zapamti lozinke i **sačuvaj `fplbp.jks` na sigurno** (ne u repozitorijum, `.gitignore` ga već isključuje). Ako se ključ izgubi, svi moraju da obrišu aplikaciju i instaliraju je ponovo.

### 2. GitHub secrets

U repozitorijumu: **Settings → Secrets and variables → Actions → New repository secret**:

| Ime | Vrednost |
|---|---|
| `KEYSTORE_BASE64` | izlaz komande `base64 -w0 fplbp.jks` (macOS: `base64 -i fplbp.jks`) |
| `KEYSTORE_PASSWORD` | lozinka keystore-a |
| `KEY_ALIAS` | `fplbp` |
| `KEY_PASSWORD` | lozinka ključa |

Ili preko GitHub CLI:

```bash
gh secret set KEYSTORE_BASE64 < <(base64 -w0 fplbp.jks)
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body fplbp
gh secret set KEY_PASSWORD
```

### 3. Push

```bash
git init && git add . && git commit -m "FPLBP"
git branch -M main
git remote add origin https://github.com/<tvoj-nalog>/fplbp.git
git push -u origin main
```

Posle par minuta APK je na stranici **Releases**. Pošalji link drugarima.

### 4. Instalacija na telefonu

Otvori `.apk`, dozvoli "Instaliraj nepoznate aplikacije" za pregledač ili Viber kad telefon pita, i instaliraj. Nove verzije se instaliraju preko stare, podaci ostaju.

Na Xiaomi/Huawei/Samsung telefonima isključi optimizaciju baterije za FPLBP (dugme u Podešavanjima), inače obaveštenja mogu da kasne.

## Tehnički

Kotlin, Jetpack Compose (Material 3), Room, WorkManager, OkHttp, kotlinx.serialization. Grafikoni su nacrtani ručno (Canvas), bez dodatnih biblioteka. Logika duga, izmena, bonusa i obaveštenja je u `domain/` (čist Kotlin) i pokrivena testovima u `app/src/test`.

Lokalni build: Android Studio (otvori folder), ili `gradle assembleDebug` sa Gradle 8.11 i JDK 17.
