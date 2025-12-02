import java.net.URL;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

// Importante: Importe a INTERFACE
import wsMercado.MercadoServidor;

import static java.lang.Math.max;

public class Kitchen implements Cozinha {

    Map<Integer, Integer> who = new HashMap<>();
    Map<Integer, ArrayList<String>> pedidos = new HashMap<>();
    Map<Integer, Long> fim = new HashMap<>();
    static Map<String, String> ingredienteNecessario = new HashMap<>();
    static Map<String, Integer> estoque = new HashMap<>();

    private MercadoServidor mercadoProxy;
    private int idRestaurante;

    int id = 0;
    Random rng = new Random();

    public Kitchen() {
        ingredienteNecessario.put("Café", "Pó de Café");
        ingredienteNecessario.put("Pão de Queijo", "Polvilho");
        ingredienteNecessario.put("Suco Natural", "Frutas");
        ingredienteNecessario.put("Sanduíche", "Pão");
        ingredienteNecessario.put("Salada", "Alface");
        ingredienteNecessario.put("Pizza (fatia)", "Massa");
        ingredienteNecessario.put("Hambúrguer", "Carne");
        ingredienteNecessario.put("Refrigerante", "Refrigerante");

        for (String ingrediente : ingredienteNecessario.values()) {
            estoque.put(ingrediente, 0);
        }

        try {
            System.out.println("Conectando ao Gateway do Mercado...");
            URL urlGateway = new URL("http://localhost:9999/mercadoGateway?wsdl");
            QName qname = new QName("http://wsMercado/", "MercadoGatewayImplService");
            Service service = Service.create(urlGateway, qname);
            this.mercadoProxy = service.getPort(MercadoServidor.class);
            System.out.println(">>> CONECTADO AO GATEWAY!");

            System.out.println("Cadastrando Cozinha no sistema...");
            // Envia o nome da cozinha e recebe um ID válido do Raft
            this.idRestaurante = mercadoProxy.cadastrarPedido("Cozinha_Java_Client");
            System.out.println(">>> Cadastro Aprovado! ID do Restaurante: " + this.idRestaurante);

        } catch (Exception e) {
            System.err.println("ERRO CRÍTICO: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private long Agora(){ return System.currentTimeMillis(); }

    private void verificarEstoque(String itemStr) {
        String nomePrato = itemStr.contains(",") ? itemStr.split(",")[1] : itemStr;
        String ingrediente = ingredienteNecessario.get(nomePrato);
        if (ingrediente != null && estoque.getOrDefault(ingrediente, 0) <= 0) {
            System.out.println("--- FALTA: " + ingrediente + " ---");

            boolean conseguiuComprar = false;
            while (!conseguiuComprar) {
                try {
                    System.out.println("Tentando comprar " + ingrediente + "...");
                    String[] lote = new String[1];
                    for(int i=0; i<1; i++) lote[i] = ingrediente;

                    conseguiuComprar = mercadoProxy.comprarProdutos(this.idRestaurante, lote);

                    if (conseguiuComprar) {
                        int tempo = mercadoProxy.tempoEntrega(this.idRestaurante);
                        System.out.println(">>> Compra Aprovada! Chega em " + tempo/1000 + "s");
                        Thread.sleep(tempo);
                        estoque.put(ingrediente, estoque.get(ingrediente) + 1);
                    } else {
                        System.err.println(">>> Negado. Tentando de novo em 2s...");
                        Thread.sleep(2000);
                    }

                } catch (Exception e) {
                    System.err.println("Erro de conexão (" + e.getMessage() + "). O cluster pode estar elegendo novo líder.");
                    System.out.println("Tentando de novo em 2s...");
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    @Override
    public int novoPreparo(int comanda, String[] pedido) throws RemoteException {
        System.out.println("\nNovo Pedido - Comanda: " + comanda);
        who.put(id,comanda);
        pedidos.put(id, new ArrayList<>());

        for(String item : pedido) {
            verificarEstoque(item);

            String nomePrato = item.contains(",") ? item.split(",")[1] : item;
            String ingrediente = ingredienteNecessario.get(nomePrato);

            if (ingrediente != null) {
                int qtd = estoque.get(ingrediente);
                if (qtd > 0) {
                    estoque.put(ingrediente, qtd-1 );
                    System.out.println("Cozinhando: " + nomePrato);
                }
            }
            pedidos.get(id).add(item);
        }

        fim.put(id, Agora() + (rng.nextInt(5)+2) *1000);
        return id++;
    }

    @Override
    public int tempoPreparo(int preparo) throws RemoteException {
        if (!fim.containsKey(preparo)) return 0;
        return max(0,Math.toIntExact((fim.get(preparo) - Agora())));
    }

    @Override
    public String[] pegarPreparo(int preparo) throws RemoteException {
        if(tempoPreparo(preparo) == 0 && pedidos.containsKey(preparo)){
            return pedidos.get(preparo).toArray(new String[0]);
        }
        return new String[0];
    }

    public static void main(String[] args){
        try{
            Kitchen obj = new Kitchen();
            Cozinha stub = (Cozinha) UnicastRemoteObject.exportObject(obj, 0);
            Registry registry = LocateRegistry.createRegistry(2002);
            registry.rebind("main.java.java.Cozinha", stub);
            System.out.println("Cozinha pronta na porta 2002!");
        } catch (Exception e){ e.printStackTrace(); }
    }
}