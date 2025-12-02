#!/bin/bash

# Script para testar o sistema de mercado distribuído

echo "=========================================="
echo "  Teste do Sistema de Mercado Ratis"
echo "=========================================="
echo ""

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Verifica se Maven está instalado
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}ERRO: Maven não está instalado!${NC}"
    exit 1
fi

# Verifica se Java está instalado
if ! command -v java &> /dev/null; then
    echo -e "${RED}ERRO: Java não está instalado!${NC}"
    exit 1
fi

echo -e "${GREEN}✓${NC} Maven e Java encontrados"
echo ""

# Passo 1: Compilar projeto
echo "Passo 1: Compilando o projeto..."
mvn clean compile

if [ $? -ne 0 ]; then
    echo -e "${RED}ERRO ao compilar! Verifique os erros acima.${NC}"
    exit 1
fi

echo -e "${GREEN}✓${NC} Compilação concluída!"
echo ""

# Passo 2: Verifica se o DNS já está rodando
echo "Passo 2: Verificando se o DNS está rodando..."
if lsof -Pi :9090 -sTCP:LISTEN -t >/dev/null ; then
    echo -e "${YELLOW}⚠${NC} DNS já está rodando na porta 9090"
else
    echo -e "${YELLOW}⚠${NC} DNS não está rodando. Você precisa iniciar manualmente em outro terminal:"
    echo "   mvn exec:java -Dexec.mainClass=\"main.network.DnsServer\""
    echo ""
fi

echo ""
echo "=========================================="
echo "  Próximos passos:"
echo "=========================================="
echo ""
echo "1. Em um terminal, inicie o DNS:"
echo "   ${GREEN}mvn exec:java -Dexec.mainClass=\"main.network.DnsServer\"${NC}"
echo ""
echo "2. Em outros 3 terminais, inicie as filiais:"
echo ""
echo "   Terminal 2:"
echo "   ${GREEN}mvn exec:java -Dexec.mainClass=\"main.network.RatisClusterMain\" -Dexec.args=\"node1 5001 localhost:5002 localhost:5003\"${NC}"
echo ""
echo "   Terminal 3:"
echo "   ${GREEN}mvn exec:java -Dexec.mainClass=\"main.network.RatisClusterMain\" -Dexec.args=\"node2 5002 localhost:5001 localhost:5003\"${NC}"
echo ""
echo "   Terminal 4:"
echo "   ${GREEN}mvn exec:java -Dexec.mainClass=\"main.network.RatisClusterMain\" -Dexec.args=\"node3 5003 localhost:5001 localhost:5002\"${NC}"
echo ""
echo "3. Aguarde alguns segundos para a eleição do líder"
echo "4. Procure por 'LÍDER!' nos logs para ver qual nó foi eleito"
echo ""


