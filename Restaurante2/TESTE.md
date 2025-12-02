# Guia Rápido de Teste

## Pré-requisitos

1. **Java 11 ou superior** instalado
2. **Maven** instalado

Verifique:
```bash
java -version
mvn -version
```

## Passo 1: Baixar Dependências

Primeiro, baixe todas as dependências do Apache Ratis:

```bash
mvn clean install
```

Isso pode levar alguns minutos na primeira vez, pois vai baixar todas as dependências.

## Passo 2: Compilar o Projeto

```bash
mvn compile
```

## Passo 3: Iniciar o DNS Server

Em um terminal:

```bash
mvn exec:java -Dexec.mainClass="main.network.DnsServer"
```

Ou:

```bash
java -cp "target/classes:$(mvn dependency:build-classpath -DincludeScope=compile -q -Dmdep.outputFile=/dev/stdout)" main.network.DnsServer
```

## Passo 4: Iniciar as Filiais (3 terminais)

Abra 3 terminais separados. Em cada um:

**Terminal 2 - Filial 1:**
```bash
mvn exec:java -Dexec.mainClass="main.network.RatisClusterMain" -Dexec.args="node1 5001 localhost:5002 localhost:5003"
```

**Terminal 3 - Filial 2:**
```bash
mvn exec:java -Dexec.mainClass="main.network.RatisClusterMain" -Dexec.args="node2 5002 localhost:5001 localhost:5003"
```

**Terminal 4 - Filial 3:**
```bash
mvn exec:java -Dexec.mainClass="main.network.RatisClusterMain" -Dexec.args="node3 5003 localhost:5001 localhost:5002"
```

## O que Esperar

1. **DNS Server**: Deve mostrar "DNS Server rodando na porta 9090"
2. **Filial 1, 2, 3**: Cada uma vai iniciar e mostrar logs do Ratis
3. **Eleição**: Um dos nós vai ser eleito líder e você verá:
   - `[NODE] Este nó (nodeX) é o LÍDER!`
   - `[NODE] Web Service publicado em: http://...`
   - `[NODE] DNS atualizado: LÍDER está em ...`

## Testar o Middleware

Depois que um líder for eleito, você pode testar usando o MercadoMiddleware em outro programa ou criar um teste simples.

## Troubleshooting

### Erro: "Cannot resolve symbol" no IDE
- Isso é normal! Execute `mvn compile` primeiro para baixar as dependências
- Se usar IntelliJ, atualize o projeto Maven (Reload All Maven Projects)

### Erro: "Address already in use"
- Alguém já está usando a porta
- Pare os processos anteriores ou mude as portas

### Nós não se conectam
- Verifique se todos os nós estão rodando
- Certifique-se de que o DNS está rodando na porta 9090
- Verifique os logs de cada nó para ver erros

