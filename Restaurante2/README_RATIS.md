# Sistema de Mercado Distribuído com Apache Ratis

Este projeto implementa um sistema de mercado distribuído usando Apache Ratis para garantir consenso Raft entre as filiais.

## Estrutura

- **DnsServer**: Servidor DNS que mantém registro do líder atual
- **Node**: Representa uma filial do mercado, com integração Ratis
- **MercadoStateMachine**: StateMachine do Ratis que replica o estado (pedidos, restaurantes)
- **MercadoMiddleware**: Cliente que se conecta ao líder via DNS

## Configuração e Compilação

### 1. Instalar dependências Maven

```bash
mvn clean install
```

Isso vai baixar todas as dependências do Apache Ratis automaticamente.

### 2. Compilar o projeto

```bash
mvn compile
```

## Como Executar

### Passo 1: Iniciar o DNS Server

```bash
java -cp target/classes main.network.DnsServer
```

O DNS ficará escutando na porta 9090.

### Passo 2: Iniciar as Filiais (Nodes)

Para iniciar um cluster com 3 filiais, abra 3 terminais:

**Terminal 1 - Filial 1:**
```bash
java -cp target/classes main.network.RatisClusterMain node1 5001 localhost:5002 localhost:5003
```

**Terminal 2 - Filial 2:**
```bash
java -cp target/classes main.network.RatisClusterMain node2 5002 localhost:5001 localhost:5003
```

**Terminal 3 - Filial 3:**
```bash
java -cp target/classes main.network.RatisClusterMain node3 5003 localhost:5001 localhost:5002
```

### Formato do comando:
```
RatisClusterMain <nodeId> <portaRatis> <peer1:porta1> [peer2:porta2] ...
```

### Passo 3: Aguardar Eleição do Líder

O Ratis vai automaticamente eleger um líder entre os 3 nós. Quando um nó se tornar líder:

1. Ele vai iniciar o WebService na porta 8081
2. Ele vai se registrar no DNS como "LIDER"
3. Os outros nós ficarão como followers

### Passo 4: Testar o Sistema

O `MercadoMiddleware` vai consultar o DNS para descobrir o líder e se conectar automaticamente.

## Como Funciona

### Eleição de Líder

1. Quando os nós iniciam, o Ratis realiza uma eleição
2. Um dos nós é eleito como líder
3. O líder se registra no DNS automaticamente
4. Se o líder cair, o Ratis elege um novo líder automaticamente
5. O novo líder se registra no DNS

### Replicação de Dados

- **Cadastro de Restaurante**: Replicado via Ratis para todos os nós
- **Compra de Produtos**: Replicada via Ratis para todos os nós
- **Consulta de Tempo**: Operação de leitura (não precisa replicar)

### Comandos Ratis

Os comandos são enviados como strings:

- `CADASTRO:restaurante` - Cadastra um restaurante
- `COMPRA:idRestaurante:produto1,produto2,...` - Processa uma compra

## Arquivos Criados

Os dados do Ratis (logs e snapshots) são salvos em:
```
./ratis-data/node1/
./ratis-data/node2/
./ratis-data/node3/
```

## Troubleshooting

### Nó não está encontrando os peers

- Certifique-se de que todos os nós estão rodando
- Verifique se as portas estão corretas
- Verifique se o firewall permite comunicação entre as portas

### DNS não encontra o líder

- Verifique se algum nó se tornou líder (procure por "LÍDER!" nos logs)
- Verifique se o DNS Server está rodando
- Os nós devem conseguir se conectar ao DNS na porta 9090

### Erros de compilação sobre classes do Ratis

- Execute `mvn clean install` para baixar as dependências
- Verifique se a versão do Java é 11 ou superior

## Notas Importantes

- O cluster precisa de pelo menos 3 nós para tolerar falhas (quorum)
- Com 2 nós, o sistema funciona mas é menos resiliente
- O líder sempre se registra no DNS automaticamente quando assume a liderança

