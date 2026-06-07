# Nova/Luna Mermaid Flowcharts

This file mirrors the uploaded Mermaid PDF specification for the active Nova/Luna flows.

## Core Runtime

```mermaid
flowchart TD
    U["User"] --> UI["MainActivity"]
    UI --> VCS["VoiceCommandService"]
    VCS --> STT["SpeechRecognizer"]
    STT --> CB["CommandBrain"]
    CB --> BS["BrainService"]
    BS --> BR["BrainRouter"]
    BR --> VAL["BrainActionValidator"]
    VAL --> SFT["SafetyGate"]
    SFT --> EXE["ActionExecutor"]
    EXE --> TTS["TextToSpeechManager"]
    EXE --> DOM["Domain orchestrators and app launchers"]
```

## Safety Gate

```mermaid
flowchart TD
    IN["BrainAction or CommandIntent"] --> SG{"SafetyGate"}
    SG -->|safe| OK["Allow execution"]
    SG -->|confirmation required| CONF["Ask for explicit confirmation"]
    SG -->|biometric required| BIO["Ask for biometric gate"]
    SG -->|human only| MAN["Manual handoff only"]
    SG -->|blocked| BLK["Blocked result"]
    CONF --> CB["CommandBrain stores pending action"]
    OK --> EXE["ActionExecutor"]
```

## Music

```mermaid
flowchart TD
    U["Wake word: Luna / Nova"] --> P["Popup opens"]
    P --> C["User gives music command"]
    C --> I["AI understands music intent"]
    I --> D{"Command type detection"}
    D --> S1["Play specific song"]
    D --> S2["Play artist"]
    D --> S3["Play album"]
    D --> S4["Mood playlist"]
    D --> S5["Language / genre"]
    D --> S6["Music controls"]
    D --> S7["Create playlist"]
    D --> S8["Find new songs"]
    S1 --> E["Extract details"]
    S2 --> E
    S3 --> E
    S4 --> E
    S5 --> E
    S6 --> E
    S7 --> E
    S8 --> E
    E --> Q{"Any details missing?"}
    Q -->|yes| F["Ask follow-up question"]
    Q -->|no| R["Build music request"]
    F --> P1["Create / update preference profile"]
    R --> P1
    P1 --> A["Check installed music apps"]
    A --> B["Select preferred / available app"]
    B --> O["Open selected music app behind popup"]
    O --> X["Search requested music"]
    X --> Y{"Exact match?"}
    Y -->|yes| Z["Start playback"]
    Y -->|no| C1["Use close matches"]
    C1 --> W{"Explicit content?"}
    W -->|yes| W1["Warn user and ask for clean-version preference"]
    W1 --> W2["Clean-version path"]
    W -->|no| Z
    W2 --> Z
    Z --> M["Show mini music card"]
    M --> V["Voice response"]
    V --> L["Loop for next command"]
```

## Shopping

```mermaid
flowchart TD
    U["Product request"] --> A["Understand product / category"]
    A --> B{"Budget / purpose / brand / urgency / payment preference missing?"}
    B -->|yes| C["Ask follow-up questions"]
    B -->|no| D["Create requirement profile"]
    C --> D
    D --> E["Search internet and shopping apps"]
    E --> F["Collect latest products, official sites, trusted marketplaces, coupons, offers, reviews, warranty"]
    F --> G["Collect product options with images"]
    G --> H{"Website / seller trust check"}
    H --> T1["HTTPS / domain check"]
    H --> T2["Seller rating / reviews / complaints"]
    H --> T3["Return / refund policy"]
    H --> T4["Fake discount / scam signs"]
    T1 --> I["Exclude risky sellers / sites"]
    T2 --> I
    T3 --> I
    T4 --> I
    I --> J["Compare price, coupons, bank offers, exchange value, delivery speed, warranty / return, reviews / ratings, specs vs purpose"]
    J --> K["Rank top 3-5 deals"]
    K --> L["Written + voice summary with product pictures and website-wise deal table"]
    L --> M{"Which deal does the user want?"}
    M -->|no selection| N["Refine search / ask again"]
    M -->|selected| O["Open selected trusted website or app"]
    O --> P["Add to cart"]
    P --> Q["Apply best coupon"]
    Q --> R["Show final order summary"]
    R --> S{"Explicit final purchase confirmation?"}
    S -->|no| N
    S -->|yes| P1["Payment handling"]
    P1 --> U1["UPI / card / net banking: open payment page only; user pays manually"]
    P1 --> U2["Wallet: check balance, ask final confirmation, stop on OTP / PIN / password / biometric"]
    P1 --> U3["COD: check availability and ask final confirmation before placing order"]
    U1 --> V["Final response with order summary / order ID if visible"]
    U2 --> V
    U3 --> V
```

## Media

```mermaid
flowchart TD
    U["Media request"] --> A{"Detect app type"}
    A --> YT["YouTube / Shorts"]
    A --> IG["Instagram / Reels"]
    A --> OTT["OTT: Netflix / JioHotstar / Prime Video"]
    A --> UNK["Unknown app request"]
    UNK --> Q["Ask which app to use"]
    Q --> A2["Check app availability"]
    YT --> A2
    IG --> A2
    OTT --> A2
    A2 --> O["Open selected app behind popup"]
    O --> T{"Command type"}
    T --> S1["Search content"]
    T --> S2["Scroll feed / reels / shorts"]
    T --> S3["Select visible item"]
    T --> S4["Play / pause / resume"]
    T --> S5["Next / previous"]
    T --> S6["Like / save / subscribe / follow"]
    T --> S7["Open profile / channel / creator"]
    T --> S8["Watchlist / downloads"]
    T --> S9["Quality / subtitles / audio / speed"]
    T --> S10["Stop / exit"]
    S1 --> R["Search flow with result ranking and selection"]
    S2 --> SC["Scroll flow with direction / speed / summary"]
    S3 --> R2["Screen-item detection and safe open"]
    S4 --> P["Playback controls"]
    S5 --> P
    S6 --> C["Social actions with confirmations for subscribe / follow / comment posting"]
    S7 --> C2["Open profile / channel / creator"]
    S8 --> W["OTT watchlist / download confirmations and Wi-Fi / storage checks"]
    S9 --> K["Settings controls"]
    S10 --> X["Exit flow"]
    R --> G{"Age restricted / payment / subscription / rental / login / OTP / password / CAPTCHA?"}
    R2 --> G
    SC --> G
    P --> G
    C --> G
    C2 --> G
    W --> G
    K --> G
    X --> L["Loop for next command"]
    G -->|yes| M["Safety gate: manual handoff"]
    G -->|no| L
```

## Grocery

```mermaid
flowchart TD
    U["Grocery request"] --> A["Ask required item / quantity / brand / budget / delivery questions"]
    A --> B["Create grocery profile"]
    B --> C["Check accessibility, usage access, location"]
    C --> D["Detect grocery apps: Blinkit, Zepto, Instamart, JioMart, BigBasket"]
    D --> E["Search across available apps"]
    E --> F["Read product options, price, quantity, brand, delivery time, rating / availability"]
    F --> G["Match products to user needs"]
    G --> H["Compare total cart, availability, delivery fees, coupons, time, substitutions"]
    H --> I["Rank cheapest, fastest, best quality, best overall"]
    I --> J["Summary with app, items, total, delivery fee, savings, unavailable / replaced items"]
    J --> K{"Which cart should I use?"}
    K -->|refine| L["Refine search / ask again"]
    K -->|selected| M["Add selected items"]
    M --> N["Handle unavailable items / replacements"]
    N --> O["Apply coupon"]
    O --> P["Show final order summary"]
    P --> Q{"Explicit final confirmation?"}
    Q -->|no| L
    Q -->|yes| R["Payment safety"]
    R --> R1["UPI / card / net banking: manual payment page only"]
    R --> R2["Wallet: confirm balance and stop on secrets / biometric"]
    R --> R3["COD: confirm before placing order"]
    R1 --> S["Final grocery summary"]
    R2 --> S
    R3 --> S
```

## Content Creation

```mermaid
flowchart TD
    U["Content request"] --> A{"Detect output type"}
    A --> P1["PPT"]
    A --> P2["Image"]
    A --> P3["Video"]
    A --> P4["Document"]
    A --> P5["Excel"]
    A --> P6["PDF"]
    A --> P7["Other / best format"]
    P1 --> B["Collect topic / title, purpose, audience, style, length, language, deadline / quality"]
    P2 --> B
    P3 --> B
    P4 --> B
    P5 --> B
    P6 --> B
    P7 --> B
    B --> C["Create requirement profile"]
    C --> D{"Any follow-up details missing?"}
    D -->|yes| E["Ask follow-up questions"]
    D -->|no| F["Expand raw idea into full content brief: outline, structure, key points, design direction, content sections, export plan"]
    E --> F
    F --> G["Choose prompt / content expansion AI: ChatGPT Pro, ChatGPT Free, Gemini, Local AI, Luna / Nova internal prompt builder"]
    G --> H["Generate master creation prompt"]
    H --> I["Select best creation app"]
    I --> I1["PPT / doc: prefer user paid/pro app, otherwise best free app, Claude / ChatGPT / Gemini"]
    I --> I2["Image: Canva / ChatGPT / Gemini / image app"]
    I --> I3["Video: Canva / CapCut / video AI"]
    I --> I4["Excel: Google Sheets / Excel / ChatGPT"]
    I --> I5["PDF: Docs / Canva / PDF editor"]
    I1 --> J["Open app behind popup"]
    I2 --> J
    I3 --> J
    I4 --> J
    I5 --> J
    J --> K["Paste / send master prompt"]
    K --> L["Generate first draft"]
    L --> M["Process by output type"]
    M --> N["Show preview / summary"]
    N --> O{"User review needed?"}
    O -->|edits| F2["Collect feedback and refine prompt"]
    F2 --> H
    O -->|no| P["Finalize / export"]
    P --> Q["PPTX / PNG / JPG / MP4 / DOCX / Google Doc / XLSX / Google Sheet / PDF"]
    Q --> R["Save to phone / cloud if allowed"]
    R --> S["Share only if user asks"]
    S --> T["Keep editable version if possible"]
    T --> U1["Final response with file type, app used, saved location, edit / share option"]
```

## Communication

```mermaid
flowchart TD
    U["Communication command"] --> A{"Command type"}
    A --> A1["Summarize all today's messages"]
    A --> A2["Summarize one platform"]
    A --> A3["Summarize single long message"]
    A --> A4["Find / search message"]
    A --> A5["Draft / send reply / email"]
    A1 --> S["Read allowed sources only: Gmail, SMS, WhatsApp, Telegram"]
    A2 --> S
    A3 --> S
    A4 --> S
    A5 --> S
    S --> B["Collect today's messages"]
    B --> C["Classify important / urgent / normal / spam / promotional"]
    C --> D["Platform-wise summary, important first"]
    D --> E{"Search / summary / draft?"}
    E -->|single-platform| F["Single-platform summary"]
    E -->|single-long-message| G["Long-message summary with extracted sender intent"]
    E -->|search| H["Search across platforms with results by platform / sender / date"]
    E -->|draft| I["Detect language, tone, formal / informal / user style"]
    I --> J["Create draft"]
    J --> K["Show draft"]
    K --> L{"Send only after confirmation?"}
    L -->|edit| I
    L -->|save| M["Save draft option"]
    L -->|cancel| N["Cancel option"]
    L -->|send| O["Send only after confirmation"]
```

## Phone

```mermaid
flowchart TD
    U["Phone command"] --> A{"Command type"}
    A --> A1["Call saved contact"]
    A --> A2["Call unknown person"]
    A --> A3["Call number from message / WhatsApp / Telegram"]
    A --> A4["Create new contact"]
    A1 --> B["Search contacts"]
    B --> B1{"Exact / multiple / no match?"}
    B1 -->|exact| C["Proceed"]
    B1 -->|multiple| D["Ask user to confirm the correct contact"]
    B1 -->|none| E["No match found"]
    A2 --> F["Search contacts, recent logs, WhatsApp, SMS, Telegram, Truecaller if allowed"]
    F --> G["Ask confirmation before call"]
    A3 --> H["Identify sender / extract number / handle one / multiple / none"]
    H --> I["Ask confirmation before call"]
    A4 --> J["Extract name / number"]
    J --> K{"Number missing?"}
    K -->|yes| L["Ask for number"]
    K -->|no| M["Duplicate check and confirm save / update / create new"]
    C --> Z["Final result on popup"]
    D --> Z
    E --> Z
    G --> Z
    I --> Z
    M --> Z
```

## Food

```mermaid
flowchart TD
    U["Food request"] --> A["Ask food / cuisine, quantity, veg / non-veg, budget, restaurant, urgency"]
    A --> B["Create food profile"]
    B --> C["Check permissions"]
    C --> D["Detect food apps: Swiggy, Zomato, Domino's, McDonald's / KFC / Pizza Hut, other food apps"]
    D --> E["Search across available apps"]
    E --> F["Set delivery location"]
    F --> G["Read restaurants, menu items, price, rating, reviews, delivery time, offers"]
    G --> H["Filter and match: item, veg / non-veg, budget, rating, delivery time, coupons, user preference"]
    H --> I["Compare restaurant rating, price, delivery fee, coupons, time, hygiene / popularity, final amount"]
    I --> J["Rank cheapest, fastest, best rated, best overall"]
    J --> K["Summary and ask user which option"]
    K --> L{"No selection?"}
    L -->|yes| M["Refine search / ask again"]
    L -->|no| N["Open selected food app"]
    N --> O["Select restaurant / item"]
    O --> P["Customize: size, quantity, spice, add-ons, remove ingredients, special instructions"]
    P --> Q["Add to cart"]
    Q --> R["Handle missing / unavailable items"]
    R --> S["Apply coupon"]
    S --> T["Show final summary"]
    T --> U1{"Explicit final confirmation?"}
    U1 -->|yes| V["Payment safety: manual UPI / card / net banking, wallet confirmation and stop on secrets / biometric, COD confirmation"]
    U1 -->|no| M
    V --> W["Final response with order details"]
```

## Cab

```mermaid
flowchart TD
    U["Cab request"] --> A["Ask pickup, destination, cab type, ride time, preference"]
    A --> B["Create ride profile"]
    B --> C["Check location / accessibility / usage permissions"]
    C --> D["Detect cab apps: Ola, Uber, Rapido, inDrive"]
    D --> E["Search fares across apps"]
    E --> F["Set pickup / destination"]
    F --> G["Read ride options, fare, ETA, availability"]
    G --> H["Compare price, pickup ETA, travel time, cab type, cancellation / surge, app reliability"]
    H --> I["Rank top 3"]
    I --> J["Summary with app, cab type, fare, ETA, travel time"]
    J --> K["Ask which ride"]
    K --> L{"No selection?"}
    L -->|yes| M["Refine search / ask again"]
    L -->|no| N["Open selected cab app"]
    N --> O["Select ride"]
    O --> P["Show final ride summary"]
    P --> Q{"Explicit final booking confirmation?"}
    Q -->|no| M
    Q -->|yes| R["Check final action safety"]
    R --> S{"Book only if allowed?"}
    S -->|yes| T["Book and read booking confirmation if visible"]
    S -->|no| U1["Manual handoff"]
    T --> V["Final ride summary with driver / vehicle / ETA / fare if visible"]
    U1 --> V
```
