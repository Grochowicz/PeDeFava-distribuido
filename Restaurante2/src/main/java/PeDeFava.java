import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class PeDeFava implements Restaurante {

    static int id = 0;
    private Cozinha cozinha;

    static Map<Integer, String> cliente = new HashMap<>();
    static Map<Integer, ArrayList<Integer>> ped = new HashMap<>();
    static ArrayList<Integer> cardapio = new ArrayList<>();
    static Map<Integer, Float> valor = new HashMap<>();
    static Map<Integer, String> produto = new HashMap<>();
    static Map<Integer, ArrayList<Integer>> preparo = new HashMap<>(); 

    public PeDeFava() {
        try {
            Registry registry = LocateRegistry.getRegistry(2002); // porta da cozinha
            cozinha = (Cozinha) registry.lookup("main.java.java.Cozinha");
        } catch (Exception e) {
            System.err.println("Erro ao conectar com a cozinha: " + e.toString());
            e.printStackTrace();
        }
        System.out.println("Conectado ao servidor da main.java.java.Kitchen!");

        // Construindo cardapio do Pe de Fava :)
        int it = 0;
        cardapio.add(it); produto.put(it, "Café"); valor.put(it, 5f); it++;
        cardapio.add(it); produto.put(it, "Pão de Queijo"); valor.put(it, 7f); it++;
        cardapio.add(it); produto.put(it, "Suco Natural"); valor.put(it, 8f); it++;
        cardapio.add(it); produto.put(it, "Sanduíche"); valor.put(it, 12f); it++;
        cardapio.add(it); produto.put(it, "Salada"); valor.put(it, 10f); it++;
        cardapio.add(it); produto.put(it, "Pizza (fatia)"); valor.put(it, 6.5f); it++;
        cardapio.add(it); produto.put(it, "Hambúrguer"); valor.put(it, 15f); it++;
        cardapio.add(it); produto.put(it, "Refrigerante"); valor.put(it, 6f); it++;
    }

    @Override
    public int novaComanda(String nome, int mesa) throws RemoteException {
        ped.put(id, new ArrayList<Integer>());
        cliente.put(id, nome);
        preparo.put(id, new ArrayList<Integer>()); 
        return id++;
    }

    @Override
    public String[] consultarCardapio() throws RemoteException {
        String[] arr =  new String[cardapio.size()];
        int i = 0;
        for(Integer u : cardapio){
            arr[i] = u + "," + produto.get(u) + "," + valor.get(u);
            i++;
        }
        return arr;
    }

    private Integer idFromCsv(String s) {
        String[] x = s.split(",");
        return Integer.parseInt(x[0]);
    }

        @Override
    public String fazerPedido(int comanda, String[] pedido) throws RemoteException {
        if(!ped.containsKey(comanda)) {
            return "Comanda inválida.";
        }
        
        for(String s:pedido) {
            ped.get(comanda).add(idFromCsv(s));
        }
        
        try {
            // Envia pedido para a cozinha
            int idPreparo = cozinha.novoPreparo(comanda, pedido);
            preparo.get(comanda).add(idPreparo); 
            
            int tempoEstimado = cozinha.tempoPreparo(idPreparo);
            return "Pedido aceito. Tempo estimado de preparo: " + tempoEstimado + " ms.";
        } catch (Exception e) {
            System.err.println("Erro ao comunicar com a cozinha: " + e.toString());
            return "Erro ao processar pedido na cozinha.";
        }
    }

    @Override
    public float valorComanda(int comanda) throws RemoteException {
        if(!ped.containsKey(comanda)) {
            return -1;
        }
        
        // Verifica o status dos pedidos
        try {
            verificarPedidosProntos(comanda);
        } catch (Exception e) {
            System.err.println("Erro ao verificar pedidos prontos: " + e.toString());
        }
        
        // Calcula o valor total
        float total = 0;
        for(Integer id : ped.get(comanda)){
            total += valor.get(id);
        }
        return total;
    }

        @Override
    public boolean fecharComanda(int comanda) throws RemoteException {
        if(!ped.containsKey(comanda)) {
            return false;
        }
        
        // Verifica se todos os pedidos estão prontos
        try {
            if (!verificarPedidosProntos(comanda)) {
                System.out.println("Não é possível fechar a comanda " + comanda + " - ainda há pedidos em preparo");
                return false;
            }
        } catch (Exception e) {
            System.err.println("Erro ao verificar pedidos prontos: " + e.toString());
            return false;
        }
        
        // Se todos os pedidos estiverem prontos, fecha a comanda
        ped.remove(comanda);
        cliente.remove(comanda);
        return true;
    }

    private boolean verificarPedidosProntos(int comanda) throws RemoteException {
        ArrayList<Integer> preparos = preparo.get(comanda);
        if (preparos == null || preparos.isEmpty()) {
            // Se não há preparos pendentes, significa que todos já foram entregues
            return true;
        }
        
        boolean todosEntregues = true;
        ArrayList<Integer> preparosFinalizados = new ArrayList<>();
        
        // Verifica cada preparo da comanda
        for (Integer idPreparo : preparos) {
            int tempoRestante = cozinha.tempoPreparo(idPreparo);
            
            if (tempoRestante == 0) {
                try {
                    // Pega o pedido pronto da cozinha
                    String[] pedidoPronto = cozinha.pegarPreparo(idPreparo);
                    
                    System.out.println("Parte do pedido da comanda " + comanda + " está pronto!");
                    for (String item : pedidoPronto) {
                        System.out.println("Item pronto: " + item);
                    }
                    
                    preparosFinalizados.add(idPreparo);
                    
                } catch (Exception e) {
                    System.err.println("Erro ao pegar pedido pronto da cozinha: " + e.toString());
                    e.printStackTrace();
                    todosEntregues = false;
                }
            } else {
                todosEntregues = false;
                System.out.println("Parte do pedido da comanda " + comanda + " ainda em preparo. Tempo restante: " + tempoRestante + " ms");
            }
        }
        
        preparos.removeAll(preparosFinalizados);
        
        return todosEntregues;
    }

    public static void main(String args[]){
        // Criando o servidor
        try{
            PeDeFava obj = new PeDeFava();
            Restaurante cozinha = (Restaurante) UnicastRemoteObject.exportObject(obj,0);

            Registry registry = LocateRegistry.createRegistry(2001);
            registry.bind("main.java.java.Restaurante", cozinha);

            System.out.println("Servidor Inicializado");

        } catch(Exception e){
            e.printStackTrace();
        }
    }
}
