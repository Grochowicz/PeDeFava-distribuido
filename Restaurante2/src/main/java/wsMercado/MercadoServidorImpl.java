package wsMercado;

import network.Node;
import javax.jws.WebService;

@WebService(endpointInterface = "wsMercado.MercadoServidor")
public class MercadoServidorImpl implements MercadoServidor {

    private Node node; // Referência ao Nó

    // Construtor obrigatório do JAX-WS
    public MercadoServidorImpl() {}

    // Construtor que usamos para injetar o Nó
    public MercadoServidorImpl(Node node) {
        this.node = node;
    }

    @Override
    public int cadastrarPedido(String restaurante) {
        // Repassa para o nó processar
        return node.processarCadastro(restaurante);
    }

    @Override
    public boolean comprarProdutos(int restaurante, String[] produtos) {
        return node.processarCompra(restaurante, produtos);
    }

    @Override
    public int tempoEntrega(int restaurante) {
        return node.calcularTempoEntrega(restaurante);
    }
}