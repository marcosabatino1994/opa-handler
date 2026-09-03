# POC — RBAC con Open Policy Agent (OPA)

Proof of concept di un sistema di autorizzazione basato su ruoli (RBAC) in cui una
console di amministrazione gestisce utenti, ruoli, permessi e deleghe su database, e **OPA**
prende le decisioni di autorizzazione leggendo quei dati.

L'architettura è impostata secondo il pattern **produzione**: policy e dati vivono in
**due bundle separati**, serviti da sorgenti diverse, che OPA scarica autonomamente
(pull) e combina in memoria.

---

## Indice

- [Concetti chiave](#concetti-chiave)
- [Architettura](#architettura)
- [Componenti](#componenti)
- [Il ruolo di opa-config.yaml](#il-ruolo-di-opa-configyaml)
- [Come funziona il giro (scenario)](#come-funziona-il-giro-scenario)
- [Decisioni di progetto e relativi perché](#decisioni-di-progetto-e-relativi-perché)
- [Modello dati](#modello-dati)
- [Endpoint del backend](#endpoint-del-backend)
- [Come avviare tutto](#come-avviare-tutto)
- [Riavvii successivi (non è il primo avvio)](#riavvii-successivi-non-è-il-primo-avvio)
- [Verifiche](#verifiche)
- [Note e limiti (POC)](#note-e-limiti-poc)
- [Prossimi passi](#prossimi-passi)

---

## Concetti chiave

Il principio fondante di OPA è la separazione tra:

- **Policy** — la *logica* delle decisioni, scritta in Rego (es. "consenti se il ruolo
  dell'utente possiede il permesso richiesto"). È stabile, cambia raramente.
- **Dati** — i *fatti* su cui la logica ragiona (es. "mario ha il ruolo admin",
  "admin può leggere i report"). Cambiano di continuo.

OPA **non conosce il database**: non ha driver, non si collega a Oracle. Vive con in
memoria solo le policy e un documento `data`. Il compito del sistema è portare i dati
dentro OPA. Qui lo facciamo con i **bundle**: pacchetti `.tar.gz` che OPA scarica via
HTTP a intervalli regolari (polling).

---

## Architettura

```
        Sorgente POLICY (esterna, versionata)
        ┌──────────────────────────────┐
        │  GitHub Releases              │
        │  rbac.rego  (bundle POLICY)   │
        └───────────────┬──────────────┘
                        │ polling 30–60s
                        ▼
  ┌───────────────────────────────────────────────────────────┐
  │  rete di container (rbac-net)                               │
  │                                                             │
  │   Dashboard admin (Angular, CRUD)                           │
  │             │ REST                                          │
  │             ▼                                               │
  │        Quarkus  ── serve il bundle DATI ──┐                 │
  │             │ JDBC                         │ polling 10–20s  │
  │             ▼                              ▼                 │
  │         Oracle                           OPA  ── decisioni   │
  │       (fonte di verità)                   ▲                  │
  │                                           │ query allow      │
  │                              App di test (Angular)           │
  └───────────────────────────────────────────────────────────┘
```

Il flusso in una riga: **Dashboard → Oracle → (bundle dati) Quarkus + (bundle policy)
GitHub → OPA li combina → decisione**.

---

## Componenti

| Componente | Ruolo | Sorgente / dettaglio |
|---|---|---|
| **Oracle** | Fonte di verità: utenti, ruoli, permessi, associazioni | container `oracle-poc`, immagine `gvenzl/oracle-free`, service `FREEPDB1` |
| **Quarkus** | Backend REST: CRUD della dashboard + generazione del bundle DATI dal DB | container `rbac-opa`, porta 8080 |
| **OPA** | Motore di decisione: scarica i due bundle, li combina, valuta le query | container `opa-bundles`, porta 8181 |
| **GitHub Releases** | Sorgente del bundle POLICY (rbac.rego versionata) | repo `opa-policy`, release `v1`, asset `bundle.tar.gz` |
| **Dashboard admin** | UI Angular sul CRUD di utenti/ruoli/permessi/deleghe e tratte | progetto `opa-handler-fe` (vedi il suo README per l'avvio) |
| **App di test** | UI Angular che interroga OPA: per ogni tratta mostra/nasconde le azioni | progetto `opa-handler-fe` |

Tutti i container stanno sulla stessa rete `rbac-net` e si raggiungono per nome.

---

## Il ruolo di opa-config.yaml

`opa-config.yaml` **non è né policy né dati**: è la configurazione **di OPA stesso**.
È il file che dice a OPA *cosa fare all'avvio* — da quali URL scaricare i bundle, con
quale cadenza di polling, e come si chiamano i "services" (le sorgenti).

- **Quando entra in gioco:** una sola volta, **all'avvio del container OPA**. Lanciando
  OPA con `--config-file=/config/opa-config.yaml`, OPA legge il file, capisce che deve
  gestire due bundle (`data` e `policy`) e avvia i "bundle loader" che iniziano il
  polling. È il *bootstrap*: senza, OPA parte come un motore vuoto che non sa dove
  prendere nulla.
- **Dove vive:** è un file **locale** sul disco, **montato** nel container
  (`-v ${PWD}:/config:ro`). Distinzione chiave: OPA scarica *i bundle* da remoto
  (Quarkus, GitHub), ma la *propria configurazione* la legge dal filesystem locale —
  non la scarica da nessuna parte.
- **Conseguenza pratica:** la config viene letta **solo all'avvio**. Ogni modifica
  richiede di **ricreare il container OPA** perché la rilegga (è ciò che si fa ogni
  volta che si cambiano i puntamenti, es. da nginx a GitHub).

In una riga: *è la mappa che dice a OPA dove trovare policy e dati; la legge all'avvio,
dal disco, e definisce l'intero comportamento di download.*

> Nota: NON serve versionare `opa-config.yaml` su GitHub perché OPA la usi — OPA non la
> legge da lì. Tenerla in un repo è solo buona pratica di versionamento, non un
> ingranaggio del meccanismo. (Esiste un meccanismo avanzato, il *discovery bundle*, con
> cui OPA può scaricare anche parte della propria config da remoto, ma qui non è usato.)

---

## Come funziona il giro (scenario)

Scenario: *l'amministratore concede a Luigi il permesso di eliminare le tratte, e
nell'app Luigi vede comparire il pulsante "Elimina".*

1. **L'admin modifica i permessi.** Dalla dashboard assegna a Luigi il ruolo `editor`
   (che include `delete`/`tratta`). La dashboard chiama l'API di Quarkus, che scrive su
   **Oracle** (nuova riga nella tabella ponte `USER_ROLES`). Qui finisce l'intervento
   umano: tutto il resto è automatico.

2. **Oracle è aggiornato, OPA ancora no.** Per qualche secondo il DB e OPA divergono. È
   il prezzo (accettabile) del disaccoppiamento: si chiude da solo.

3. **OPA fa il polling (entro 10–20s).** OPA chiama `GET /bundles/rbac.tar.gz` di
   Quarkus. Quarkus **legge Oracle in tempo reale**, costruisce il JSON dei dati, lo
   impacchetta e lo restituisce. OPA vede l'ETag cambiato e **attiva il nuovo bundle**.
   Ora anche OPA sa che Luigi è editor. (In parallelo ricontrolla la policy da GitHub:
   invariata → `304 Not Modified`.)

4. **Luigi apre l'app.** L'app chiede a OPA "luigi può fare `delete` su `tratta`?". OPA
   combina la **regola** (bundle policy da GitHub) con i **fatti** (bundle dati da
   Oracle) e risponde `true`. L'app mostra il pulsante "Elimina".

Punto chiave: **l'admin ha toccato solo il database.** Nessuno ha aggiornato OPA a mano
né ridistribuito codice. Il cambiamento è fluito da solo.

**Variante con delega.** Lo stesso pulsante può comparire per un'altra strada: se Luigi
non ha il ruolo ma un utente che possiede `delete`/`tratta` glielo **delega** (via
`POST /delegations`), la delega entra nel bundle dati e la policy la riconosce. Risultato
identico — pulsante visibile — ma concesso per delega anziché per ruolo.

---

## Decisioni di progetto e relativi perché

**Perché OPA e non `if` di permessi nel codice Java?**
Separa la logica di autorizzazione dall'applicazione. Le regole cambiano per motivi e
con ritmi diversi rispetto al codice; con OPA si modificano senza toccare/ridistribuire
l'app, e la stessa OPA può servire più servizi con policy coerenti.

**Perché separare policy e dati?**
È il principio fondante di OPA. La policy è logica stabile e riutilizzabile; i dati sono
fatti che cambiano di continuo. Separati, la policy resta testabile e i dati aggiornabili
senza toccare la logica.

**Perché i bundle e non il push via API?**
Il push (spingere i dati in OPA con `PUT /v1/data/...`) è semplice ma fragile: i dati
stanno solo in memoria e si perdono al riavvio, e accoppia il backend a OPA. Con i bundle
è OPA a "tirare": sopravvive ai riavvii (riscarica) e il backend resta disaccoppiato
(espone solo un URL). È lo standard di produzione.

**Perché DUE bundle separati?**
Policy e dati hanno sorgenti, ritmi e owner diversi. La policy è codice: vive su GitHub,
cambia raramente, la modifica un dev con versionamento e review (polling lento, 30–60s).
I dati vivono su Oracle, cambiano a ogni modifica in dashboard, li gestisce l'admin
(polling veloce, 10–20s). Un unico bundle costringerebbe a ri-pacchettizzare la policy a
ogni modifica di un utente. Separati, ognuno segue la sua strada.

**Perché la policy su GitHub?**
La policy *è codice*, e il codice vive in un repository versionato: history, tag,
rollback, review via PR. (In sviluppo si può usare un semplice server statico come nginx,
ma è un ripiego.)

**Perché i dati generati al volo da Oracle e non un file statico?**
Oracle è la fonte di verità e cambia di continuo; il bundle dati deve esserne lo specchio
aggiornato. Quarkus lo costruisce on-demand leggendo le tabelle in tempo reale.

**Perché containerizzare Quarkus?**
Spinta pratica (il firewall dell'host bloccava le connessioni OPA→Quarkus quando Quarkus
girava fuori container), ma coincide con la produzione: tutto in container sulla stessa
rete, i servizi si parlano per nome.

**Perché i `roots` nei manifest dei bundle?**
Per far convivere due bundle senza conflitti. Ogni bundle dichiara quale ramo di `data`
possiede — `authz` per la policy, `user_roles`/`role_permissions`/`delegations` per i
dati. Nessuna sovrapposizione → OPA li accetta entrambi.

**Perché i DTO nel CRUD e non le entità dirette?**
Per evitare il `LazyInitializationException` di Hibernate (le collezioni lazy esplodono se
serializzate fuori transazione) e per disaccoppiare il modello DB dal contratto REST.

**Perché `RESOURCE_NAME` invece di `resource` sul DB?**
`RESOURCE` è una parola riservata di Oracle: la colonna va rinominata con `@Column`. Idem
per i nomi tabella (`APP_USERS`, `APP_ROLES`) al posto di `USER`/`ROLE`.

---

## Modello dati

Schema RBAC su Oracle (chiavi primarie `id` autogenerate da Panache):

```
APP_USERS (id, username)
APP_ROLES (id, name)
PERMISSIONS (id, action, resource_name)

USER_ROLES (user_id → APP_USERS, role_id → APP_ROLES)          -- tabella ponte
ROLE_PERMISSIONS (role_id → APP_ROLES, permission_id → PERMISSIONS) -- tabella ponte

DELEGATIONS (id, from_user, to_user, action, resource_name)    -- deleghe puntuali (riferite per username, non per FK)
ROUTES (id, origin, destination, modes, status)                -- tratte: dominio applicativo dell'app di test
```

- `APP_USERS` ↔ `APP_ROLES`: molti-a-molti tramite `USER_ROLES`.
- `APP_ROLES` ↔ `PERMISSIONS`: molti-a-molti tramite `ROLE_PERMISSIONS`.

Le tabelle ponte e le deleghe sono l'informazione che alimenta i dati di OPA:
`USER_ROLES` → `user_roles`, `ROLE_PERMISSIONS` (join con `PERMISSIONS`) →
`role_permissions`, `DELEGATIONS` → `delegations`. La tabella `ROUTES` **non** entra nel
bundle: è il dominio su cui l'app di test interroga OPA, non un fatto di autorizzazione.

Esempio del JSON dati prodotto per OPA:

```json
{
  "user_roles": { "mario": ["admin"] },
  "role_permissions": {
    "admin": [ { "action": "read", "resource": "tratta" } ]
  },
  "delegations": [
    { "from_user": "mario", "to_user": "luigi", "action": "delete", "resource": "tratta" }
  ]
}
```

La policy Rego (`rbac.rego`, ramo `authz`):

```rego
package authz

import rego.v1

default allow := false

# via ruolo: l'utente ha un ruolo che possiede il permesso richiesto
allow if {
    some role in data.user_roles[input.user]
    some perm in data.role_permissions[role]
    perm.action == input.action
    perm.resource == input.resource
}

# via delega: esiste una delega verso l'utente per quell'azione/risorsa
allow if {
    some d in data.delegations
    d.to_user == input.user
    d.action == input.action
    d.resource == input.resource
}
```

---

## Endpoint del backend

Quarkus, porta 8080:

| Metodo | Path | Descrizione |
|---|---|---|
| GET/POST/PUT/DELETE | `/permissions` | CRUD permessi |
| GET/POST/PUT/DELETE | `/roles` | CRUD ruoli — body POST/PUT: `{ "name": "...", "permissionIds": [..] }` |
| GET/POST/PUT/DELETE | `/users` | CRUD utenti — body POST/PUT: `{ "username": "...", "roleIds": [..] }` |
| GET/POST/DELETE | `/delegations` | Deleghe puntuali — body POST: `{ "fromUser", "toUser", "action", "resource" }`; valida che il delegante possieda davvero quel permesso |
| GET/POST/PUT/DELETE | `/routes` | CRUD tratte, più `POST /routes/{id}/approve` e `POST /routes/{id}/reject` per il ciclo `IN_REVISIONE → APPROVATA \| RIFIUTATA` |
| GET | `/authz?user=..&action=..&resource=..&status=..` | Proxy che interroga OPA (`status` opzionale) |
| GET | `/bundles/rbac.tar.gz` | Bundle DATI per OPA (generato dal DB, con ETag) |

OPA, porta 8181:

| Metodo | Path | Descrizione |
|---|---|---|
| POST | `/v1/data/authz/allow` | Query di decisione; body `{ "input": { "user", "action", "resource", "status?" } }` (la policy attuale usa solo user/action/resource) |
| GET | `/v1/status` | Stato dei bundle attivi (revision) |
| GET | `/health` | Health check |

---

## Come avviare tutto

Prerequisiti: nerdctl (o Docker), JDK 21, Maven. Comandi PowerShell.

**Ordine di avvio (e perché conta):** rete → Oracle → Quarkus → bundle policy → OPA.
L'ordine non è casuale: ogni componente dipende dai precedenti. Oracle deve essere
*pronto* prima di Quarkus (che vi si connette all'avvio); Quarkus e il bundle policy
devono essere raggiungibili prima di OPA (che al primo polling li scarica). Avviare OPA
per primo non romperebbe nulla in modo permanente (riproverebbe in loop), ma vedresti
errori finché il resto non è su.

### 1. Rete condivisa

```powershell
nerdctl network create rbac-net
```

**Perché:** crea una rete virtuale in cui i container si raggiungono **per nome**
(es. `oracle-poc`, `rbac-opa`) invece che per IP. È ciò che permette a OPA di chiamare
Quarkus come `http://rbac-opa:8080` senza passare dall'host — evitando sia
`host.docker.internal` sia il firewall che bloccava le connessioni container→host.

### 2. Oracle

```powershell
nerdctl run -d --name oracle-poc --network rbac-net -p 1521:1521 `
  -e ORACLE_PASSWORD=poc -e APP_USER=pocuser -e APP_USER_PASSWORD=pocpass `
  gvenzl/oracle-free
```

**Perché ogni pezzo:**
- `-d` → in background (detached): il DB gira dietro, il terminale resta libero.
- `--name oracle-poc` → nome fisso; sarà l'**hostname** con cui Quarkus lo raggiunge sulla rete.
- `--network rbac-net` → lo mette sulla rete condivisa.
- `-p 1521:1521` → espone la porta del listener Oracle sull'host, così puoi connetterti da `sqlplus` per ispezioni manuali (non necessario per il funzionamento tra container, ma comodo).
- `-e ORACLE_PASSWORD=poc` → password dell'utente amministrativo (`SYSTEM`).
- `-e APP_USER=pocuser` / `-e APP_USER_PASSWORD=pocpass` → l'immagine crea un utente applicativo dedicato: è buona pratica non usare l'admin dall'applicazione.

Attendere `DATABASE IS READY TO USE!` in `nerdctl logs -f oracle-poc`: lo stato "Up" del
container **non basta**, il database interno impiega tempo a inizializzarsi e Quarkus
fallirebbe se si collegasse prima.

### 3. Quarkus (build immagine + avvio)

Nel progetto Quarkus (con `quarkus.datasource.jdbc.url` puntato a `oracle-poc:1521`):

```powershell
.\mvnw clean package -DskipTests
nerdctl build -f src/main/docker/Dockerfile.jvm -t poc/rbac-opa:latest .
nerdctl run -d --name rbac-opa --network rbac-net -p 8080:8080 poc/rbac-opa:latest
```

**Perché ogni comando:**
- `.\mvnw clean package -DskipTests` → compila il progetto e produce il jar. `-DskipTests` salta l'esecuzione dei test, che altrimenti proverebbero a connettersi a Oracle durante il build.
- `nerdctl build -f src/main/docker/Dockerfile.jvm -t poc/rbac-opa:latest .` → costruisce l'immagine container. Usiamo il `Dockerfile.jvm` **generato da Quarkus** perché è già scritto per la struttura "fast-jar" (cartella `quarkus-app/` con jar + dipendenze). Il build manuale con `nerdctl build` è preferito a quello automatico dell'estensione container di Quarkus, che non si integra in modo affidabile con nerdctl.
- `nerdctl run` con:
  - `--name rbac-opa` → questo nome è l'indirizzo con cui **OPA** raggiungerà Quarkus (`http://rbac-opa:8080` nella config di OPA).
  - `--network rbac-net` → stessa rete di Oracle, così la URL JDBC `oracle-poc:1521` risolve.
  - `-p 8080:8080` → espone Quarkus sull'host per test manuali da `curl`.

**Perché containerizzare Quarkus** (e non lanciarlo in dev mode sull'host): quando girava
sull'host, il firewall bloccava le connessioni in ingresso da OPA. In container, il suo
traffico passa per il port-forwarding di nerdctl (non bloccato) ed è sulla stessa rete
degli altri — assetto identico alla produzione.

### 4. Bundle policy (su GitHub)

Nella cartella della policy (solo `rbac.rego` + `.manifest`, **senza** `.git`):

```powershell
nerdctl run --rm -v ${PWD}:/work -w /work openpolicyagent/opa build -b . -o bundle.tar.gz
```

**Perché ogni pezzo:**
- `--rm` → container usa-e-getta: serve solo a eseguire il build ed esce.
- `-v ${PWD}:/work -w /work` → monta la cartella corrente dentro il container come `/work` e ci si posiziona, così `opa build` vede i file della policy.
- `openpolicyagent/opa build -b .` → compila **e valida** la policy in un bundle (`-b` = build dalla cartella). Usare `opa build` invece di creare il tar a mano ci dà validazione della sintassi Rego prima della pubblicazione.
- `-o bundle.tar.gz` → file di output.

**Perché la cartella deve essere pulita (senza `.git`):** `opa build -b .` include
*ricorsivamente* tutto ciò che trova; una `.git` o file estranei finirebbero nel bundle.

Caricare poi `bundle.tar.gz` come asset di una release (es. `v1`) sul repo `opa-policy`.
**Perché su GitHub:** la policy è codice e va versionata (history, tag, rollback).

### 5. OPA

Con `opa-config.yaml` (vedi sotto) nella cartella corrente:

```powershell
nerdctl run -d --name opa-bundles --network rbac-net -p 8181:8181 `
  -v ${PWD}:/config:ro `
  openpolicyagent/opa run --server --addr=0.0.0.0:8181 `
  --config-file=/config/opa-config.yaml --log-level debug
```

**Perché ogni pezzo:**
- `-d --name opa-bundles` → in background, con nome fisso.
- `--network rbac-net` → sulla rete condivisa, così raggiunge Quarkus per nome.
- `-p 8181:8181` → espone l'API di OPA sull'host, per fare le query di decisione da `curl`.
- `-v ${PWD}:/config:ro` → **monta la config locale** dentro il container in sola lettura (`:ro`). OPA legge `opa-config.yaml` dal disco, non la scarica.
- `--server` → avvia OPA come server HTTP (invece che come CLI one-shot).
- `--addr=0.0.0.0:8181` → mette OPA in ascolto su **tutte** le interfacce del container, non solo sul loopback interno: senza, il port-forward `-p` non funzionerebbe.
- `--config-file=/config/opa-config.yaml` → indica la config di bootstrap (il file montato). È ciò che "accende" i bundle loader.
- `--log-level debug` → log verbosi, utili per vedere download e attivazione dei bundle.

**Perché OPA va ricreato a ogni modifica della config:** la legge **solo all'avvio**, quindi
un cambiamento richiede `nerdctl rm -f opa-bundles` seguito da un nuovo `run`.

`opa-config.yaml`:

```yaml
services:
  quarkus:
    url: http://rbac-opa:8080
  policy:
    url: https://github.com

bundles:
  data:
    service: quarkus
    resource: /bundles/rbac.tar.gz
    polling:
      min_delay_seconds: 10
      max_delay_seconds: 20
  policy:
    service: policy
    resource: /<utente>/opa-policy/releases/download/v1/bundle.tar.gz
    polling:
      min_delay_seconds: 30
      max_delay_seconds: 60
```

---

## Riavvii successivi (non è il primo avvio)

Dopo il primo setup, **non serve rifare tutto**. La rete esiste già, le immagini sono
già costruite, la policy è già su GitHub. Distinzione fondamentale tra due comandi di
nerdctl:

- `nerdctl run` → **crea** un container nuovo (da usare solo la prima volta).
- `nerdctl start` → **riavvia** un container già esistente che era stato fermato,
  mantenendone il filesystem (e quindi, per Oracle, i dati).

Quindi, se avevi solo **fermato** i container (o riavviato il PC), l'avvio è molto più
breve.

### Caso A — i container esistono ancora (li avevi solo fermati)

Verifica cosa c'è, anche fermo:

```powershell
nerdctl ps -a
```

Se vedi `oracle-poc`, `rbac-opa`, `opa-bundles` (in stato `Exited`), riavviali **nello
stesso ordine** del primo avvio:

```powershell
nerdctl start oracle-poc
# attendi che Oracle sia di nuovo pronto prima di procedere:
nerdctl logs -f oracle-poc   # cerca di nuovo "DATABASE IS READY TO USE!", poi Ctrl+C
nerdctl start rbac-opa
nerdctl start opa-bundles
```

**Perché quest'ordine anche al riavvio:** Quarkus (`rbac-opa`) all'avvio si connette a
Oracle, quindi Oracle deve essere di nuovo pronto prima; OPA (`opa-bundles`) al primo
polling scarica il bundle da Quarkus, quindi Quarkus deve essere già su.

**Nota sulla rete:** `rbac-net` sopravvive ai riavvii, non va ricreata. Se per qualche
motivo non esistesse più (`nerdctl network ls` non la mostra), ricreala con
`nerdctl network create rbac-net` **prima** di avviare i container.

**Nota su OPA:** non devi fare nulla di speciale. Alla ripartenza rilegge la config
montata e **riscarica da solo** entrambi i bundle (è il vantaggio del modello pull: lo
stato si ricostruisce). Anche se OPA avesse perso tutto dalla memoria, dopo pochi secondi
è di nuovo allineato.

### Caso B — i container erano stati rimossi (`rm`)

Se avevi fatto `nerdctl rm` (non solo `stop`), i container non esistono più e vanno
**ricreati** con i comandi `run` della sezione precedente. In questo caso:

- **Oracle**: ricrearlo con `run` significa **ripartire con il DB vuoto** (non abbiamo un
  volume persistente). I dati (utenti/ruoli/permessi) vanno reinseriti via CRUD. Per la
  POC va bene; se vuoi persistenza tra ricreazioni, aggiungi un volume (vedi sotto).
- **Quarkus**: l'immagine `poc/rbac-opa:latest` è già stata costruita, quindi **non serve
  rifare il build** — basta il comando `run`. Rifai il build (`mvnw package` +
  `nerdctl build`) **solo se hai cambiato il codice**.
- **OPA**: basta il `run`; la config e i bundle sono invariati.

### Quando rifare il build di Quarkus

Solo se hai modificato il **codice Java** o le **dipendenze**:

```powershell
.\mvnw clean package -DskipTests
nerdctl build -f src/main/docker/Dockerfile.jvm -t poc/rbac-opa:latest .
nerdctl rm -f rbac-opa
nerdctl run -d --name rbac-opa --network rbac-net -p 8080:8080 poc/rbac-opa:latest
```

Se hai cambiato solo la **policy**, invece, non tocchi Quarkus: rigeneri il bundle con
`opa build`, lo ricarichi su GitHub, e OPA lo prende da solo al polling successivo (o
ricrei OPA per forzarlo subito).

### (Opzionale) Rendere persistenti i dati di Oracle

Per non perdere i dati quando ricrei il container Oracle, monta un volume:

```powershell
nerdctl run -d --name oracle-poc --network rbac-net -p 1521:1521 `
  -e ORACLE_PASSWORD=poc -e APP_USER=pocuser -e APP_USER_PASSWORD=pocpass `
  -v oracle-data:/opt/oracle/oradata `
  gvenzl/oracle-free
```

Il volume `oracle-data` sopravvive alla rimozione del container, così i dati restano.

---

## Verifiche

Bundle caricati (nei log di OPA cercare, per `data` e `policy`):

```
Bundle loaded and activated successfully
```

Stato:

```powershell
curl.exe http://localhost:8181/v1/status
```

Query di decisione (JSON in un file `query.json` per evitare problemi di escaping):

```powershell
# query.json:  { "input": { "user": "mario", "action": "read", "resource": "tratta" } }
curl.exe http://localhost:8181/v1/data/authz/allow -H "Content-Type: application/json" --data-binary "@query.json"
```

Atteso: `{"result":true}` (e `false` per un'azione non concessa).

Prova del giro dinamico: creare/modificare un utente via CRUD, attendere il polling
(~15s), rifare la query → la decisione cambia senza aver toccato OPA.

---

## Note e limiti (POC)

- `quarkus.hibernate-orm.database.generation=update` fa creare lo schema a Hibernate:
  comodo in POC, **da non usare in produzione** (usare migration, es. Flyway).
- Oracle non ha un volume persistente: ricreare il container azzera i dati (ricreabili
  via CRUD).
- Le credenziali (`pocuser`/`pocpass`, ecc.) sono da POC: non usarle in ambienti reali.
- CORS è abilitato in modo permissivo per lo sviluppo delle app Angular.
- Latenza di propagazione: tra modifica su Oracle e aggiornamento di OPA passa il tempo
  di polling (10–20s per i dati).
- Le deleghe sono riferite per **username** (stringhe), senza foreign key verso gli
  utenti: una delega **sopravvive** alla cancellazione del `fromUser` o alla rimozione
  del suo ruolo — viene validata solo alla creazione, mai ri-verificata. In un sistema
  reale servirebbe integrità referenziale o una ri-validazione nel bundle.
- Il campo `status` delle tratte viene passato a OPA nell'input di `/authz`, ma la policy
  attuale **non lo usa** ancora: la decisione dipende solo da user/action/resource.

---

## Prossimi passi

- **Policy `status`-aware**: estendere `rbac.rego` perché usi anche lo `status` della
  tratta (es. `read` solo su `APPROVATA`, `approve`/`reject` solo su `IN_REVISIONE`).
- **Integrità delle deleghe**: legare le deleghe agli utenti (FK/cascade) o ri-validarle
  nel bundle, così che decadano quando il delegante perde il permesso.
- **CI/CD della policy**: GitHub Action che esegue `opa build` e pubblica il bundle a
  ogni push sulla policy ("policy as code" completo).
