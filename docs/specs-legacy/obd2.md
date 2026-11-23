# Plano OBD2 Nível Mercado — VYKnifeSwiss

> Análise baseada nos apps líderes: **Torque Pro**, **Car Scanner ELM OBD2**, **OBD Fusion**, **EOBD Facile**

---

## 🏆 Benchmark de Mercado

| Feature | Torque Pro | Car Scanner | OBD Fusion | **VYKnifeSwiss (Atual)** | **Meta** |
|---|:---:|:---:|:---:|:---:|:---:|
| Live Data (PIDs Mode 01/Elétricos) | ✅ 30+ | ✅ 40+ | ✅ 35+ | ✅ 40+ | ✅ 40+ |
| Gauges customizáveis | ✅ | ✅ | ✅ | ✅ | ✅ |
| Leitura DTCs (Mode 03/07) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Freeze Frame (Mode 02) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Readiness Monitors (Mode 01/PID 01) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Mode 06 (ECU self-test) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Sensor de O2 (lambda) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Consumo de combustível calc. | ✅ | ✅ | ✅ | ✅ | ✅ |
| Performance: 0-100 / 1/4 milha | ✅ | ✅ | ❌ | ✅ | ✅ |
| Estimativa HP/Torque (Dyno virtual) | ✅ | ❌ | ❌ | ✅ | ✅ |
| Data logging (CSV) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Trip computer / Viagem | ✅ | ✅ | ✅ | ✅ | ✅ |
| Custom PIDs | ✅ | ✅ | ✅ | ✅ | ✅ |
| Histórico de sessões | ✅ | ✅ | ✅ | ✅ | ✅ |
| Notificações de alerta | ✅ | ✅ | ✅ | ✅ | ✅ |
| Exportação PDF/relatório | ❌ | ❌ | ✅ | ✅ | ✅ |
| WiFi OBD2 / Bluetooth SPP | ✅ | ✅ | ✅ | ✅ | ✅ |

---

## 📦 Fase 2 — Implementar agora

### Arquivo: `ObdPidRegistry.kt` (novo)
- Registro completo de 40+ PIDs padrão OBD-II
- PIDs de O2 sensor (Mode 01: 0113–014F)
- Suporte a custom PIDs definidos pelo usuário

### Arquivo: `ObdModes.kt` (novo)
- **Mode 02** — Freeze Frame: snapshot de dados no momento do DTC
- **Mode 01 PID 01** — Readiness Monitors (8 monitores: catalisador, O2, EVAP, EGR, etc.)
- **Mode 06** — ECU self-test results (non-continuous monitors)

### Arquivo: `ObdSession.kt` + `ObdSessionDao.kt` (novo)
- Entidade Room para persistência de sessões
- Histórico de viagens com duração, distância, max RPM, max velocidade
- Log CSV exportável

### Arquivo: `ObdTripComputer.kt` (novo)
- Consumo instantâneo (L/100km) calculado via MAF
- Consumo médio da viagem
- Distância percorrida na sessão
- Custo estimado em R$ por km

### Arquivo: `ObdPerformance.kt` (novo)
- Cronômetro 0-100 km/h com precisão de décimos
- Estimativa de HP (via MAF + RPM)
- Estimativa de torque
- Melhor tempo e histórico de corridas

### Arquivo: `Obd2ScannerScreen.kt` (atualizar)
- Tab "Viagem" com trip computer
- Tab "Performance" com cronômetro 0-100
- Tab "Monitores" com Readiness + Freeze Frame
- Dashboard com gauges configuráveis (salvo em prefs)
- Exportar sessão para CSV/compartilhar

---

## 📊 PIDs Adicionais a Implementar

| PID | Descrição |
|---|---|
| `0101` | Status monitores I/M readiness |
| `0102` | Freeze Frame DTC trigger |
| `0106` | Short term fuel trim banco 1 |
| `0107` | Long term fuel trim banco 1 |
| `0108` | Short term fuel trim banco 2 |
| `0109` | Long term fuel trim banco 2 |
| `010E` | Avanço de ignição |
| `0113` | O2 Sensor presente |
| `0114` | O2 Sensor B1S1 tensão |
| `0115` | O2 Sensor B1S2 tensão |
| `011C` | Tipo de OBD suportado |
| `011D` | O2 Sensor status |
| `011F` | Tempo desde partida (segundos) |
| `0121` | Distância com MIL |
| `012E` | Duty cycle purge valve EVAP |
| `0130` | Contagem warm-ups desde limpeza DTCs |
| `0131` | Distância desde limpeza DTCs |
| `0132` | Pressão vapor EVAP |
| `013C` | Temp catalisador B1S1 |
| `014E` | Tempo desde limpeza DTCs |
| `015E` | Taxa de fluxo de combustível |
