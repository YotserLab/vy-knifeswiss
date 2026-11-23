# Regras Gerais de Segurança para o Projeto VYKnifeSwiss

Ao escrever ou modificar código neste projeto, os agentes devem seguir rigorosamente estas diretrizes para evitar a introdução de código frágil ou vulnerável e reduzir a necessidade de auditorias constantes:

- **Proibido Código Mockado em Produção:** Nunca deixe dados hardcoded (chaves de API, senhas em texto plano, tokens) ou lógicas simuladas (mocks temporários) em código destinado a produção.
- **Validação Rigorosa de Entrada:** Sempre valide e sanitize as entradas de usuários, arquivos ou dados recebidos pela rede antes de processá-los.
- **Tratamento Seguro de Exceções:** Implemente tratamento de erros adequado que não vaze informações sensíveis da infraestrutura, chaves ou caminhos internos através de stack traces e mensagens de erro não tratadas.
- **Uso Seguro de Criptografia:** Utilize bibliotecas de criptografia padrão e fortes providas pelo SO/plataforma. Não implemente algoritmos de criptografia caseiros e nunca armazene chaves criptográficas diretamente no código-fonte.
- **Princípio do Menor Privilégio (Android):** Solicite apenas as permissões estritamente necessárias no `AndroidManifest.xml` e nunca exporte componentes (Activities, Services, Receivers) sem as devidas proteções.
