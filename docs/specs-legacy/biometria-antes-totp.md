# Plano de Implementação — Proteção Biométrica no Autenticador TOTP 2FA

Adicionar a opção de configuração para exigir biometria (digital/face/credencial do dispositivo) ao abrir a tela do Autenticador TOTP. Por padrão (`default`), a solicitação de biometria estará ativada.

## User Review Required

> [!IMPORTANT]
> - **Comportamento Padrão**: A proteção por biometria para abrir o TOTP estará **ativada por padrão** (`totp_require_biometric` = `true`). Ao abrir a ferramenta 2FA, a biometria do dispositivo será solicitada automaticamente.
> - **Dispositivos sem Biometria**: Caso o aparelho não possua leitor biométrico ou senha cadastrada, uma opção de desbloqueio alternativo/aviso será fornecida para que o usuário não fique preso.

## Proposed Changes

### Configuration Storage & UI

#### [MODIFY] [TotpAuthenticatorScreen.kt](file:///projetos/apps-mobile/android/VYKnifeSwiss/app/src/main/java/com/example/ui/screens/TotpAuthenticatorScreen.kt)
- Adicionar leitura da preferência `totp_require_biometric` das `SharedPreferences` (`"totp_settings"`), com valor padrão `true`.
- Adicionar estado de bloqueio (`isUnlocked`) na tela:
  - Se `requireBiometric == true` e `!isUnlocked`: exibir tela/overlay de desbloqueio biométrico com ícone de biometria, instrução e botão "Autenticar com Biometria".
  - Disparar `BiometricPrompt` automaticamente ao abrir a tela (`LaunchedEffect`).
  - Após sucesso no `BiometricPrompt`, alterar `isUnlocked` para `true` para exibir as contas TOTP.
- Adicionar no menu superior (`DropdownMenu` de configurações da barra superior):
  - Toggle / Opção "Exigir Biometria ao Abrir" com estado de switch/checkbox refletindo e alterando a preferência em tempo real.

#### [MODIFY] [SettingsScreen.kt](file:///projetos/apps-mobile/android/VYKnifeSwiss/app/src/main/java/com/example/ui/screens/SettingsScreen.kt)
- Adicionar configuração global na lista de preferências de ferramentas e segurança para controlar o requisito de biometria no Autenticador TOTP.

## Verification Plan

### Automated Tests
- Executar `./gradlew assembleDebug` para garantir compilação sem erros.

### Manual Verification
1. Abrir a ferramenta Autenticador TOTP 2FA.
2. Verificar que o prompt de biometria do celular é acionado automaticamente ao abrir a tela.
3. Ao autenticar com sucesso com a biometria/senha do celular, os códigos TOTP são exibidos.
4. Entrar no menu de opções do TOTP ou nas Configurações do app e desativar "Exigir Biometria ao Abrir".
5. Sair e reabrir o TOTP: confirmar que abre diretamente sem pedir biometria.
6. Reativar a opção e confirmar que volta a solicitar a biometria.
