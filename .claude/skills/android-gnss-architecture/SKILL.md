---
name: android-gnss-architecture
description: Regras para ler GLONASS, Galileo e BeiDou (GPS só como referência) com LocationManager + GnssStatus.Callback no MultiGNSS Tracker. Use ao mexer em GnssRepository, permissões ou métricas de satélite.
---
# Arquitetura GNSS

- Só `LocationManager` + `GnssStatus.Callback` (API 24+, targetSdk 35). Nada de Play Services/FusedLocation.
- Constelações: `CONSTELLATION_GLONASS` = Rússia, `CONSTELLATION_GALILEO` = UE, `CONSTELLATION_BEIDOU` = China, `CONSTELLATION_GPS` = referência. Outras (QZSS, SBAS, IRNSS) são descartadas.
- Métricas por satélite: `getSvid`, `getCn0DbHz`, `getElevationDegrees`, `getAzimuthDegrees`, `getCarrierFrequencyHz` (só se `hasCarrierFrequencyHz`, API 26+), `usedInFix`.
- Posição: `onLocationChanged` do `GPS_PROVIDER` → lat, lon, altitude, velocidade, rumo e precisão, cada uma só se `hasX()`.
- Permissões: pedir `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` em runtime; nunca registrar callback sem checar `checkSelfPermission`; capturar `SecurityException`.
- Registrar no ON_START e remover no ON_STOP (sem vazamento de callback).
- Frequência → banda: 1575.42 MHz L1/E1/B1C, ~1602 MHz G1, 1561.098 MHz B1I, 1176.45 MHz L5/E5a/B2a.
