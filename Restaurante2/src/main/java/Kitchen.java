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
import wsMercado.MercadoMiddleware;

import static java.lang.Math.max;

public class Kitchen implements Cozinha{
    // Que comanda meu pedido pertence
    Map<Integer, Integer> who = new HashMap<>();
    // Quais pedidos há num preparo
    static Map<Integer, ArrayList<String>> pedidos = new HashMap<>();
    Map<Integer, Long> fim = new HashMap<>();
    static Map<String, String> ingredienteNecessario = new HashMap<>();
    static Map<String, Integer> estoque = new HashMap<>();
    private MercadoMiddleware mercadoMiddleware;
    private int idMercado;

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
            // Mercado middleware conversa com o dns
            this.mercadoMiddleware = new MercadoMiddleware();

            System.out.println("Middleware do Mercado inicializado (DNS configurado).");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private long Agora(){
        return System.currentTimeMillis();
    }

    private void verificarEstoque(String item) {
        String ingrediente = ingredienteNecessario.get(item.split(",")[1]);
        if (ingrediente != null && estoque.get(ingrediente) <= 0) {
            System.out.println("Verificando estoque e falta");
            try {
                System.out.println("Ingrediente em falta: " + ingrediente + ". Solicitando ao mercado...");
                String[] produtos = {ingrediente};
                if (mercadoMiddleware.comprarProdutos(ingrediente, 10)) {
                    // Aguarda a entrega
                    Thread.sleep(mercadoMiddleware.tempoEntrega());
                    // Atualiza o estoque
                    estoque.put(ingrediente, estoque.get(ingrediente) + 10);
                    System.out.println("Recebido do mercado: " + ingrediente);
                }
            } catch (Exception e) {
                System.err.println("Erro ao comprar do mercado: " + e);
            }
        }
    }

    @Override
    public int novoPreparo(int comanda, String[] pedido) throws RemoteException {
        who.put(id,comanda);
        pedidos.put(id, new ArrayList<>());
        
        // Verifica e atualiza estoque para cada item do pedido
        for(String item : pedido) {
            // Verifica se precisamos comprar ingredientes
            verificarEstoque(item);
            
            // Consome um ingrediente do estoque
            String ingrediente = ingredienteNecessario.get(item);
            if (ingrediente != null) {
                int quantidade = estoque.get(ingrediente);
                estoque.put(ingrediente, quantidade - 1);
                System.out.println("Usando " + ingrediente + " para preparar " + item + ". Restante: " + (quantidade - 1));
            }
            
            pedidos.get(id).add(item);
        }
        
        fim.put(id, Agora() + (rng.nextInt(10)+1) *1000);
        return id++;
    }

    @Override
    public int tempoPreparo(int preparo) throws RemoteException {
        System.out.println("Tempo de preparo consultado para preparo " + preparo);
        return max(0,Math.toIntExact((fim.get(preparo) - Agora())));
    }

    @Override
    public String[] pegarPreparo(int preparo) throws RemoteException {
        if(tempoPreparo(preparo) == 0){
            String[] arr = new String[pedidos.get(preparo).size()];
            int i = 0;
            for(String c : pedidos.get(preparo)){
                arr[i] = c;
                i++;
            }
            return arr;
        }

        return new String[0];
    }


    // criou um servidor
    public static void main(String[] args){
        try{
            Kitchen obj = new Kitchen();
            Cozinha stub = (Cozinha) UnicastRemoteObject.exportObject(obj, 0);


            Registry registry = LocateRegistry.createRegistry(2002);
            registry.rebind("main.java.java.Cozinha", stub);

            System.err.println("Servidor main.java.java.Cozinha inicializado!");
        } catch (Exception e){
            e.printStackTrace();
        }

    }


}


