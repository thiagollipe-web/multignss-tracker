---
name: offline-maps-osmdroid
description: Regras de mapa offline com osmdroid (OpenStreetMap, sem chave de API), cache, .mbtiles/.zip, pré-download e trilhas GPX. Use ao mexer em OfflineMapManager ou na aba de mapa.
---
# Mapas offline com osmdroid

- Biblioteca: `org.osmdroid:osmdroid-android`. Proibido Google Maps.
- Antes de criar qualquer `MapView`: `Configuration.getInstance().load(ctx, prefs)`, `userAgentValue = packageName`, `osmdroidBasePath = getExternalFilesDir(null)/osmdroid`, `osmdroidTileCache = <base>/tiles`.
- Arquivos `.mbtiles`, `.zip` ou `.sqlite` em `<base>` são lidos sozinhos pelo `MapTileFileArchiveProvider` padrão: não reimplementar.
- Fonte de tiles: Mapnik (OSM, limpo, padrão) para ver; OpenTopo ("Estilo Topo") para baixar área. CARTO exige chave (testado: "API KEY REQUIRED"). Rota amarela com contorno escuro. `AndroidView` do mapa com `clipToBounds()` (senão o MapView desenha por cima da busca). O Mapnik (tile.openstreetmap.org) tem `FLAG_NO_BULK` e o `CacheManager` lança `TileSourcePolicyException`; a política do OSM proíbe pré-download em massa.
- Pré-download: `CacheManager.downloadAreaAsync` só da área visível, do zoom atual até +2, com teto de tiles (`MAX_TILES`). Respeitar servidores voluntários.
- Modo somente offline: `mapView.setUseDataConnection(false)`.
- Mapa: marcador da posição, círculo de precisão (`Polygon.pointsAsCircle`, raio = accuracy em metros), polyline do rastro.
- Trilha: cada ponto é gravado em arquivo (append) na hora, para sobreviver ao app ser fechado. Export/import `.gpx` 1.1 via Storage Access Framework (sem permissão de armazenamento). Escapar XML.
