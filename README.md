<p align="center"><img src="art/icon_orbi.png" width="360" alt="Ícone do MultiGNSS Tracker com o Orbi"></p>

# MultiGNSS Tracker

App Android que lê **GLONASS (Rússia)**, **Galileo (UE)** e **BeiDou (China)** direto do chip GNSS do celular, com mapa offline e navegação passo a passo — sem Google Play Services e sem chave de API.

**[⬇️ Baixar o APK (última versão)](https://github.com/thiagollipe-web/multignss-tracker/releases/latest)** — Android 8.0+

Mascote: **Orbi**, um satélite amarelo (feliz, piscando, dormindo à noite, alerta no radar).

## Funções

- **Satélites & Radar**: skyplot, CN0, elevação, azimute, banda (L1/E1/B1C, G1, B1I, L5/E5a…), filtro por constelação, GPS como referência.
- **Mapa Offline** (osmdroid/OpenStreetMap): cache de tiles, `.mbtiles`/`.zip`/`.sqlite`, baixar área (estilo Topo), trilha gravada em tempo real, exportar/importar GPX.
- **Navegar**: busca de endereço (Nominatim) ou segurar o dedo no mapa, rota por carro/bike/a pé (OSRM do FOSSGIS), seta de manobra, faixas, prévia “depois”, voz em pt-BR, recálculo ao sair da rota, aviso de radar e limite de velocidade (Overpass/OSM), modo noite.

## Compilar

```bash
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
Requer JDK 17–21 (o Kotlin 2.1 não aceita o JDK 25) e Android SDK 35.

## Mapas offline prontos

Coloque `.mbtiles` ou `.zip` em `Android/data/com.thigas.multignss/files/osmdroid/`. Dentro do `.zip`, a pasta de tiles deve se chamar `OpenTopoMap`.

## Limites conhecidos

- O Android não deixa o app escolher qual constelação calcula a posição; o “satélite guia” só mostra quanto ela está ajudando.
- Calcular rota e buscar radares precisa de internet; seguir a rota funciona offline.
- Radares, limites e faixas só existem onde o OpenStreetMap tem esses dados. Os servidores públicos do Overpass às vezes caem.
- O GNSS para quando o app sai da tela (a tela fica acesa durante a navegação).
- Respeite as políticas de uso dos servidores gratuitos (OSM tiles, Nominatim 1 req/s, OSRM FOSSGIS, OpenTopoMap).

## Créditos

Dados © colaboradores do OpenStreetMap (ODbL). Estilo Topo © OpenTopoMap (CC-BY-SA). Código sob licença MIT.
