# Issue #51680 — Support Reloading of the System Truststore

## Issue Context

- **Issue**: https://github.com/keycloak/keycloak/issues/51680
- **Parent**: https://github.com/keycloak/keycloak/issues/26524 (Support automatic reload of rotated certificates and private keys)
- **Prerequisite**: PR #51567 (Quarkus TLS registry integration for HTTPS server — already merged)

The parent issue (#26524) asks for hot-reload of ALL certificates (server, client, CA) without restart. Issue #51680 is specifically about the **system truststore** — the merged truststore used for all outbound TLS connections (LDAP, SMTP, IdP, JWKS, etc.).

---

## What the Issue Actually Means

System properties (`javax.net.ssl.trustStore`) point to a file, but changing the file on disk doesn't help because:
1. The `KeyStore` is loaded into memory once at boot
2. All consumers cache their `SSLContext`/`SSLSocketFactory` objects permanently
3. There is no hook to tell consumers "trust changed, rebuild your SSL state"

The issue asks: **leverage the Quarkus TLS registry's reload mechanism** (already integrated for the HTTPS server) to also manage the system truststore, and then propagate changes to all outbound TLS consumers.

---

## Current Architecture (One-Shot, No Reload)

```
Boot time (STATIC_INIT):
  KeycloakRecorder.configureTruststore()
    -> TruststoreBuilder.setSystemTruststore()
      -> Merges: JRE cacerts + truststore-paths + K8s CAs + conf/truststores/
      -> Saves merged file to <data-dir>/keycloak-truststore.pkcs12
      -> Sets javax.net.ssl.trustStore* system properties

SPI init:
  FileTruststoreProviderFactory.init()
    -> Reads javax.net.ssl.trustStore system property
    -> Loads the merged KeyStore into memory
    -> Classifies certs into root/intermediate maps (Collections.unmodifiableMap!)
    -> Creates immutable FileTruststoreProvider (all fields are final)
    -> Stores in TruststoreProviderSingleton (static field)

Runtime:
  All consumers read from the singleton. Never refreshed.
```

### Key Files — Truststore Infrastructure

| File | Purpose |
|---|---|
| `services/src/main/java/org/keycloak/truststore/TruststoreBuilder.java` | Merges multiple cert sources into one PKCS12 file, sets system properties |
| `services/src/main/java/org/keycloak/truststore/FileTruststoreProviderFactory.java` | Loads merged keystore once at boot, creates immutable provider |
| `services/src/main/java/org/keycloak/truststore/FileTruststoreProvider.java` | Immutable value object — all fields `final`, maps wrapped in `unmodifiableMap` |
| `services/src/main/java/org/keycloak/truststore/TruststoreProviderSingleton.java` | Static holder so LDAP's SSLSocketFactory can access provider without a session |
| `services/src/main/java/org/keycloak/truststore/SSLSocketFactory.java` | Custom SSLSocketFactory for LDAP JNDI — static singleton, `final sslsf` field |
| `services/src/main/java/org/keycloak/truststore/JSSETruststoreConfigurator.java` | Creates SSLContext/TrustManagers from provider — volatile cached, never refreshed |
| `server-spi-private/src/main/java/org/keycloak/truststore/TruststoreProvider.java` | SPI interface |
| `quarkus/runtime/src/main/java/org/keycloak/quarkus/runtime/KeycloakRecorder.java` | `configureTruststore()` — aggregates paths, calls TruststoreBuilder at STATIC_INIT |
| `quarkus/config-api/src/main/java/org/keycloak/config/TruststoreOptions.java` | Config options: `truststore-paths`, `truststore-kubernetes-enabled`, `tls-hostname-verifier` |

---

## Existing TLS Registry Integration (PR #51567)

Two named TLS buckets already exist for inbound HTTPS:

1. **`keycloak-https-server`** — main HTTPS server
   - Defined in `HttpPropertyMappers.java` (line 39-40)
   - Maps `--https-*` options to `quarkus.tls."keycloak-https-server".*`
   - Wired via `quarkus.http.tls-configuration-name`

2. **`keycloak-management-server`** — management interface
   - Defined in `ManagementPropertyMappers.java` (line 45-46)
   - Maps `--https-management-*` options
   - Inherits from main HTTP options when not explicitly set

### How HTTPS Certificate Reload Already Works

1. `quarkus.tls."keycloak-https-server".reload-period` is set (default `1h`)
2. `TlsCertificateUpdater` calls `VertxCertificateHolder.reload()` periodically
3. If files changed on disk, `reload()` returns `true`
4. `CertificateUpdatedEvent(name, tlsConfiguration)` CDI event fires
5. `HttpCertificateUpdateEventListener` observes the event, calls `server.updateSSLOptions()` on matching Vert.x HTTP servers
6. SSL context hot-swapped without server restart

### Key Files — TLS Registry (Quarkus)

| File | Purpose |
|---|---|
| `/opt/workspace/quarkus/extensions/tls-registry/spi/.../TlsConfiguration.java` | Core interface: `reload()`, `getTrustStore()`, `getKeyStore()`, `createSSLContext()` |
| `/opt/workspace/quarkus/extensions/tls-registry/spi/.../TlsConfigurationRegistry.java` | Registry: `get(name)`, `getDefault()`, `register(name, config)` |
| `/opt/workspace/quarkus/extensions/tls-registry/spi/.../CertificateUpdatedEvent.java` | CDI event record: `name` + `tlsConfiguration` |
| `/opt/workspace/quarkus/extensions/tls-registry/spi/.../TrustStoreProvider.java` | Quarkus SPI for programmatic truststore provision (CDI bean with `@Identifier`) |
| `/opt/workspace/quarkus/extensions/tls-registry/runtime/.../VertxCertificateHolder.java` | Concrete TlsConfiguration impl — `reload()` re-reads from disk, atomic swap under `synchronized` |
| `/opt/workspace/quarkus/extensions/tls-registry/runtime/.../TlsCertificateUpdater.java` | Periodic reload scheduler — `vertx.setPeriodic()` + fires CDI event on change |
| `/opt/workspace/quarkus/extensions/tls-registry/runtime/.../CertificateRecorder.java` | Registry impl — manages `ConcurrentHashMap<String, TlsConfiguration>` |
| `/opt/workspace/quarkus/extensions/vertx-http/runtime/.../HttpCertificateUpdateEventListener.java` | Observes CertificateUpdatedEvent, calls `server.updateSSLOptions()` |
| `quarkus/runtime/src/main/java/org/keycloak/quarkus/runtime/configuration/mappers/HttpPropertyMappers.java` | Keycloak option -> TLS registry property mapping |
| `quarkus/runtime/src/main/java/org/keycloak/quarkus/runtime/configuration/mappers/ManagementPropertyMappers.java` | Management interface TLS mapping |
| `quarkus/runtime/src/main/java/org/keycloak/quarkus/runtime/services/KeycloakHttpServerOptionsCustomizer.java` | Enables SNI on HTTP server |

### Quarkus TLS Registry Key Capabilities

- Named TLS configurations via `quarkus.tls.<name>.*`
- Truststore-only configurations (no keystore) are supported
- Supports PEM (multiple cert files), P12, JKS, "other" (BCFKS), and programmatic `TrustStoreProvider` CDI beans
- `TrustStoreProvider` CDI bean annotated with `@Identifier("<name>")` provides custom truststore for a named bucket
- Periodic reload via `reload-period` config
- Manual reload via `TlsConfiguration.reload()` (does NOT fire CDI event — caller must fire manually)
- `CertificateUpdatedEvent` is fired only by the periodic updater (and Let's Encrypt endpoint)
- Reserved name `"javax.net.ssl"` lazily wraps JVM default truststore

---

## Complete Inventory of Truststore Consumers

### Consumers That Use TruststoreProvider (SPI)

| Consumer | File | How it gets trust | Caching | Can reload? |
|---|---|---|---|---|
| **DefaultHttpClientFactory** | `services/.../connections/httpclient/DefaultHttpClientFactory.java` (line 223) | `TruststoreProvider.getTruststore()` -> `SSLContext` | JVM singleton, never recreated | **NO** |
| **LDAP (LDAPS)** | `federation/ldap/.../LDAPContextManager.java` (line 244) | Static `SSLSocketFactory.getDefault()` via JNDI class name | JVM static singleton | **NO** — hardest |
| **LDAP (StartTLS)** | `federation/ldap/.../LDAPContextManager.java` (line 103) | `TruststoreProvider.getSSLSocketFactory()` per-connection | Per-connection, but same immutable provider | **NO** |
| **SMTP/Email** | `services/.../email/DefaultEmailSenderProvider.java` (line 318) | New `JSSETruststoreConfigurator` per send | Per-send, but provider is immutable | Partial |
| **X.509 cert validation** | `services/.../x509/CertificateValidator.java` (line 612) | `TruststoreProvider.getHttpsRootCertificates()` per-auth | Per-request, reads immutable maps | Would pick up if provider swapped |
| **WebAuthn** | `services/.../WebAuthnRegisterFactory.java` (line 41) | `TruststoreProvider.getTruststore()` per-registration | Per-request | Would pick up if provider swapped |
| **Nginx proxy SSL** | `services/.../x509/NginxProxySslClientCertificateLookupFactory.java` (line 79) | Double-checked locking, loaded once | JVM singleton | **NO** — `isTruststoreLoaded` flag |
| **CRL validation** | `services/.../utils/CRLUtils.java` (line 92) | `TruststoreProvider` cert maps per-check | Per-check | Would pick up if provider swapped |

### Indirect Consumers (via shared HttpClient singleton)

All of these go through `DefaultHttpClientFactory`'s singleton `CloseableHttpClient`:
- OIDC/OAuth2/SAML identity providers (broker)
- Social providers (Facebook, GitHub, Google, etc.)
- JWKS fetching, backchannel logout, CIBA
- SAML artifact resolution, metadata loading
- SSF push delivery, SCIM client
- reCAPTCHA validation
- Admin callbacks (ResourceAdminManager)
- OCSP/CRL fetching via HTTP

### Independent TLS Subsystems (NOT using TruststoreProvider)

| Consumer | File | How it gets trust | Can reload? |
|---|---|---|---|
| **Remote Infinispan** | `model/infinispan/.../DefaultCacheRemoteConfigProviderFactory.java` (line 234) | `SSLContext.init(null, null, null)` -> JVM default | **NO** |
| **JGroups TLS** | `model/infinispan/.../JGroupsConfigurator.java` (line 213) | Separate `JGroupsCertificateProvider` SPI | **YES** — has `rotateCertificate()` |
| **Database (PostgreSQL)** | `quarkus/runtime/.../DatabasePropertyMappers.java` (line 195) | `DefaultJavaSSLFactory` -> JVM default truststore | **NO** |
| **Database (Oracle/MSSQL)** | Same file | JDBC-specific properties | **NO** |
| **Kubernetes Operator** | `operator/.../KeycloakClientBaseController.java` (line 353) | Custom SSLContext per reconciliation | **YES** |

---

## Design Challenges

### Challenge 1: Merging Logic vs TLS Registry Model

`TruststoreBuilder` does complex merging the TLS registry doesn't natively support:
- Recursive directory scanning
- Mixed PEM + PKCS12 files
- JRE cacerts inclusion
- Kubernetes CA auto-discovery

**Options:**
- **A:** Implement a Quarkus `io.quarkus.tls.TrustStoreProvider` CDI bean (with `@Identifier("keycloak-system-truststore")`) that does the merging programmatically
- **B:** Keep `TruststoreBuilder` merge-to-file, re-run on reload, have TLS registry re-read the merged file
- **C:** Map individual paths directly to PEM config (limited — doesn't handle P12/directories)

### Challenge 2: Consumer Refresh — The Hard Parts

Even after the TLS registry detects a change, we need to propagate to each consumer.

**Most consumers**: Swapping `TruststoreProviderSingleton` + making `FileTruststoreProvider` mutable would suffice for per-request consumers (X.509, WebAuthn, CRL, SMTP).

**Two hard cases:**

1. **DefaultHttpClientFactory** — `CloseableHttpClient` singleton bakes in `SSLContext`. Options:
   - Recreate the HttpClient (drain existing connections, null the volatile reference, next request triggers lazy re-init)
   - Use a delegating `SSLContext` wrapping a mutable reference
   - Longer-term: switch to Vert.x-based HTTP client with `updateSSLOptions()`

2. **LDAP `SSLSocketFactory` static singleton** — JNDI requires a class name, class has `static instance` + `final sslsf`. Options:
   - Change `sslsf` from `final` to `volatile`, add `reload()` that rebuilds from `TruststoreProviderSingleton.get()`
   - Reset the `instance` static field on reload with `synchronized` coordination

### Challenge 3: Timing — STATIC_INIT vs RUNTIME_INIT

`TruststoreBuilder` runs at Quarkus STATIC_INIT, TLS registry initializes at RUNTIME_INIT. System truststore must be available early (before datasource creation). Likely requires:
- Keep initial build at STATIC_INIT
- Add TLS registry-based reload at RUNTIME_INIT

---

## Proposed Implementation Approach

### Phase 1: Make the Truststore Infrastructure Mutable

1. Make `FileTruststoreProvider` fields non-final (or create `ReloadableTruststoreProvider`)
2. Add a `reload(KeyStore, Map, Map)` swap method to `FileTruststoreProviderFactory`
3. Change `SSLSocketFactory.sslsf` from `final` to `volatile`, add `reload()` method
4. `TruststoreProviderSingleton.set()` is already accessible

### Phase 2: Register a Truststore-Only TLS Bucket

1. New named TLS configuration: `keycloak-system-truststore`
2. Implement Quarkus `TrustStoreProvider` CDI bean with `@Identifier("keycloak-system-truststore")` that runs the merging logic from `TruststoreBuilder`
3. Map `--truststore-paths` and new `--truststore-reload-period` option to the TLS registry
4. Reload period triggers periodic `VertxCertificateHolder.reload()` -> `TrustStoreProvider.getTrustStore()` re-invoked

### Phase 3: CDI Observer to Propagate Changes

Create a CDI bean that `@Observes CertificateUpdatedEvent`:
1. Filter for the `keycloak-system-truststore` bucket name
2. On event:
   - Get updated `TlsConfiguration.getTrustStore()` from the registry
   - Rebuild `FileTruststoreProvider` with updated certs (re-classify root/intermediate)
   - Swap `TruststoreProviderSingleton`
   - Reset `SSLSocketFactory.instance`
   - Trigger `DefaultHttpClientFactory` HttpClient recreation
   - Clear `NginxProxySslClientCertificateLookupFactory.isTruststoreLoaded`

### Phase 4: Consumer-Specific Refresh

- **HttpClient**: Add `resetHttpClient()` to `DefaultHttpClientFactory` — closes old client, nulls reference, next request triggers lazy re-init with new trust
- **LDAP SSLSocketFactory**: Make `sslsf` volatile, add `reload()` that rebuilds from current `TruststoreProviderSingleton.get()`
- **Remote Infinispan**: Needs investigation — may need its own SSLContext refresh hook
- **Per-request consumers** (X.509, WebAuthn, CRL, SMTP): Automatically pick up changes once provider singleton is swapped

### Out of Scope

- **HTTPS server truststore for mTLS** (`--https-trust-store-file`) — already managed by `keycloak-https-server` TLS bucket
- **Database TLS** — JDBC pools manage their own SSL; separate concern
- **JGroups TLS** — already has its own rotation via `JGroupsCertificateProvider`
