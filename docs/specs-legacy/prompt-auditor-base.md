### 📋 O "Prompt Ouro" para Auditorias de Código (Base Genérica)

> Este é o núcleo reaproveitável para qualquer stack/plataforma. Para auditar um projeto de uma plataforma específica (Android, iOS, Web, etc.), combine este arquivo com o respectivo `prompt-auditor-<plataforma>.md`, que adiciona vetores e arquivos-alvo próprios daquele ecossistema.

> **Atue como engenheiro sênior de segurança de software e arquiteto de sistemas, realizando uma revisão defensiva autorizada deste repositório.** O repositório pertence a mim ou estou expressamente autorizado a auditá-lo. A análise deve permanecer restrita ao código local e ter finalidade exclusivamente defensiva.
>
> Restrinja a análise ao código e ao ambiente local. Não ataque serviços externos, não utilize credenciais encontradas, não exfiltre dados e não execute testes destrutivos. Priorize análise estática e testes locais seguros.
>
> O objetivo principal é descobrir vulnerabilidades críticas, recursos implementados pela metade (stubs/mocks) e más práticas de arquitetura.
>
> **Vetores genéricos (aplicáveis a qualquer plataforma):**
> 1. **Segurança de Rede & P2P:** Injeção, exposição indevida de Sockets/Servidores, tráfego em texto plano (HTTP/TCP sem TLS), falhas de Path Traversal, autenticação/autorização fraca ou ausente em serviços expostos.
> 2. **Funcionalidades Quebradas/Mocks:** Encontre funções, telas ou lógicas de negócio que estão simuladas (`random()`, dados *hardcoded*) ou inacabadas. Antes de classificar algo como mock ou implementação incompleta, verifique chamadas, comentários, interfaces, testes, variantes de compilação e comportamento esperado.
>
> *(Vetores adicionais específicos de plataforma — ex: armazenamento seguro e privacidade em Android — devem vir do arquivo de extensão correspondente.)*
>
> **Metodologia Exigida:**
> - Não se limite à superfície. Faça varreduras profundas em arquivos de configuração, classes utilitárias de criptografia e gerenciadores de servidores locais.
> - Se precisar, use ferramentas de busca (grep) para rastrear onde e como variáveis sensíveis transitam no código.
> - Não classifique padrões isolados como vulnerabilidades sem demonstrar o fluxo explorável, o ativo afetado e o impacto concreto. Diferencie "vulnerabilidade confirmada", "risco provável" e "recomendação de endurecimento". Apresente evidência verificável, fluxo do problema, condições necessárias para exploração, impacto e correção.
>
> **Metodologia de Eficiência (obrigatória, para não desperdiçar tokens/memória na execução):**
> - **Inventário único:** faça a descoberta de arquivos/estrutura do repositório uma única vez no início e reutilize essa lista para todos os vetores. Não repita `find`/exploração de diretórios por vetor.
> - **Particionamento sem sobreposição:** se for delegar a análise por vetor a subagentes ou etapas separadas, garanta que cada arquivo seja atribuído a **no máximo um** vetor/agente. Nunca envie o mesmo arquivo para duas buscas paralelas distintas.
> - **Grep antes de read:** localize primeiro com busca por padrão (grep) e leia apenas o trecho relevante (contexto de ~20 linhas ao redor do match). Só leia o arquivo inteiro se ele tiver poucas linhas (ex.: <150) ou se o contexto isolado não for suficiente para entender o fluxo.
> - **Confiar na evidência citada:** ao consolidar resultados de subagentes ou buscas anteriores, aceite como válido um achado que já venha com trecho de código exato e caminho:linha citados. Releitura manual de verificação é obrigatória apenas para uma amostra (os itens Crítico, ou 2-3 itens aleatórios) — não para todos os achados.
> - **Auditoria incremental:** antes de iniciar, verifique se já existe um relatório anterior em `specs/auditoria/`. Se existir, rode `git diff <hash-do-commit-referenciado-no-último-relatório>..HEAD` e restrinja a nova varredura aos arquivos alterados/criados; para o restante, apenas referencie o achado antigo (mantém validade ou marca como resolvido) em vez de reanalisar do zero.
> - **Detalhamento proporcional à severidade:** achados Crítico e Alto recebem o formato completo (trecho de código, fluxo, impacto, correção). Achados Médio podem ser mais concisos. Achados Baixo/Informativo (incluindo confirmações de "implementação correta") entram apenas como uma linha em uma tabela resumida, sem seção dedicada nem bloco de código.
>
> **Formato de Entrega (Output):**
> Crie um relatório executivo chamado `specs/auditoria/auditoria-profunda-[DATA].md`.
> - O relatório deve conter: Nível de Gravidade (Crítico, Alto, Médio, Baixo, Informativo), Onde o problema ocorre (informe caminho do arquivo, símbolo afetado e intervalo de linhas, usando o formato `arquivo:linhaInicial-linhaFinal`) e a Solução Técnica Proposta (com trechos de código).
> - Ao final do documento, gere um *Plano de Ação (Checklist)* em fases de execução (Fase 1: Hotfixes de Segurança, Fase 2: etc) para que possamos implementar em seguida.

***

### 💡 Por que este prompt funciona melhor?

1. **Atribuição de Papel e Autorização:** Define a persona de especialista. No entanto, não concede capacidade especial nem autorização real à IA; serve apenas para orientar a profundidade, o vocabulário e a perspectiva técnica. A autorização efetiva vem da declaração de propriedade e escopo.
2. **Definição Clara de Vetores:** Em vez de pedir "busque falhas", você diz *exatamente* quais falhas são comuns (Path Traversal, TLS ausente, permissões abusivas).
3. **Escopo de Inspeção Autorizado:** Ao instruir o uso de ferramentas de busca dentro do limite local seguro, você concede permissão explícita para investigações profundas. O termo é mais preciso e menos propenso a acionar filtros automáticos da IA.
4. **Evidências, Não Suspeitas:** Exigir evidências, fluxos exploráveis e forçar a IA a verificar chamadas e contextos antes de acusar recursos de "quebrados" ou "vulneráveis" evita que você perca tempo com falsos positivos.
5. **Formato Direcionado à Ação:** O formato `arquivo:linhaInicial-linhaFinal` é mais confiável do que links diretos e evita quebras. O Checklist final prepara o terreno para você simplesmente dizer *"pode executar"*, acelerando a correção real.
6. **Metodologia de Eficiência:** particionar arquivos sem sobreposição, priorizar grep sobre leitura integral, confiar em evidência já citada e auditar de forma incremental evitam que o custo de execução (tokens/tempo) cresça de forma redundante a cada nova rodada ou a cada vetor analisado em paralelo.
