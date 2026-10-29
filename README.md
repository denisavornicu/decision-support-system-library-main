# SAD Librărie — Northern Lights Book Co. (Tema VII, SAD-BI)

Aplicație de **suport decizional**. Java (Spring Boot) + SQLite + React/Recharts.

## Structură
```
sad-librarie/
├─ data/raw/            CSV-urile Kaggle (sales, inventory, customers_expanded)
├─ database/
│  ├─ schema.sql        schema SQLite (12 tabele)
│  ├─ build_db.py       încarcă CSV + completează lipsurile (seed fix) -> librarie.db
│  └─ librarie.db       baza generată (inclusă)
├─ backend/             Spring Boot 3 / Java 17, port 8080
│  └─ src/main/java/ro/uaic/sad/  SadApplication, Filters, Stats, AnalyticsService, AnalyticsController
└─ frontend/            React + Vite + Recharts, port 5173
   └─ src/  App, Filters, tabs1 (Panou, Drill, Geo), tabs2 (Segmente, Vârfuri, Promoții, Aprovizionare, Decizie), ui, api, styles.css
```

## Rulare
1. (opțional, regenerare date) `pip install pandas numpy` apoi `cd database && python build_db.py`
2. Backend: `cd backend && mvn spring-boot:run` (necesită JDK 17+ și Maven; rulează din folderul `backend`, calea DB este `../database/librarie.db`)
3. Frontend: `cd frontend && npm install && npm run dev` -> http://localhost:5173
4. Visual Studio Code: deschide folderul `sad-librarie`, extensiile „Extension Pack for Java" și ESLint/Prettier. În Visual Studio clasic deschide `frontend` ca proiect Node.js și `backend` ca proiect Maven.

## Schema (rezumat)
`store`, `category`→`subcategory`→`book(sku)`, `fact_sales(ym,store,channel,sku,units,revenue,cost)`, `sales_daily`, `inventory_move`,
`customer`→`neighborhood(province,region,name)`, `customer_pref(customer, subcategory, spend)`, `promotion`, `event`.

## Ce a fost completat față de datasetul Kaggle 
| Lipsă în Kaggle | Soluție (build_db.py) |
|---|---|
| Categorii/subcategorii/titluri | 5 categorii, 17 subcategorii, titluri generate pe cele 100 SKU |
| Vânzări pe SKU | venitul net lunar (real) pe magazin×canal alocat pe SKU după inventar × sezon × promoție × zgomot; totaluri identice cu `sales_daily` |
| Gen, vârstă, studii | generate cu seed fix, corelate cu preferințe |
| Județ/localitate | provincie → regiune → cartier/oraș |
| Promoții, evenimente | tabelele `promotion` și `event` (valorile de impact sunt MĂSURATE din date, nu introduse) |

## Cerințe 
- Grid/chart interactive, filtre cu auto-refresh (debounce 300 ms), combo-uri din SQL: bara de filtre + toate tab-urile
- Perioadă de referință aleasă de utilizator + trenduri: `Filters.jsx`, `/api/trend`
- Drill-down dinamic: tab „Vânzări" și „Geografie" (componenta `Hierarchy`)
- Grid colorat verde/galben/roșu (satisfăcător / puțin / nesatisfăcător), praguri editabile
- Estimări: previziune (regresie + sezonalitate), aprovizionare, simulare reducere de preț
- Progress bar: tab „Decizie"; alerte + toast + time stamp; drag & drop cu message box
- Decizie finală multi-criteriu cu ponderi reglabile
