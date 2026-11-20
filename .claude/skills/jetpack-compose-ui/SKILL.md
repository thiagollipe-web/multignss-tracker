---
name: jetpack-compose-ui
description: Regras de UI Compose + Material 3 do MultiGNSS Tracker (tema escuro de radar, StateFlow/ViewModel, lifecycle do MapView, abas). Use ao criar ou mudar telas.
---
# UI Compose

- Compose + Material 3, `darkColorScheme` estilo radar: fundo quase preto e destaque amarelo #FFC400. Sem azul (pedido do usuário).
- Estado: `ViewModel` expõe `StateFlow`; telas usam `collectAsStateWithLifecycle()`.
- Lifecycle: `LifecycleEventObserver` em `DisposableEffect` — ON_START/ON_STOP registram e removem o GNSS; ON_RESUME/ON_PAUSE chamam `mapView.onResume()/onPause()`; `onDetach()` no dispose.
- `MapView` só dentro de `AndroidView`; atualizar overlays no bloco `update`, sem recriar o mapa.
- Navegação: `NavigationBar` com 3 abas e `rememberSaveable` (sem biblioteca de navegação):
  1. "Satélites & Radar": resumo por constelação, Skyplot em `Canvas`, lista com barra CN0.
  2. "Mapa Offline": mapa, baixar área, interruptor somente offline, trilha iniciar/parar/exportar/importar, status da rede.
- Acessibilidade: `contentDescription` nos ícones e no Skyplot; texto não depende só de cor.
  3. "Navegar": busca (Nominatim) ou segurar o dedo no mapa → rota OSRM (routing.openstreetmap.de, carro/bike/a pé), faixa de manobra amarela no topo, bolha de velocidade, cartão de chegada, voz TTS pt-BR, recálculo ao sair da rota (> 50 m), tela acesa enquanto navega.
  Extras de navegação: faixas (lanes do OSRM, válidas em destaque), "depois" quando a manobra seguinte está a < 300 m, aviso de radar (Overpass, voz + chip vermelho a < 400 m), placa de limite R-19 e bolha vermelha acima do limite + 5 km/h, modo noite 18h–6h (tiles invertidos + cartão escuro).
- Nunca copiar assets/código de apps de terceiros (ex.: Waze); só imitar o comportamento público.
